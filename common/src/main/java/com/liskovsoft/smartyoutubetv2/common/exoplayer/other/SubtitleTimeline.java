package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import com.google.android.exoplayer2.C;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable, bounded subtitle timeline of one source snapshot: every event boundary of the native
 * decoder, the original text of each frame, stable item ids and a content fingerprint.
 *
 * <p>Timings stay in the native microsecond base; the caller must convert the player position
 * (milliseconds) exactly once. Nothing here is persisted.
 */
public final class SubtitleTimeline {
    private final List<SubtitleFrame> mFrames;
    private final String mContentFingerprint;

    public SubtitleTimeline(List<SubtitleFrame> frames, String contentFingerprint) {
        mFrames = frames == null ? Collections.<SubtitleFrame>emptyList() : Collections.unmodifiableList(new ArrayList<>(frames));
        mContentFingerprint = contentFingerprint;
    }

    public List<SubtitleFrame> getFrames() {
        return mFrames;
    }

    public int size() {
        return mFrames.size();
    }

    public boolean isEmpty() {
        return mFrames.isEmpty();
    }

    /** Fingerprint of the source content; part of the session cache key, not a credential. */
    public String getContentFingerprint() {
        return mContentFingerprint;
    }

    /**
     * Frame displayed at a position, or null before the first event.
     *
     * @param positionUs position in microseconds
     */
    public SubtitleFrame frameAt(long positionUs) {
        int low = 0;
        int high = mFrames.size() - 1;
        SubtitleFrame result = null;

        while (low <= high) {
            int middle = (low + high) >>> 1;
            SubtitleFrame frame = mFrames.get(middle);

            if (frame.getStartUs() <= positionUs) {
                result = frame;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }

        return result;
    }

    /**
     * Frames that still intersect the prefetch window
     * {@code end > positionUs && start < positionUs + aheadUs}.
     */
    public List<SubtitleFrame> framesInWindow(long positionUs, long aheadUs) {
        List<SubtitleFrame> result = new ArrayList<>();
        long windowEndUs = positionUs + aheadUs;

        for (SubtitleFrame frame : mFrames) {
            long frameEndUs = frame.getEndUs() == C.TIME_UNSET ? Long.MAX_VALUE : frame.getEndUs();

            if (frameEndUs > positionUs && frame.getStartUs() < windowEndUs) {
                result.add(frame);
            }
        }

        return result;
    }

    @Override
    public String toString() {
        return "SubtitleTimeline{frames=" + mFrames.size() + ", fingerprint=" + mContentFingerprint + "}";
    }
}
