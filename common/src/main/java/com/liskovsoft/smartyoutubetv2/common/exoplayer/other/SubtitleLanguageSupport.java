package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

/**
 * Language rules of plan section 5, kept pure so they can be tested without Android locales.
 *
 * <p>A bare {@code zh} stays unknown: Simplified and Traditional are never guessed from it. Only
 * explicit region codes are normalised ({@code zh-CN}/{@code zh-SG} to {@code zh-Hans},
 * {@code zh-TW}/{@code zh-HK}/{@code zh-MO} to {@code zh-Hant}), and the target language never
 * depends on the translation tracks a particular video happens to offer.
 */
public final class SubtitleLanguageSupport {
    public static final String ZH_HANS = "zh-Hans";
    public static final String ZH_HANT = "zh-Hant";
    public static final String DEFAULT_TARGET_LANGUAGE = "en";

    private SubtitleLanguageSupport() {
    }

    /**
     * @return the normalised writing system code, or null when the code is unknown (including a
     * bare {@code zh})
     */
    public static String normalizeWritingSystem(String languageCode) {
        if (languageCode == null) {
            return null;
        }

        String code = languageCode.trim().toLowerCase();

        if (code.isEmpty()) {
            return null;
        }

        if ("zh-hans".equals(code) || "zh-cn".equals(code) || "zh-sg".equals(code) || "zh-my".equals(code)) {
            return ZH_HANS;
        }

        if ("zh-hant".equals(code) || "zh-tw".equals(code) || "zh-hk".equals(code) || "zh-mo".equals(code)) {
            return ZH_HANT;
        }

        if ("zh".equals(code) || code.startsWith("zh-")) {
            return null; // unknown Chinese variant: do not assume a writing system
        }

        // For every other language the writing system follows the language itself, so regional
        // variants such as en-US and en-GB compare as the same writing system.
        int separator = code.indexOf('-');

        return separator > 0 ? code.substring(0, separator) : code;
    }

    /** True when source and target are known to use the same writing system, so nothing is sent. */
    public static boolean isSameWritingSystem(String sourceLanguageCode, String targetLanguageCode) {
        String source = normalizeWritingSystem(sourceLanguageCode);
        String target = normalizeWritingSystem(targetLanguageCode);

        return source != null && target != null && source.equals(target);
    }

    /**
     * Target language for a UI locale: the normalised writing system when it is known, otherwise
     * English, which is what the menu then shows.
     */
    public static String defaultTargetLanguage(String uiLanguageCode) {
        String normalized = normalizeWritingSystem(uiLanguageCode);

        return normalized != null ? normalized : DEFAULT_TARGET_LANGUAGE;
    }
}
