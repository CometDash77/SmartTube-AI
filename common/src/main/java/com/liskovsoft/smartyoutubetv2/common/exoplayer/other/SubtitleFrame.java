package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import com.google.android.exoplayer2.C;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A display frame: the items shown from {@link #getStartUs()} (inclusive) until {@link #getEndUs()}
 * (exclusive). An empty frame is a real clearing boundary, not a missing frame.
 */
public final class SubtitleFrame {
    private final long mStartUs;
    private final long mEndUs;
    private final List<SubtitleItem> mItems;

    public SubtitleFrame(long startUs, long endUs, List<SubtitleItem> items) {
        mStartUs = startUs;
        mEndUs = endUs;
        mItems = items == null ? Collections.<SubtitleItem>emptyList() : Collections.unmodifiableList(new ArrayList<>(items));
    }

    public long getStartUs() {
        return mStartUs;
    }

    /** Exclusive end, or {@link C#TIME_UNSET} when the source has no known end. */
    public long getEndUs() {
        return mEndUs;
    }

    public List<SubtitleItem> getItems() {
        return mItems;
    }

    public boolean isEmpty() {
        return mItems.isEmpty();
    }

    public List<String> getTexts() {
        List<String> texts = new ArrayList<>(mItems.size());

        for (SubtitleItem item : mItems) {
            texts.add(item.getText());
        }

        return texts;
    }

    public boolean containsFrameTime(long positionUs) {
        long endUs = mEndUs == C.TIME_UNSET ? Long.MAX_VALUE : mEndUs;

        return positionUs >= mStartUs && positionUs < endUs;
    }

    @Override
    public String toString() {
        return "SubtitleFrame{" + mStartUs + "-" + mEndUs + " " + getTexts() + "}";
    }
}
