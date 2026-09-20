package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

/**
 * One source/target text pair of the translation context (plan 4.2): a verified translation example
 * or one terminology entry of the video summary. Both texts are already normalised by their producer;
 * the class only carries them.
 */
public final class SubtitleTextPair {
    private final String mSource;
    private final String mTarget;

    public SubtitleTextPair(String source, String target) {
        mSource = source;
        mTarget = target;
    }

    public String getSource() {
        return mSource;
    }

    public String getTarget() {
        return mTarget;
    }

    /** True when both sides carry usable text; a half-empty pair is never sent. */
    public boolean isUsable() {
        return mSource != null && !mSource.trim().isEmpty()
                && mTarget != null && !mTarget.trim().isEmpty();
    }

    public int codePoints() {
        return SubtitleBatch.codePoints(mSource) + SubtitleBatch.codePoints(mTarget);
    }

    @Override
    public String toString() {
        return "SubtitleTextPair{" + (mSource != null ? mSource.length() : 0) + "->"
                + (mTarget != null ? mTarget.length() : 0) + "}";
    }
}
