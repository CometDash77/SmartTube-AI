package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import java.util.ArrayList;
import java.util.List;

/**
 * Aligns stored translations to the cue slots of the frame that is currently displayed.
 *
 * <p>The alignment is driven by the stable item ids of the source timeline, never by the order in
 * which a translation service answered: a slot without a stored translation stays null so the
 * composer falls back to the original line.
 */
public final class SubtitleFrameTranslations {
    /** Lookup of a stored translation by stable item id. */
    public interface TranslationLookup {
        String get(String itemId);
    }

    private SubtitleFrameTranslations() {
    }

    public static List<String> align(List<SubtitleItem> items, TranslationLookup lookup) {
        List<String> result = new ArrayList<>();

        if (items == null) {
            return result;
        }

        for (SubtitleItem item : items) {
            result.add(item != null && lookup != null ? lookup.get(item.getItemId()) : null);
        }

        return result;
    }
}
