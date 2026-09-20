package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Target languages the AI subtitle menu offers (plan section 5).
 *
 * <p>The list is owned by the programme and does not depend on the translation tracks a particular
 * video happens to return; Simplified and Traditional Chinese are explicit entries with their writing
 * system normalised, which is what the plan asks for. Display names are plain English here so the list
 * can be built without resources; the menu may replace them with localised labels later.
 */
public final class SubtitleTargetLanguages {
    public static final String SIMPLIFIED_CHINESE = SubtitleLanguageSupport.ZH_HANS;
    public static final String TRADITIONAL_CHINESE = SubtitleLanguageSupport.ZH_HANT;

    private static final List<String> CODES = Collections.unmodifiableList(Arrays.asList(
            SIMPLIFIED_CHINESE, TRADITIONAL_CHINESE, "en", "ja", "ko", "es", "fr", "de", "it", "pt",
            "ru", "ar", "hi", "id", "vi", "th", "tr", "pl", "nl", "uk"));

    private static final List<String> NAMES = Collections.unmodifiableList(Arrays.asList(
            "Chinese (Simplified)", "Chinese (Traditional)", "English", "Japanese", "Korean", "Spanish",
            "French", "German", "Italian", "Portuguese", "Russian", "Arabic", "Hindi", "Indonesian",
            "Vietnamese", "Thai", "Turkish", "Polish", "Dutch", "Ukrainian"));

    private SubtitleTargetLanguages() {
    }

    /** Codes in menu order; the two Chinese writing systems come first. */
    public static List<String> getCodes() {
        return new ArrayList<>(CODES);
    }

    public static String getDisplayName(String code) {
        if (code == null) {
            return null;
        }

        String normalized = SubtitleLanguageSupport.normalizeWritingSystem(code);
        int index = normalized != null ? CODES.indexOf(normalized) : -1;

        return index >= 0 ? NAMES.get(index) : code;
    }

    /** Unknown codes are allowed (the model may support more), but the menu marks them as custom. */
    public static boolean isOffered(String code) {
        String normalized = SubtitleLanguageSupport.normalizeWritingSystem(code);

        return normalized != null && CODES.contains(normalized);
    }

    /** The language used when the UI locale cannot be recognised (matches the plan's default). */
    public static String defaultCode() {
        return SubtitleLanguageSupport.DEFAULT_TARGET_LANGUAGE;
    }
}
