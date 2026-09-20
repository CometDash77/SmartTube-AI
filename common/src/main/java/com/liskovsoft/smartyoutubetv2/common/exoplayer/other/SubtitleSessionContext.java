package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Session-scoped context material of one subtitle source: the verified translation examples already
 * produced and the frozen video summary (plan 4.2).
 *
 * <p>Rules:
 * <ul>
 *     <li>Only a truly verified success becomes an example: a blank or missing translation never
 *     does, and the same item id is stored once.</li>
 *     <li>At most the last two successful batches and the newest 8 usable pairs within 1,000 code
 *     points are offered back, so the context cannot grow with playback time.</li>
 *     <li>A seek clears the examples (the text before the seek must not leak into the new position)
 *     while the frozen summary survives; a new source, video or configuration drops both.</li>
 *     <li>The summary is frozen once a session decided on it: a late answer must not be mixed in.</li>
 * </ul>
 */
public class SubtitleSessionContext implements SubtitleContextSource {
    /** The plan's bound: only the two most recent successful batches may provide examples. */
    public static final int MAX_BATCHES = 2;

    private final Map<String, Entry> mEntries = new LinkedHashMap<>();
    private final List<List<String>> mBatches = new ArrayList<>();
    private SubtitleSummary mSummary;

    private static final class Entry {
        private final long mStartUs;
        private final String mSource;
        private final String mTarget;

        private Entry(long startUs, String source, String target) {
            mStartUs = startUs;
            mSource = source;
            mTarget = target;
        }
    }

    /**
     * Records the verified successes of one finished batch. Only non-blank one-to-one results are
     * stored; the previous state of the same item id is replaced, and only the last two batches stay.
     */
    public synchronized void recordBatch(SubtitleBatch batch, List<String> translations) {
        if (batch == null || translations == null) {
            return;
        }

        List<SubtitleItem> items = batch.getItems();
        List<String> recorded = new ArrayList<>();
        int usable = 0;

        for (int i = 0; i < items.size() && i < translations.size(); i++) {
            String translation = translations.get(i);
            String id = items.get(i).getItemId();
            long startUs = batch.getItemStartUs(i);

            if (translation == null || translation.trim().isEmpty() || startUs == SubtitleBatch.TIME_UNKNOWN) {
                continue; // a blank result is not a verified example, and without a position it is unusable
            }

            mEntries.put(id, new Entry(startUs, items.get(i).getText(), translation.trim()));
            recorded.add(id);
            usable++;
        }

        if (usable == 0) {
            return;
        }

        mBatches.add(recorded);
        trimBatches();
    }

    /** A seek: the text before the new position must not become context of the new position. */
    public synchronized void resetExamples() {
        mEntries.clear();
        mBatches.clear();
    }

    /** A new source, video or configuration: examples and the summary both belong to the old one. */
    public synchronized void reset() {
        resetExamples();
        mSummary = null;
    }

    /** Freezes the one allowed summary of this session; a second call is ignored. */
    public synchronized boolean freezeSummary(SubtitleSummary summary) {
        if (mSummary != null || summary == null || summary.isEmpty()) {
            return false;
        }

        mSummary = summary;

        return true;
    }

    public synchronized boolean hasSummary() {
        return mSummary != null;
    }

    @Override
    public synchronized SubtitleSummary getSummary() {
        return mSummary;
    }

    /**
     * The newest usable examples that really played before {@code positionUs}, in time order.
     *
     * @param maxCodePoints upper bound of the returned pairs' code points (plan: 1,000)
     */
    @Override
    public synchronized List<SubtitleTextPair> examplesBefore(long positionUs, int maxCodePoints) {
        List<SubtitleTextPair> result = new ArrayList<>();
        List<Entry> ordered = new ArrayList<>();

        for (Entry entry : mEntries.values()) {
            if (entry.mStartUs < positionUs) {
                ordered.add(entry);
            }
        }

        int codePoints = 0;

        for (int i = ordered.size() - 1; i >= 0; i--) {
            Entry entry = ordered.get(i);
            SubtitleTextPair pair = new SubtitleTextPair(entry.mSource, entry.mTarget);

            if (result.size() >= SubtitleContext.MAX_EXAMPLES) {
                break;
            }

            int pairCodePoints = pair.codePoints();

            if (codePoints + pairCodePoints > maxCodePoints) {
                continue;
            }

            result.add(0, pair);
            codePoints += pairCodePoints;
        }

        return result;
    }

    /** Keeps at most the plan's two most recent successful batches. */
    private void trimBatches() {
        while (mBatches.size() > MAX_BATCHES) {
            List<String> oldest = mBatches.remove(0);

            for (String id : oldest) {
                mEntries.remove(id);
            }
        }
    }
}
