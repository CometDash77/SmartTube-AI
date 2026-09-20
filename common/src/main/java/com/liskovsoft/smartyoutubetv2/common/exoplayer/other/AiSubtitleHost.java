package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

/**
 * Implemented only by a player surface that can host AI subtitles.
 *
 * <p>It is a separate optional interface on purpose: the embed player and the remote-receiver
 * manager keep AI subtitles off instead of having to stub methods they cannot support.
 */
public interface AiSubtitleHost {
    /** The UI display surface, or null while the subtitle view does not exist yet. */
    SubtitleDisplay getSubtitleDisplay();

    /** The currently bound subtitle source of the player, or null when subtitles are off. */
    SelectedSubtitleSource getSelectedSubtitleSource();

    /** Format of the selected subtitle track, or null when subtitles are off. */
    com.google.android.exoplayer2.Format getSelectedSubtitleFormat();

    /**
     * Result of the last {@link SubtitleSourceBinder} resolution. Callers must resolve first
     * ({@link #getSelectedSubtitleSource()}) so this status belongs to the current selection instead
     * of an earlier one.
     */
    SubtitleSourceBinder.Status getSubtitleSourceStatus();

    /**
     * Payload factory of the AI subtitle snapshot: the player's own data source for the bound source
     * (plan 4.1), or null when this surface cannot provide one.
     */
    SubtitleSnapshotFetcher.PayloadFactory createSubtitlePayloadFactory();
}
