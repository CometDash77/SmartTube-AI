package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import com.google.android.exoplayer2.Format;

import java.io.IOException;
import java.io.InputStream;

/**
 * Performs one subtitle snapshot fetch and decode (plan 4.1).
 *
 * <p>It is deliberately synchronous and callback-free: the caller decides on which worker it runs and
 * only takes the result back to the main thread. That keeps the plan's rule "one extra read per source
 * snapshot" and the cancellation check explicit, without any hidden callback contract.
 */
public class SubtitleSnapshotFetcher {
    /** Opens the payload of the selected source (an ExoPlayer data source in production). */
    public interface PayloadFactory {
        InputStream open(SelectedSubtitleSource source);
    }

    public static final class Result {
        private final SubtitleSnapshotReader.Status mStatus;
        private final SubtitleTimeline mTimeline;

        Result(SubtitleSnapshotReader.Status status, SubtitleTimeline timeline) {
            mStatus = status;
            mTimeline = timeline;
        }

        public SubtitleSnapshotReader.Status getStatus() {
            return mStatus;
        }

        /** Null unless the fetch succeeded. */
        public SubtitleTimeline getTimeline() {
            return mTimeline;
        }

        public boolean isUsable() {
            return mTimeline != null && mTimeline.size() > 0;
        }

        @Override
        public String toString() {
            return "Result{" + mStatus + ", frames=" + (mTimeline != null ? mTimeline.size() : 0) + "}";
        }
    }

    /** API 17 safe: java.util.function.BooleanSupplier would require API 24. */
    private static boolean isCancelled(SubtitleSnapshotReader.Cancellation cancellation) {
        return cancellation != null && cancellation.isCancelled();
    }

    private final SubtitleSnapshotReader mReader;
    private final PayloadFactory mPayloadFactory;

    public SubtitleSnapshotFetcher(SubtitleSnapshotReader reader, PayloadFactory payloadFactory) {
        mReader = reader;
        mPayloadFactory = payloadFactory;
    }

    /**
     * @param cancelled checked before the read and repeatedly by the reader; a cancelled fetch
     *                  returns {@link SubtitleSnapshotReader.Status#CANCELLED}
     */
    public Result fetch(SelectedSubtitleSource source, Format format, long mediaEndUs, SubtitleSnapshotReader.Cancellation cancelled) {
        if (source == null || mReader == null) {
            return new Result(SubtitleSnapshotReader.Status.NO_SOURCE, null);
        }

        if (isCancelled(cancelled)) {
            return new Result(SubtitleSnapshotReader.Status.CANCELLED, null);
        }

        InputStream payload = null;

        try {
            payload = mPayloadFactory != null ? mPayloadFactory.open(source) : null;

            if (isCancelled(cancelled)) {
                return new Result(SubtitleSnapshotReader.Status.CANCELLED, null);
            }

            SubtitleSnapshotReader.Snapshot snapshot =
                    mReader.read(format, payload, mediaEndUs, () -> isCancelled(cancelled));

            return new Result(snapshot.getStatus(), snapshot.getTimeline());
        } catch (RuntimeException e) {
            return new Result(SubtitleSnapshotReader.Status.IO_FAILED, null);
        } finally {
            if (payload != null) {
                try {
                    payload.close();
                } catch (IOException ignored) {
                    // The result already exists; a failing close changes nothing.
                }
            }
        }
    }
}
