package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import com.google.android.exoplayer2.C;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Plans the next translation batch inside the current prefetch window.
 *
 * <p>Rules (plan 4.2/6.2/6.3):
 * <ul>
 *     <li>Window: {@code end > position && start < position + 60 s}; the first batch is at most 4
 *     items and later batches at most 20.</li>
 *     <li>One stable item id is planned once, however many consecutive frames show it; the item keeps
 *     the frame start where it first appeared.</li>
 *     <li>Budget: 6,000 code points over items plus all context; context is dropped before items are,
 *     and a single over-budget item is marked oversize instead of blocking the queue.</li>
 *     <li>Neighbours come from the whole timeline, not only from the window: at most three preceding
 *     and two following items in time order, 500 code points per direction, the current batch
 *     excluded.</li>
 *     <li>Reference material of the session (verified examples and the frozen summary) is added last
 *     and dropped first when the budget is tight (plan 4.2).</li>
 * </ul>
 */
public class SubtitleBatchPlanner {
    public static final long WINDOW_AHEAD_US = 60_000_000L;
    public static final int MAX_ITEMS = 20;
    public static final int FIRST_BATCH_ITEMS = 4;
    public static final int MAX_CODE_POINTS = 6_000;
    public static final int MAX_ITEM_CODE_POINTS = 6_000;
    public static final int CONTEXT_BEFORE = SubtitleContext.MAX_BEFORE;
    public static final int CONTEXT_AFTER = SubtitleContext.MAX_AFTER;
    public static final int MAX_CONTEXT_CODE_POINTS = SubtitleContext.MAX_NEIGHBOUR_CODE_POINTS;

    private final Set<String> mPending = new LinkedHashSet<>();
    private final Set<String> mDone = new HashSet<>();
    private final Set<String> mOversize = new HashSet<>();
    private SubtitleContextSource mContextSource;
    private int mContextTier = SubtitleAiSettings.CONTEXT_BASIC;
    private SubtitleTimeline mIndexedTimeline;
    private List<Indexed> mFlatIndex;

    /** One distinct item of the timeline with the start and end of the frames that carry it. */
    private static final class Indexed {
        private final SubtitleItem mItem;
        private final long mStartUs;
        private long mEndUs;

        private Indexed(SubtitleItem item, long startUs, long endUs) {
            mItem = item;
            mStartUs = startUs;
            mEndUs = endUs;
        }

        private boolean isVisibleAt(long positionUs) {
            long endUs = mEndUs == C.TIME_UNSET ? Long.MAX_VALUE : mEndUs;

            return endUs > positionUs;
        }
    }

    /** Session context material (verified examples and the frozen summary); may be null. */
    public void setContextSource(SubtitleContextSource contextSource) {
        mContextSource = contextSource;
    }

    /**
     * The context tier of the configuration that will answer the next batch. The basic tier keeps the
     * payload unchanged, so its budget is not spent on material the request may not carry (plan 4.2).
     */
    public void setContextTier(int contextTier) {
        mContextTier = contextTier;
    }

    /**
     * @param firstBatch true while the session has not started a batch yet
     * @return the next batch, or null when the window holds nothing new
     */
    public SubtitleBatch nextBatch(SubtitleTimeline timeline, long positionUs, boolean firstBatch) {
        if (timeline == null) {
            return null;
        }

        List<Indexed> flat = flatIndex(timeline);
        List<SubtitleItem> items = new ArrayList<>();
        List<Long> starts = new ArrayList<>();
        int limit = firstBatch ? FIRST_BATCH_ITEMS : MAX_ITEMS;
        long windowEndUs = positionUs + WINDOW_AHEAD_US;
        int codePoints = 0;

        for (Indexed indexed : flat) {
            if (indexed.mStartUs >= windowEndUs) {
                break; // the flat index is in time order
            }

            if (!indexed.isVisibleAt(positionUs)) {
                continue;
            }

            SubtitleItem item = indexed.mItem;
            String id = item.getItemId();
            int itemCodePoints = SubtitleBatch.codePoints(item.getText());

            if (itemCodePoints > MAX_ITEM_CODE_POINTS) {
                mOversize.add(id); // the item itself is unusable: never queue it again
                continue;
            }

            if (mPending.contains(id) || mDone.contains(id) || mOversize.contains(id)) {
                continue;
            }

            if (items.size() >= limit || codePoints + itemCodePoints > MAX_CODE_POINTS) {
                break;
            }

            items.add(item);
            starts.add(indexed.mStartUs);
            codePoints += itemCodePoints;
        }

        if (items.isEmpty()) {
            return null;
        }

        SubtitleContext context = buildContext(flat, items, positionUs, MAX_CODE_POINTS - codePoints);

        return SubtitleBatch.withContext(items, starts, context);
    }

    /**
     * Neighbours come from the whole timeline (plan 4.2); the session's verified examples and the
     * frozen summary are added after them, inside whatever budget the items left.
     */
    private SubtitleContext buildContext(List<Indexed> flat, List<SubtitleItem> items, long positionUs,
                                         int remaining) {
        if (remaining <= 0) {
            return SubtitleContext.EMPTY;
        }

        Set<String> batchIds = new HashSet<>();

        for (SubtitleItem item : items) {
            batchIds.add(item.getItemId());
        }

        int beforeBudget = Math.min(MAX_CONTEXT_CODE_POINTS, remaining);
        List<String> before = neighbours(flat, batchIds, items.get(0).getItemId(), true, beforeBudget);
        int used = codePoints(before);
        int afterBudget = Math.min(MAX_CONTEXT_CODE_POINTS, Math.max(0, remaining - used));
        List<String> after = neighbours(flat, batchIds, items.get(items.size() - 1).getItemId(), false, afterBudget);
        used += codePoints(after);

        // Neighbours first, then the frozen summary, then the examples: plan 4.2 reduces the examples
        // first and the far neighbours/summary last, so the examples are the first to be squeezed out.
        SubtitleSummary summary = null;

        if (mContextSource != null && mContextTier >= SubtitleAiSettings.CONTEXT_VIDEO_ENHANCED) {
            SubtitleSummary candidate = mContextSource.getSummary();

            if (candidate != null && !candidate.isEmpty() && used + candidate.codePoints() <= remaining) {
                summary = candidate;
                used += candidate.codePoints();
            }
        }

        List<SubtitleTextPair> examples = Collections.emptyList();

        if (mContextSource != null && mContextTier >= SubtitleAiSettings.CONTEXT_COHERENT) {
            examples = mContextSource.examplesBefore(positionUs,
                    Math.min(SubtitleContext.MAX_EXAMPLE_CODE_POINTS, Math.max(0, remaining - used)));
        }

        return new SubtitleContext(before, after, examples, summary);
    }

    /**
     * The distinct items of the timeline in time order, each with the span of its frames. The index is
     * built once per timeline, so a per-tick plan never scans the whole file again.
     */
    private List<Indexed> flatIndex(SubtitleTimeline timeline) {
        if (timeline == mIndexedTimeline && mFlatIndex != null) {
            return mFlatIndex;
        }

        List<Indexed> flat = new ArrayList<>();
        java.util.Map<String, Integer> positions = new java.util.HashMap<>();

        for (SubtitleFrame frame : timeline.getFrames()) {
            for (SubtitleItem item : frame.getItems()) {
                if (item.getText() == null || item.getText().trim().isEmpty()) {
                    continue; // a clearing frame is a boundary, never a translatable item
                }

                Integer at = positions.get(item.getItemId());

                if (at == null) {
                    positions.put(item.getItemId(), flat.size());
                    flat.add(new Indexed(item, frame.getStartUs(), frame.getEndUs()));
                } else {
                    // The same stable item persisted across consecutive frames: extend its span and
                    // keep one entry, so a batch never requests the same id twice (plan 4.2).
                    flat.get(at).mEndUs = frame.getEndUs();
                }
            }
        }

        mIndexedTimeline = timeline;
        mFlatIndex = flat;

        return flat;
    }

    /**
     * Context is optional: it is dropped before any item, so it is only added while the per-direction
     * cap and the whole 6,000 code point budget still hold. The result is always in time order.
     */
    private static List<String> neighbours(List<Indexed> flat, Set<String> batchIds, String anchorId,
                                           boolean before, int budget) {
        List<String> result = new ArrayList<>();

        if (budget <= 0) {
            return result;
        }

        int anchor = -1;

        for (int i = 0; i < flat.size(); i++) {
            if (flat.get(i).mItem.getItemId().equals(anchorId)) {
                anchor = i;
                break;
            }
        }

        if (anchor < 0) {
            return result;
        }

        int limit = before ? CONTEXT_BEFORE : CONTEXT_AFTER;
        int codePoints = 0;

        if (before) {
            for (int i = anchor - 1; i >= 0 && result.size() < limit; i--) {
                if (add(result, flat.get(i).mItem, batchIds, budget - codePoints)) {
                    codePoints = codePoints(result);
                }
            }

            Collections.reverse(result); // the prompt carries the preceding lines in time order
        } else {
            for (int i = anchor + 1; i < flat.size() && result.size() < limit; i++) {
                if (add(result, flat.get(i).mItem, batchIds, budget - codePoints)) {
                    codePoints = codePoints(result);
                }
            }
        }

        return result;
    }

    private static boolean add(List<String> result, SubtitleItem item, Set<String> batchIds, int budget) {
        String text = item.getText();

        if (text == null || text.trim().isEmpty() || batchIds.contains(item.getItemId())) {
            return false;
        }

        int textCodePoints = SubtitleBatch.codePoints(text);

        if (textCodePoints > budget) {
            return false;
        }

        for (String existing : result) {
            if (existing.equals(text)) {
                return false; // the same context line twice would waste the budget
            }
        }

        result.add(text);

        return true;
    }

    private static int codePoints(List<String> texts) {
        int codePoints = 0;

        for (String text : texts) {
            codePoints += SubtitleBatch.codePoints(text);
        }

        return codePoints;
    }

    /** Marks the batch as queued or in flight so it is never planned twice. */
    public void markQueued(SubtitleBatch batch) {
        if (batch != null) {
            mPending.addAll(batch.getItemIds());
        }
    }

    /** The batch finished (success or final failure): its ids may be retried only explicitly. */
    public void markFinished(SubtitleBatch batch, boolean success) {
        if (batch == null) {
            return;
        }

        for (String id : batch.getItemIds()) {
            mPending.remove(id);

            if (success) {
                mDone.add(id);
            }
        }
    }

    public void markDone(String itemId) {
        mPending.remove(itemId);
        mDone.add(itemId);
    }

    public boolean isPending(String itemId) {
        return mPending.contains(itemId);
    }

    public boolean isOversize(String itemId) {
        return mOversize.contains(itemId);
    }

    public int getPendingCount() {
        return mPending.size();
    }

    /** Drops queued and finished bookkeeping; used when the session or the source changes. */
    public void reset() {
        mPending.clear();
        mDone.clear();
        mOversize.clear();
    }
}
