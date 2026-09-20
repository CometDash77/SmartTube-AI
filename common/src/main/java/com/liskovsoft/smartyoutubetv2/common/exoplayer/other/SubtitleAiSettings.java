package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

/**
 * Validated, non-sensitive AI subtitle settings of one playback.
 *
 * <p>It is the value the menu edits and the pipeline consumes: the translation configuration
 * (endpoint, model, target language, expression instruction), the display mode, the smart-context
 * tier, the rule-segmentation switch, the load-notification switch and whether AI is on for this
 * video. Every field is normalised here so no caller has to repeat the rules, and nothing sensitive
 * is part of this object - the key lives behind its own store (T08).
 *
 * <p>Kiss feature mapping (plan sections 2 and 4.1): the context tier and the segmentation switch
 * change what is sent to the model, so they are part of the translation identity and changing them
 * rebuilds the translation session. The notification switch only changes what the player shows; it
 * never invalidates a session and never starts a request.
 */
public final class SubtitleAiSettings {
    /** Basic neighbouring original lines only; the previous default and the lowest cost. */
    public static final int CONTEXT_BASIC = 0;
    /** Neighbouring lines plus verified translation examples of the same session. */
    public static final int CONTEXT_COHERENT = 1;
    /** Coherent context plus one bounded video-summary/terminology analysis. */
    public static final int CONTEXT_VIDEO_ENHANCED = 2;

    /**
     * Version of the built-in segmentation rules (Kiss feature: rule segmentation). It is part of
     * the translation identity and of every derived segment id, so results produced under different
     * rule versions never mix. {@code SubtitleRuleSegmenter} records the same value.
     */
    public static final int SEGMENTATION_RULE_VERSION = 1;

    private final boolean mEnabled;
    private final int mDisplayMode;
    private final int mContextTier;
    private final boolean mRuleSegmentation;
    private final boolean mLoadNotifications;
    private final SubtitleTranslationConfig mConfig;

    private SubtitleAiSettings(boolean enabled, int displayMode, int contextTier, boolean ruleSegmentation,
                               boolean loadNotifications, SubtitleTranslationConfig config) {
        mEnabled = enabled;
        mDisplayMode = displayMode;
        mContextTier = contextTier;
        mRuleSegmentation = ruleSegmentation;
        mLoadNotifications = loadNotifications;
        mConfig = config;
    }

    /**
     * @param targetLanguage user choice or UI language; blank falls back to the UI default
     * @param instruction    user expression preference; trimmed and capped, may be blank
     */
    public static SubtitleAiSettings create(boolean enabled, int displayMode, String targetLanguage, String instruction,
                                            String endpointBaseUrl, String model) {
        return create(enabled, displayMode, targetLanguage, instruction, endpointBaseUrl, model,
                CONTEXT_BASIC, false, true);
    }

    /**
     * @param contextTier        one of the {@code CONTEXT_*} tiers; an unknown value falls back to basic
     * @param ruleSegmentation   true when full sentences are derived from the original cues
     * @param loadNotifications  true when the player may show short subtitle load states
     */
    public static SubtitleAiSettings create(boolean enabled, int displayMode, String targetLanguage, String instruction,
                                            String endpointBaseUrl, String model,
                                            int contextTier, boolean ruleSegmentation, boolean loadNotifications) {
        int mode = displayMode == SubtitleComposer.MODE_TRANSLATION_ONLY || displayMode == SubtitleComposer.MODE_BILINGUAL
                ? displayMode : SubtitleComposer.MODE_ORIGINAL_ONLY;
        String target = targetLanguage == null || targetLanguage.trim().isEmpty()
                ? SubtitleLanguageSupport.defaultTargetLanguage(null)
                : SubtitleLanguageSupport.defaultTargetLanguage(targetLanguage);
        String style = SubtitleProtocolInstruction.cappedStyle(instruction);
        int tier = normalizeTier(contextTier);

        return new SubtitleAiSettings(enabled, mode, tier, ruleSegmentation, loadNotifications,
                new SubtitleTranslationConfig(endpointBaseUrl, model, target, style, tier,
                        ruleSegmentation ? SEGMENTATION_RULE_VERSION : 0));
    }

    /** The plan's initial state: AI off for this video, original subtitles only. */
    public static SubtitleAiSettings defaults() {
        return create(false, SubtitleComposer.MODE_ORIGINAL_ONLY, null, null, null, null);
    }

    public static SubtitleAiSettings withEnabled(SubtitleAiSettings settings, boolean enabled) {
        return new SubtitleAiSettings(enabled, settings.mDisplayMode, settings.mContextTier,
                settings.mRuleSegmentation, settings.mLoadNotifications, settings.mConfig);
    }

    public static SubtitleAiSettings withDisplayMode(SubtitleAiSettings settings, int displayMode) {
        return create(settings.mEnabled, displayMode, settings.mConfig.getTargetLanguage(),
                settings.mConfig.getInstruction(), settings.mConfig.getEndpointBaseUrl(), settings.mConfig.getModel(),
                settings.mContextTier, settings.mRuleSegmentation, settings.mLoadNotifications);
    }

    public static SubtitleAiSettings withContextTier(SubtitleAiSettings settings, int contextTier) {
        return create(settings.mEnabled, settings.mDisplayMode, settings.mConfig.getTargetLanguage(),
                settings.mConfig.getInstruction(), settings.mConfig.getEndpointBaseUrl(), settings.mConfig.getModel(),
                contextTier, settings.mRuleSegmentation, settings.mLoadNotifications);
    }

    public static SubtitleAiSettings withRuleSegmentation(SubtitleAiSettings settings, boolean ruleSegmentation) {
        return create(settings.mEnabled, settings.mDisplayMode, settings.mConfig.getTargetLanguage(),
                settings.mConfig.getInstruction(), settings.mConfig.getEndpointBaseUrl(), settings.mConfig.getModel(),
                settings.mContextTier, ruleSegmentation, settings.mLoadNotifications);
    }

    public static SubtitleAiSettings withLoadNotifications(SubtitleAiSettings settings, boolean loadNotifications) {
        return create(settings.mEnabled, settings.mDisplayMode, settings.mConfig.getTargetLanguage(),
                settings.mConfig.getInstruction(), settings.mConfig.getEndpointBaseUrl(), settings.mConfig.getModel(),
                settings.mContextTier, settings.mRuleSegmentation, loadNotifications);
    }

    private static int normalizeTier(int contextTier) {
        return contextTier >= CONTEXT_BASIC && contextTier <= CONTEXT_VIDEO_ENHANCED
                ? contextTier : CONTEXT_BASIC;
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    public int getDisplayMode() {
        return mDisplayMode;
    }

    /** One of the {@code CONTEXT_*} tiers; always valid. */
    public int getContextTier() {
        return mContextTier;
    }

    /** True when full sentences are derived from the original cues instead of translating raw items. */
    public boolean usesRuleSegmentation() {
        return mRuleSegmentation;
    }

    /** True when the player may show short subtitle load states. */
    public boolean showsLoadNotifications() {
        return mLoadNotifications;
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
                + ", target=" + getTargetLanguage() + ", context=" + mContextTier
                + ", segmented=" + mRuleSegmentation + ", notifications=" + mLoadNotifications
                + ", namespace=" + mConfig.namespace() + "}";
    }
}
