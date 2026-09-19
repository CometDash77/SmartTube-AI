package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import com.liskovsoft.mediaserviceinterfaces.data.MediaSubtitle;

/**
 * Immutable snapshot of the subtitle source that the user is actually watching.
 *
 * <p>It is produced only when the selected {@link com.google.android.exoplayer2.Format} instance can
 * be tied to exactly one manifest representation and that representation to exactly one source
 * entry. Anything else must stay unbound so that the player keeps showing the original subtitles
 * instead of translating the wrong track.
 *
 * <p>The base URL is memory-only. It is deliberately excluded from {@link #toString()} and must never
 * be persisted, logged or sent to a translation service.
 */
public final class SelectedSubtitleSource {
    private final int mGeneration;
    private final String mBaseUrl;
    private final String mVssId;
    private final String mLanguageCode;
    private final String mName;
    private final String mMimeType;
    private final String mCodecs;
    private final String mType;
    private final boolean mIsTranslatable;

    SelectedSubtitleSource(int generation, String baseUrl, String vssId, String languageCode, String name,
                           String mimeType, String codecs, String type, boolean isTranslatable) {
        mGeneration = generation;
        mBaseUrl = baseUrl;
        mVssId = vssId;
        mLanguageCode = languageCode;
        mName = name;
        mMimeType = mimeType;
        mCodecs = codecs;
        mType = type;
        mIsTranslatable = isTranslatable;
    }

    static SelectedSubtitleSource from(MediaSubtitle subtitle, String baseUrl, int generation) {
        return new SelectedSubtitleSource(generation, baseUrl, subtitle.getVssId(), subtitle.getLanguageCode(),
                subtitle.getName(), subtitle.getMimeType(), subtitle.getCodecs(), subtitle.getType(),
                subtitle.isTranslatable());
    }

    /** Source generation; changes for every new media source so stale results can be rejected. */
    public int getGeneration() {
        return mGeneration;
    }

    /** Memory-only locator; never log, persist or transmit it. */
    public String getBaseUrl() {
        return mBaseUrl;
    }

    public String getVssId() {
        return mVssId;
    }

    public String getLanguageCode() {
        return mLanguageCode;
    }

    public String getName() {
        return mName;
    }

    public String getMimeType() {
        return mMimeType;
    }

    public String getCodecs() {
        return mCodecs;
    }

    public String getType() {
        return mType;
    }

    public boolean isTranslatable() {
        return mIsTranslatable;
    }

    @Override
    public String toString() {
        return "SelectedSubtitleSource{generation=" + mGeneration + ", vssId=" + mVssId
                + ", languageCode=" + mLanguageCode + ", name=" + mName + ", mimeType=" + mMimeType + "}";
    }
}
