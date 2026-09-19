package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import com.google.android.exoplayer2.Format;

/**
 * One text representation found in the manifest the player actually uses, keyed by the exact
 * {@link Format} instance that reaches the track selector.
 *
 * <p>The base URL is kept in memory only so that a source can be matched without guessing. It must
 * never be written to a log, an evidence file or a translation request.
 */
public final class SubtitleFormatCandidate {
    private final Format mFormat;
    private final String mBaseUrl;

    public SubtitleFormatCandidate(Format format, String baseUrl) {
        mFormat = format;
        mBaseUrl = baseUrl;
    }

    public Format getFormat() {
        return mFormat;
    }

    /** Memory-only locator of the representation; never log or transmit it. */
    public String getBaseUrl() {
        return mBaseUrl;
    }

    @Override
    public String toString() {
        return "SubtitleFormatCandidate{formatId=" + (mFormat != null ? mFormat.id : null) + "}";
    }
}
