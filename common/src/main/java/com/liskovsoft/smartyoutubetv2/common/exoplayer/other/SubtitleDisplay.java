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

    /**
     * Rule segmentation (plan 4.3.6): the original lines of the derived sentence frame that is on
     * screen, or null to hand the original text back to the native cue path.
     *
     * <p>Both the original and the translation of a derived frame are written through this display, so
     * there is still exactly one write entry. The default is a no-op for implementations that never
     * render derived text; {@link SubtitleManager} is the production implementation that must.
     */
    default void setDerivedOriginalLines(List<String> lines) {
    }

    /** Drops the incremental original-text state (seek, track or video change). */
    void resetOriginalCueState();
}
