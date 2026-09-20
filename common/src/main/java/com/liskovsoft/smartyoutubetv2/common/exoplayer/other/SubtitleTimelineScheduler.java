package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps at most one subtitle-timeline fetch in flight per source identity (plan 17.3 / N1).
 *
 * <p>Every attempt carries an immutable identity (media generation + source key + content locator)
 * and its own cancellation flag. That is what makes a late answer harmless: a superseded attempt can
 * never be mistaken for the live one, and a new attempt can never resurrect the cancellation of an
 * older one by resetting shared state.
 *
 * <p>The class is deliberately free of Android and player dependencies: the caller owns the worker
 * and the main thread, so the whole ordering is deterministic in tests.
 */
public class SubtitleTimelineScheduler {
    /** Immutable identity of one fetch attempt, plus its own cooperative cancellation. */
    public static final class Request {
        private final long mId;
        private final int mMediaGeneration;
        private final String mSourceKey;
        private final String mLocator;
        private final AtomicBoolean mCancelled = new AtomicBoolean();

        Request(long id, int mediaGeneration, String sourceKey, String locator) {
            mId = id;
            mMediaGeneration = mediaGeneration;
            mSourceKey = sourceKey;
            mLocator = locator;
        }

        public long getId() {
            return mId;
        }

        public int getMediaGeneration() {
            return mMediaGeneration;
        }

        /** Identity of the subtitle source this attempt reads; memory only, never logged. */
        public String getSourceKey() {
            return mSourceKey;
        }

        /** Content locator of the payload (vssId + language + url); memory only, never logged. */
        public String getLocator() {
            return mLocator;
        }

        public boolean isCancelled() {
            return mCancelled.get();
        }

        /** The cooperative cancellation the snapshot reader polls. */
        public SubtitleSnapshotReader.Cancellation asCancellation() {
            return mCancelled::get;
        }

        /** True when this attempt reads the same source of the same media generation. */
        public boolean matches(int mediaGeneration, String sourceKey) {
            return mMediaGeneration == mediaGeneration
                    && (mSourceKey == null ? sourceKey == null : mSourceKey.equals(sourceKey));
        }

        @Override
        public String toString() {
            // The source key and the locator stay out of every printable form.
            return "Request{id=" + mId + ", mediaGeneration=" + mMediaGeneration + "}";
        }
    }

    private long mNextId;
    private Request mInFlight;

    /**
     * Registers the attempt to start, or returns null when nothing has to be fetched.
     *
     * @return the attempt to run, or null when an attempt for the same identity is already running
     */
    public Request begin(int mediaGeneration, String sourceKey, String locator) {
        if (sourceKey == null || locator == null) {
            return null; // no selected source: there is nothing this attempt could read
        }

        if (mInFlight != null && mInFlight.matches(mediaGeneration, sourceKey)) {
            return null; // the same source is already being read: reuse that attempt, do not restart it
        }

        cancelInFlight(); // a genuinely different identity: the previous attempt is abandoned

        mInFlight = new Request(mNextId++, mediaGeneration, sourceKey, locator);

        return mInFlight;
    }

    /** Abandons the current attempt. Returns true when there was one. */
    public boolean cancel() {
        if (mInFlight == null) {
            return false;
        }

        cancelInFlight();

        return true;
    }

    public Request getInFlight() {
        return mInFlight;
    }

    /** True while this attempt is still the one the scheduler is waiting for. */
    public boolean isCurrent(Request request) {
        return request != null && request == mInFlight;
    }

    /**
     * Releases the attempt's slot after its work settled.
     *
     * @return true only when the attempt was still the current one, so a superseded attempt can
     * neither install its timeline, overwrite the current diagnostic status, nor clear the slot of a
     * newer request
     */
    public boolean settle(Request request) {
        if (request == null || request != mInFlight) {
            return false;
        }

        mInFlight = null;

        return true;
    }

    private void cancelInFlight() {
        Request pending = mInFlight;
        mInFlight = null;

        if (pending != null) {
            pending.mCancelled.set(true);
        }
    }
}
