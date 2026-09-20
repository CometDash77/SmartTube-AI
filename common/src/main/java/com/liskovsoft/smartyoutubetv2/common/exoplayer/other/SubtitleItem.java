package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

/**
 * One translatable subtitle item of a display frame.
 *
 * <p>The id is stable for as long as the same text stays in the same cue slot across consecutive
 * frames, so an overlapping cue appearing or disappearing does not re-translate unchanged text.
 * Text that disappears and later reappears receives a new id: it is a different event.
 */
public final class SubtitleItem {
    private final String mItemId;
    private final String mText;

    public SubtitleItem(String itemId, String text) {
        mItemId = itemId;
        mText = text;
    }

    public String getItemId() {
        return mItemId;
    }

    public String getText() {
        return mText;
    }

    @Override
    public String toString() {
        return "SubtitleItem{" + mItemId + "=" + mText + "}";
    }
}
