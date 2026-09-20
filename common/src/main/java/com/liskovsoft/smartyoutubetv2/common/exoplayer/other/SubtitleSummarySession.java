package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

/**
 * Bookkeeping of the one automatic context analysis of a session (plan 4.2).
 *
 * <p>The attempt key combines the selected subtitle source with the analysis configuration, so a new
 * video, track or analysis setting starts a fresh attempt while everything else keeps the result of
 * the first one. At most one attempt is started per key and a pending attempt may not be restarted,
 * which is what keeps a failing summary from being retried on every playback event.
 */
public class SubtitleSummarySession {
    private String mKey;
    private boolean mAttempted;
    private boolean mPending;

    /**
     * @return true when the one attempt for this key may start now
     */
    public boolean beginAttempt(String key) {
        if (key == null) {
            return false;
        }

        if (!key.equals(mKey)) {
            mKey = key;
            mAttempted = false;
            mPending = false;
        }

        if (mAttempted || mPending) {
            return false;
        }

        mPending = true;

        return true;
    }

    /** The attempt reached its terminal outcome; it is never repeated for the same key. */
    public void finishAttempt() {
        mPending = false;
        mAttempted = true;
    }

    public boolean isPending() {
        return mPending;
    }

    /** The attempt could not start (the shared AI slot was busy): the key stays un-attempted. */
    public void releaseAttempt() {
        mPending = false;
    }

    /** The user did not get an attempt started (no slot, no timeline yet): leave the key untouched. */
    public boolean hasAttempted() {
        return mAttempted;
    }

    public void reset() {
        mKey = null;
        mAttempted = false;
        mPending = false;
    }
}
