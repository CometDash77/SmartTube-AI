package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

/**
 * Immutable, session-scoped translation configuration.
 *
 * <p>Its {@link #namespace()} is the printable cache/session key of plan 6.3: endpoint, model, target
 * language, the expressive instruction, the smart-context tier and the segmentation rule version
 * (plan 4.1: rule version, rule parameters and context tier are part of the translation identity).
 * The API key is deliberately absent (a key change refreshes the credential generation and stops
 * authorization, it does not rename cached content), and the display mode is absent as well because
 * a mode switch only repaints.
 */
public final class SubtitleTranslationConfig {
    public static final String DEFAULT_MODEL = "deepseek-flash";

    private final String mEndpointBaseUrl;
    private final String mModel;
    private final String mTargetLanguage;
    private final String mInstruction;
    private final int mContextTier;
    private final int mSegmentationRuleVersion;

    public SubtitleTranslationConfig(String endpointBaseUrl, String model, String targetLanguage, String instruction) {
        this(endpointBaseUrl, model, targetLanguage, instruction, 0, 0);
    }

    /**
     * @param contextTier            smart-context tier (0 basic / 1 coherent / 2 video enhanced)
     * @param segmentationRuleVersion 0 when translation units follow the raw cue boundaries, otherwise
     *                                the {@code SEGMENTATION_RULE_VERSION} of the active rule set
     */
    public SubtitleTranslationConfig(String endpointBaseUrl, String model, String targetLanguage, String instruction,
                                     int contextTier, int segmentationRuleVersion) {
        mEndpointBaseUrl = endpointBaseUrl == null ? SubtitleEndpoint.DEFAULT_BASE_URL : endpointBaseUrl;
        mModel = model == null || model.trim().isEmpty() ? DEFAULT_MODEL : model.trim();
        mTargetLanguage = targetLanguage == null ? "" : targetLanguage.trim();
        mInstruction = instruction == null ? "" : instruction.trim();
        mContextTier = contextTier;
        mSegmentationRuleVersion = segmentationRuleVersion;
    }

    public String getEndpointBaseUrl() {
        return mEndpointBaseUrl;
    }

    public String getModel() {
        return mModel;
    }

    public String getTargetLanguage() {
        return mTargetLanguage;
    }

    public String getInstruction() {
        return mInstruction;
    }

    public int getContextTier() {
        return mContextTier;
    }

    /** 0 when translation units are the raw items, otherwise the active segmentation rule version. */
    public int getSegmentationRuleVersion() {
        return mSegmentationRuleVersion;
    }

    /** True when the endpoint is usable at all; an unusable one must never be persisted. */
    public boolean isEndpointUsable() {
        return SubtitleEndpoint.chatCompletionsUrl(mEndpointBaseUrl) != null;
    }

    /** Printable namespace: two configurations with the same namespace may share translations. */
    public String namespace() {
        String normalizedEndpoint = SubtitleEndpoint.chatCompletionsUrl(mEndpointBaseUrl);
        String seed = (normalizedEndpoint != null ? normalizedEndpoint : "invalid")
                + "|" + mModel + "|" + mTargetLanguage + "|" + Integer.toHexString(mInstruction.hashCode())
                + "|" + mContextTier + "|" + mSegmentationRuleVersion;

        return Long.toHexString(fnv(seed));
    }

    public boolean hasSameNamespace(SubtitleTranslationConfig other) {
        return other != null && namespace().equals(other.namespace());
    }

    @Override
    public String toString() {
        return "SubtitleTranslationConfig{endpoint=" + mEndpointBaseUrl + ", model=" + mModel
                + ", target=" + mTargetLanguage + ", context=" + mContextTier
                + ", segRule=" + mSegmentationRuleVersion + ", namespace=" + namespace() + "}";
    }

    private static long fnv(String value) {
        long hash = 0xcbf29ce484222325L;

        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x100000001b3L;
        }

        return hash;
    }
}
