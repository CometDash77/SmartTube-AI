package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import com.google.android.exoplayer2.text.Cue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One change point of the subtitle stream: the cues the player shows from {@link #getTimeUs()}
 * until the next event. Time is in microseconds on the media timeline, exactly as
 * {@code TextRenderer} consumes it.
 */
public final class SubtitleEvent {
    private final long mTimeUs;
    private final List<Cue> mCues;
    private final List<String> mTexts;

    public SubtitleEvent(long timeUs, List<Cue> cues) {
        mTimeUs = timeUs;
        mCues = cues == null ? Collections.<Cue>emptyList() : Collections.unmodifiableList(new ArrayList<>(cues));

        List<String> texts = new ArrayList<>(mCues.size());

        for (Cue cue : mCues) {
            texts.add(cue != null && cue.text != null ? cue.text.toString() : "");
        }

        mTexts = Collections.unmodifiableList(texts);
    }

    public long getTimeUs() {
        return mTimeUs;
    }

    public List<Cue> getCues() {
        return mCues;
    }

    public List<String> getCueTexts() {
        return mTexts;
    }

    @Override
    public String toString() {
        return "SubtitleEvent{timeUs=" + mTimeUs + ", texts=" + mTexts + "}";
    }
}
