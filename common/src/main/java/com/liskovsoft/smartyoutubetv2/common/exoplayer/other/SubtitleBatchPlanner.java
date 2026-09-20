package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Plans the next translation batch inside the current prefetch window.
 *
 * <p>Rules (plan 6.2/6.3):
 * <ul>
 *     <li>Window: {@code end > position && start < position + 60 s}; empty gaps are window entries
 *     too, but they carry no items.</li>
 *     <li>Priority: the frames in time order, so the current item and its nearest neighbours are
 *     first; the first batch is at most 4 items and later batches at most 20.</li>
 *     <li>Budget: 6,000 code points over items plus context; context is dropped before items are,
 *     and a single over-budget item is marked oversize instead of blocking the queue.</li>
 *     <li>An item that is queued, in flight or already done is never submitted twice.</li>
 * </ul>
 */
public class SubtitleBatchPlanner {
    public static final long WINDOW_AHEAD_US = 60_000_000L;
    public static final int MAX_ITEMS = 20;
    public static final int FIRST_BATCH_ITEMS = 4;
    public static final int MAX_CODE_POINTS = 6_000;
    public static final int MAX_ITEM_CODE_POINTS = 6_000;
    public static final int CONTEXT_BEFORE = 3;
    public static final int CONTEXT_AFTER = 2;
    public static final int MAX_CONTEXT_CODE_POINTS = 500;

    private final Set<String> mPending = new LinkedHashSet<>();
    private final Set<String> mDone = new HashSet<>();
    private final Set<String> mOversize = new HashSet<>();

    /**
     * @param firstBatch true while the session has not started a batch yet
     * @return the next batch, or null when the window holds nothing new
     */
    public SubtitleBatch nextBatch(SubtitleTimeline timeline, long positionUs, boolean firstBatch) {
        if (timeline == null) {
            return null;
        }

        List<SubtitleFrame> window = timeline.framesInWindow(positionUs, WINDOW_AHEAD_US);
        List<SubtitleItem> items = new ArrayList<>();
        int limit = firstBatch ? FIRST_BATCH_ITEMS : MAX_ITEMS;
        int codePoints = 0;

        for (SubtitleFrame frame : window) {
            for (SubtitleItem item : frame.getItems()) {
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
                codePoints += itemCodePoints;
            }

            if (items.size() >= limit) {
                break;
            }
        }

        if (items.isEmpty()) {
            return null;
        }

        List<String> before = context(window, items.get(0).getItemId(), true, codePoints);
        int usedCodePoints = codePoints + codePoints(before);
        List<String> after = context(window, items.get(items.size() - 1).getItemId(), false, usedCodePoints);

        return new SubtitleBatch(items, before, after);
    }

    /**
     * Context is optional: it is dropped before any item, so it is only added while both the
     * per-direction cap and the whole 6,000 code point budget still hold.
     */
    private List<String> context(List<SubtitleFrame> window, String itemId, boolean before, int usedCodePoints) {
        List<String> result = new ArrayList<>();
        int codePoints = 0;
        int budget = Math.min(MAX_CONTEXT_CODE_POINTS, MAX_CODE_POINTS - usedCodePoints);

        if (budget <= 0) {
            return result;
        }

        for (String candidate : collectContext(window, itemId, before)) {
            int candidateCodePoints = SubtitleBatch.codePoints(candidate);

            if (codePoints + candidateCodePoints > budget) {
                continue;
            }

            result.add(candidate);
            codePoints += candidateCodePoints;
        }

        return result;
    }

    private static int codePoints(List<String> texts) {
        int codePoints = 0;

        for (String text : texts) {
            codePoints += SubtitleBatch.codePoints(text);
        }

        return codePoints;
    }

    private List<String> collectContext(List<SubtitleFrame> window, String itemId, boolean before) {
        List<String> texts = new ArrayList<>();
        int start = 0;

        for (int i = 0; i < window.size(); i++) {
            for (SubtitleItem item : window.get(i).getItems()) {
                texts.add(item.getText());

                if (item.getItemId().equals(itemId)) {
                    start = texts.size() - 1;
                }
            }
        }

        List<String> result = new ArrayList<>();

        if (before) {
            for (int i = start - 1; i >= 0 && result.size() < CONTEXT_BEFORE; i--) {
                result.add(texts.get(i));
            }
        } else {
            for (int i = start + 1; i < texts.size() && result.size() < CONTEXT_AFTER; i++) {
                result.add(texts.get(i));
            }
        }

        return result;
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
