package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import java.util.List;

/**
 * The only display surface the AI subtitle session is allowed to touch.
 *
 * <p>{@link SubtitleManager} is the production implementation, so the controller can never write to
 * a second subtitle window or bypass the original-text processing.
 */
public interface SubtitleDisplay {
    /** Translations aligned to the current frame's cue slots; missing entries are null. */
    void setTranslations(List<String> translations);

    void clearTranslations();

    void setAiDisplayMode(int mode);

    /** Drops the incremental original-text state (seek, track or video change). */
    void resetOriginalCueState();
}
