package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.text.SubtitleDecoder;
import com.google.android.exoplayer2.text.SubtitleDecoderException;
import com.google.android.exoplayer2.text.SubtitleDecoderFactory;
import com.google.android.exoplayer2.text.SubtitleInputBuffer;
import com.google.android.exoplayer2.text.SubtitleOutputBuffer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads one subtitle file in the background and decodes it with the same native decoder the player
 * uses, producing a bounded {@link SubtitleTimeline}.
 *
 * <p>This is the plan's deliberate single extra read of the current source: it never touches the
 * player's decoder, renderer or buffers and the original playback does not depend on it. The caller
 * must fetch at most once per source snapshot, cancel on seek/track/source change and release the
 * worker.
 *
 * <p>Limits are engineering initial values, not measured constants: 2 MiB of raw payload, 20,000
 * events and 30 seconds of wall time. On any limit the result is a controlled refusal, never a
 * truncated timeline presented as success.
 */
public class SubtitleSnapshotReader {
    public static final long MAX_RAW_BYTES = 2L * 1024 * 1024;
    public static final int MAX_EVENTS = 20_000;
    public static final long MAX_DURATION_MS = 30_000;
    private static final long DECODE_GRACE_MS = 5_000;
    private static final long DECODE_POLL_INTERVAL_MS = 2;

    public enum Status {
        OK,
        /** No subtitle source was bound, so there was nothing to fetch. */
        NO_SOURCE,
        UNSUPPORTED_FORMAT,
        OVERSIZE,
        TOO_MANY_EVENTS,
        TIMEOUT,
        CANCELLED,
        DECODE_FAILED,
        IO_FAILED
    }

    /** Cooperative cancellation checked between reads and decode steps. */
    public interface Cancellation {
        boolean isCancelled();
    }

    public static final class Snapshot {
        private final Status mStatus;
        private final SubtitleTimeline mTimeline;
        private final long mBytes;
        private final int mEventCount;

        Snapshot(Status status, SubtitleTimeline timeline, long bytes, int eventCount) {
            mStatus = status;
            mTimeline = timeline;
            mBytes = bytes;
            mEventCount = eventCount;
        }

        public Status getStatus() {
            return mStatus;
        }

        /** Null unless the snapshot completed and the decoder produced at least one event. */
        public SubtitleTimeline getTimeline() {
            return mTimeline;
        }

        public long getBytes() {
            return mBytes;
        }

        public int getEventCount() {
            return mEventCount;
        }

        public boolean isUsable() {
            return mStatus == Status.OK && mTimeline != null && mTimeline.size() > 0;
        }

        @Override
        public String toString() {
            return "Snapshot{status=" + mStatus + ", events=" + mEventCount + ", frames="
                    + (mTimeline != null ? mTimeline.size() : 0) + "}";
        }
    }

    /**
     * @param format     format of the selected subtitle representation
     * @param payload    the subtitle file; closed by the caller
     * @param mediaEndUs known end of the media in microseconds, or {@link C#TIME_UNSET}
     */
    public Snapshot read(Format format, InputStream payload, long mediaEndUs, Cancellation cancellation) {
        if (format == null || !SubtitleDecoderFactory.DEFAULT.supportsFormat(format)) {
            return new Snapshot(Status.UNSUPPORTED_FORMAT, null, 0, 0);
        }

        long startedMs = System.nanoTime() / 1_000_000;
        long deadlineMs = startedMs + MAX_DURATION_MS;
        byte[] data = null;
        SubtitleDecoder decoder = null;

        try {
            data = readAll(payload, deadlineMs, cancellation);
            decoder = SubtitleDecoderFactory.DEFAULT.createDecoder(format);

            List<SubtitleEvent> events = decode(decoder, format, data, deadlineMs, cancellation);

            if (events.size() >= MAX_EVENTS) {
                return new Snapshot(Status.TOO_MANY_EVENTS, null, data.length, events.size());
            }

            return new Snapshot(Status.OK, new SubtitleTimelineBuilder().build(events, mediaEndUs), data.length, events.size());
        } catch (OversizeException e) {
            return new Snapshot(Status.OVERSIZE, null, MAX_RAW_BYTES, 0);
        } catch (TimeoutException e) {
            return new Snapshot(Status.TIMEOUT, null, data != null ? data.length : 0, 0);
        } catch (CancelledException e) {
            return new Snapshot(Status.CANCELLED, null, data != null ? data.length : 0, 0);
        } catch (SubtitleDecoderException e) {
            return new Snapshot(Status.DECODE_FAILED, null, data != null ? data.length : 0, 0);
        } catch (IOException e) {
            return new Snapshot(Status.IO_FAILED, null, data != null ? data.length : 0, 0);
        } finally {
            if (decoder != null) {
                decoder.release();
            }
        }
    }

    private byte[] readAll(InputStream payload, long deadlineMs, Cancellation cancellation) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];

        while (true) {
            if (cancellation != null && cancellation.isCancelled()) {
                throw new CancelledException();
            }

            if (System.nanoTime() / 1_000_000 > deadlineMs) {
                throw new TimeoutException();
            }

            int read = payload.read(buffer);

            if (read == -1) {
                break;
            }

            if (output.size() + read > MAX_RAW_BYTES) {
                throw new OversizeException();
            }

            output.write(buffer, 0, read);
        }

        return output.toByteArray();
    }

    /**
     * Decodes the payload on the decoder's own worker thread.
     *
     * <p>This checkout's {@code SimpleDecoder} performs the decode on a background thread, so
     * {@code dequeueOutputBuffer()} legitimately returns null while work is in flight; polling with
     * a deadline plus cancellation is the only correct way to wait. Stopping at the first null
     * would silently produce an empty timeline.
     */
    private List<SubtitleEvent> decode(SubtitleDecoder decoder, Format format, byte[] data, long deadlineMs, Cancellation cancellation)
            throws SubtitleDecoderException, IOException {
        List<SubtitleEvent> events = new ArrayList<>();
        decoder.setPositionUs(0);

        SubtitleInputBuffer input = decoder.dequeueInputBuffer();

        if (input != null) {
            input.ensureSpaceForWrite(data.length);
            input.data.put(data);
            input.timeUs = 0;
            input.subsampleOffsetUs = format.subsampleOffsetUs; // same rule as TextRenderer
            input.flip();
            decoder.queueInputBuffer(input);
        }

        SubtitleInputBuffer endOfStream = decoder.dequeueInputBuffer();
        boolean endOfStreamQueued = endOfStream != null;

        if (endOfStream != null) {
            endOfStream.setFlags(C.BUFFER_FLAG_END_OF_STREAM);
            endOfStream.timeUs = 0;
            decoder.queueInputBuffer(endOfStream);
        }

        // The decoder never produces the end-of-stream output when the caller could not queue the
        // marker buffer, so the wait is bounded by a grace period as well.
        long graceDeadlineMs = Math.min(deadlineMs, System.nanoTime() / 1_000_000 + DECODE_GRACE_MS);

        while (true) {
            if (cancellation != null && cancellation.isCancelled()) {
                throw new CancelledException();
            }

            if (System.nanoTime() / 1_000_000 > (endOfStreamQueued ? deadlineMs : graceDeadlineMs)) {
                throw new TimeoutException();
            }

            SubtitleOutputBuffer output = decoder.dequeueOutputBuffer();

            if (output == null) {
                sleepQuietly();

                continue;
            }

            boolean endOfStreamBuffer = output.isEndOfStream();
            int count = endOfStreamBuffer ? 0 : output.getEventTimeCount();

            for (int i = 0; i < count; i++) {
                long timeUs = output.getEventTime(i);
                events.add(new SubtitleEvent(timeUs, output.getCues(timeUs)));

                if (events.size() >= MAX_EVENTS) {
                    output.release();
                    return events;
                }
            }

            output.release();

            if (endOfStreamBuffer) {
                return events; // the decode thread is done: the next input was the end marker
            }
        }
    }

    private static void sleepQuietly() throws CancelledException {
        try {
            Thread.sleep(DECODE_POLL_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancelledException();
        }
    }

    private static final class OversizeException extends IOException {
    }

    private static final class TimeoutException extends IOException {
    }

    private static final class CancelledException extends IOException {
    }
}
