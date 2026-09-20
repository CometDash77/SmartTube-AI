package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

/**
 * Validated, non-sensitive AI subtitle settings of one playback.
 *
 * <p>It is the value the menu edits and the pipeline consumes: the translation configuration
 * (endpoint, model, target language, expression instruction), the display mode and whether AI is on
 * for this video. Every field is normalised here so no caller has to repeat the rules, and nothing
 * sensitive is part of this object - the key lives behind its own store (T08).
 */
public final class SubtitleAiSettings {
    private final boolean mEnabled;
    private final int mDisplayMode;
    private final SubtitleTranslationConfig mConfig;

    private SubtitleAiSettings(boolean enabled, int displayMode, SubtitleTranslationConfig config) {
        mEnabled = enabled;
        mDisplayMode = displayMode;
        mConfig = config;
    }

    /**
     * @param targetLanguage user choice or UI language; blank falls back to the UI default
     * @param instruction    user expression preference; trimmed and capped, may be blank
     */
    public static SubtitleAiSettings create(boolean enabled, int displayMode, String targetLanguage, String instruction,
                                            String endpointBaseUrl, String model) {
        int mode = displayMode == SubtitleComposer.MODE_TRANSLATION_ONLY || displayMode == SubtitleComposer.MODE_BILINGUAL
                ? displayMode : SubtitleComposer.MODE_ORIGINAL_ONLY;
        String target = targetLanguage == null || targetLanguage.trim().isEmpty()
                ? SubtitleLanguageSupport.defaultTargetLanguage(null)
                : SubtitleLanguageSupport.defaultTargetLanguage(targetLanguage);
        String style = SubtitleProtocolInstruction.cappedStyle(instruction);

        return new SubtitleAiSettings(enabled, mode,
                new SubtitleTranslationConfig(endpointBaseUrl, model, target, style));
    }

    /** The plan's initial state: AI off for this video, original subtitles only. */
    public static SubtitleAiSettings defaults() {
        return create(false, SubtitleComposer.MODE_ORIGINAL_ONLY, null, null, null, null);
    }

    public static SubtitleAiSettings withEnabled(SubtitleAiSettings settings, boolean enabled) {
        return new SubtitleAiSettings(enabled, settings.mDisplayMode, settings.mConfig);
    }

    public static SubtitleAiSettings withDisplayMode(SubtitleAiSettings settings, int displayMode) {
        return create(settings.mEnabled, displayMode, settings.mConfig.getTargetLanguage(),
                settings.mConfig.getInstruction(), settings.mConfig.getEndpointBaseUrl(), settings.mConfig.getModel());
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    public int getDisplayMode() {
        return mDisplayMode;
    }

    public String getTargetLanguage() {
        return mConfig.getTargetLanguage();
    }

    /** Capped user preference, or null when none was configured. */
    public String getInstruction() {
        return mConfig.getInstruction().isEmpty() ? null : mConfig.getInstruction();
    }

    public SubtitleTranslationConfig getConfig() {
        return mConfig;
    }

    @Override
    public String toString() {
        return "SubtitleAiSettings{enabled=" + mEnabled + ", mode=" + mDisplayMode
                + ", target=" + getTargetLanguage() + ", namespace=" + mConfig.namespace() + "}";
    }
}
