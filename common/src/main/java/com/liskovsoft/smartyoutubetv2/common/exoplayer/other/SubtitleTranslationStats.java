package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

/**
 * Session counters of the AI subtitle work (plan section 11: record the requests actually made).
 *
 * <p>Only numbers, never subtitle text or credentials: the plan asks for observable counts such as
 * requests, delivered items, failures and cancellations without leaking content. The counters are
 * session scoped and are meant to be reset with the session.
 */
public final class SubtitleTranslationStats {
    private int mRequests;
    private int mDeliveredItems;
    private int mFailedBatches;
    private int mCancelledBatches;

    public void onRequestStarted() {
        mRequests++;
    }

    public void onBatchDelivered(int items) {
        mDeliveredItems += Math.max(0, items);
    }

    public void onBatchFailed() {
        mFailedBatches++;
    }

    /** A cancelled batch is neither a success nor a failure: it is a position/source change. */
    public void onBatchCancelled() {
        mCancelledBatches++;
    }

    public int getRequests() {
        return mRequests;
    }

    public int getDeliveredItems() {
        return mDeliveredItems;
    }

    public int getFailedBatches() {
        return mFailedBatches;
    }

    public int getCancelledBatches() {
        return mCancelledBatches;
    }

    /** True when nothing was requested yet, so the menu can stay silent instead of showing zeros. */
    public boolean isEmpty() {
        return mRequests == 0;
    }

    public void reset() {
        mRequests = 0;
        mDeliveredItems = 0;
        mFailedBatches = 0;
        mCancelledBatches = 0;
    }

    @Override
    public String toString() {
        return "SubtitleTranslationStats{requests=" + mRequests + ", items=" + mDeliveredItems
                + ", failed=" + mFailedBatches + ", cancelled=" + mCancelledBatches + "}";
    }
}
