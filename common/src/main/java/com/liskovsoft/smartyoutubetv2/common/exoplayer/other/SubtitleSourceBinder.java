package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import com.google.android.exoplayer2.Format;
import com.liskovsoft.mediaserviceinterfaces.data.MediaSubtitle;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.MediaTrack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Session-scoped binding between the subtitle {@link Format} instances of the manifest that the
 * player is really using and the {@link MediaSubtitle} entries that describe them.
 *
 * <p>Why a manifest-bound table instead of "the first track with this vssId": derived YouTube
 * translation tracks reuse the origin {@code vssId} and may even repeat the displayed name, so id
 * or name matching alone cannot identify a source. The parser stores the source URL verbatim on the
 * representation, so a representation is bound only when exactly one source has that URL. Identity
 * is preserved from the manifest into {@code TrackGroup} (the DASH/SABR media periods copy no
 * {@code Format} objects), so an identity map over the manifest formats is an exact key.
 *
 * <p>Everything here stays in memory for one playback session. No URL is ever logged.
 */
public class SubtitleSourceBinder {
    public enum Status {
        /** No manifest was bound yet, or the binding was cleared. */
        UNBOUND,
        /** The resolved format is the selected subtitle source. */
        BOUND,
        /** The subtitle renderer has no track selected (subtitles off). */
        NOT_SELECTED,
        /** The format belongs to a representation no source entry could be matched to. */
        SOURCE_UNKNOWN,
        /** The URL matched more than one source entry; guessing is forbidden. */
        SOURCE_AMBIGUOUS
    }

    private final Map<Format, SelectedSubtitleSource> mSourceByFormat = new IdentityHashMap<>();
    private final Map<Format, Boolean> mUnresolvedByFormat = new IdentityHashMap<>(); // value: ambiguous
    private int mGeneration;
    private int mBoundCount;
    private int mRejectedCount;
    private Status mStatus = Status.UNBOUND;
    private SelectedSubtitleSource mSelectedSource;

    /**
     * Replaces the whole binding. Called for every constructed media source, so a later response for
     * an older video cannot leave its formats resolvable.
     *
     * @param subtitles  source entries of the request this manifest was built for
     * @param candidates text representations of that same manifest
     */
    public void bind(List<MediaSubtitle> subtitles, List<SubtitleFormatCandidate> candidates) {
        clear();
        mGeneration++;

        if (subtitles == null || candidates == null) {
            return;
        }

        Map<String, List<MediaSubtitle>> byUrl = new HashMap<>();

        for (MediaSubtitle subtitle : subtitles) {
            if (subtitle == null || subtitle.getBaseUrl() == null) {
                continue;
            }

            List<MediaSubtitle> bucket = byUrl.get(subtitle.getBaseUrl());

            if (bucket == null) {
                bucket = new ArrayList<>();
                byUrl.put(subtitle.getBaseUrl(), bucket);
            }

            bucket.add(subtitle);
        }

        for (SubtitleFormatCandidate candidate : candidates) {
            if (candidate == null || candidate.getFormat() == null) {
                continue;
            }

            List<MediaSubtitle> matches = candidate.getBaseUrl() != null ? byUrl.get(candidate.getBaseUrl()) : null;

            if (matches == null || matches.size() != 1) {
                // 0 matches: the source list and the manifest disagree.
                // 2+ matches: the URL cannot identify one source. Never pick the first candidate.
                mUnresolvedByFormat.put(candidate.getFormat(), matches != null && matches.size() > 1);
                mRejectedCount++;
                continue;
            }

            mSourceByFormat.put(candidate.getFormat(), SelectedSubtitleSource.from(matches.get(0), candidate.getBaseUrl(), mGeneration));
            mBoundCount++;
        }
    }

    /**
     * Resolves the subtitle source of an actually selected track.
     *
     * @param track the selected subtitle track, or null/empty when subtitles are off
     * @return the bound source, or null with {@link #getStatus()} explaining the refusal
     */
    public SelectedSubtitleSource resolve(MediaTrack track) {
        if (track == null || track.format == null || track.isEmpty()) {
            mSelectedSource = null;
            mStatus = Status.NOT_SELECTED;
            return null;
        }

        return resolve(track.format);
    }

    /** Resolves a selected subtitle {@link Format} by identity. */
    public SelectedSubtitleSource resolve(Format format) {
        if (format == null) {
            mSelectedSource = null;
            mStatus = Status.NOT_SELECTED;
            return null;
        }

        SelectedSubtitleSource source = mSourceByFormat.get(format);

        if (source != null) {
            mSelectedSource = source;
            mStatus = Status.BOUND;
            return source;
        }

        mSelectedSource = null;

        Boolean ambiguous = mUnresolvedByFormat.get(format);
        mStatus = ambiguous == null ? (mBoundCount == 0 && mGeneration == 0 ? Status.UNBOUND : Status.SOURCE_UNKNOWN)
                : (ambiguous ? Status.SOURCE_AMBIGUOUS : Status.SOURCE_UNKNOWN);

        return null;
    }

    public Status getStatus() {
        return mStatus;
    }

    public SelectedSubtitleSource getSelectedSource() {
        return mSelectedSource;
    }

    /** Source generation of the current binding; 0 when nothing was bound yet. */
    public int getGeneration() {
        return mGeneration;
    }

    /** Number of representations that were tied to exactly one source entry. */
    public int getBoundCount() {
        return mBoundCount;
    }

    /** Number of representations that were rejected (unknown or ambiguous source). */
    public int getRejectedCount() {
        return mRejectedCount;
    }

    public void clear() {
        mSourceByFormat.clear();
        mUnresolvedByFormat.clear();
        mBoundCount = 0;
        mRejectedCount = 0;
        mSelectedSource = null;
        mStatus = Status.UNBOUND;
    }
}
