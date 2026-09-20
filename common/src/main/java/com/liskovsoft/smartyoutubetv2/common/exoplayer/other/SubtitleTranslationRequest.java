package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.json.JSONException;

/**
 * One prepared translation request: endpoint, authorization header and the fixed JSON body.
 *
 * <p>It is produced only when the endpoint is acceptable and a key is present, so an unusable
 * configuration can never turn into a network call. The key is exposed solely as this header value
 * and must never be copied into a message or a log.
 */
public final class SubtitleTranslationRequest {
    private final String mUrl;
    private final String mAuthorization;
    private final String mBody;
    /** The instruction this request was built for; null means "the translation instruction". */
    private final String mSystemInstruction;

    private SubtitleTranslationRequest(String url, String authorization, String body) {
        this(url, authorization, body, null);
    }

    private SubtitleTranslationRequest(String url, String authorization, String body, String systemInstruction) {
        mUrl = url;
        mAuthorization = authorization;
        mBody = body;
        mSystemInstruction = systemInstruction;
    }

    /**
     * One prepared context-analysis request (plan 4.2). It shares the endpoint, the credential and the
     * transport with the translations, but has its own fixed instruction and its smaller output bound;
     * a missing key or an unusable endpoint still returns null instead of calling out.
     */
    public static SubtitleTranslationRequest createSummary(SubtitleTranslationConfig config, String apiKey,
                                                           String payload) throws JSONException {
        if (config == null) {
            return null;
        }

        String url = SubtitleEndpoint.chatCompletionsUrl(config.getEndpointBaseUrl());
        String authorization = SubtitleCredentials.bearerHeader(apiKey);

        if (url == null || authorization == null) {
            return null;
        }

        String instruction = SubtitleProtocolInstruction.summaryInstruction(config.getTargetLanguage());
        String body = SubtitleRequestBuilder.buildChatCompletionsBody(config, instruction, payload, null,
                SubtitleRequestBuilder.MAX_SUMMARY_OUTPUT_TOKENS);

        return new SubtitleTranslationRequest(url, authorization, body, instruction);
    }

    /**
     * @return the prepared request, or null when the endpoint is unusable or no key is configured
     */
    public static SubtitleTranslationRequest create(SubtitleTranslationConfig config, String apiKey,
                                                    String sourceLanguage, SubtitleBatch batch) throws JSONException {
        if (config == null) {
            return null;
        }

        String url = SubtitleEndpoint.chatCompletionsUrl(config.getEndpointBaseUrl());
        String authorization = SubtitleCredentials.bearerHeader(apiKey);

        if (url == null || authorization == null) {
            return null;
        }

        String payload = SubtitleRequestBuilder.buildPayload(sourceLanguage, config.getTargetLanguage(), batch,
                config.getContextTier());
        String body = SubtitleRequestBuilder.buildChatCompletionsBody(config,
                SubtitleProtocolInstruction.systemInstruction(config.getTargetLanguage(), null), payload,
                SubtitleProtocolInstruction.cappedStyle(config.getInstruction()));
        return new SubtitleTranslationRequest(url, authorization, body);
    }

    /**
     * Visible for wire-level tests only. The product path always builds requests through
     * {@link #create}, which refuses plain HTTP; a test needs a local address the product would reject.
     */
    static SubtitleTranslationRequest unsafeCreateForTest(String url, String authorization, String body) {
        return new SubtitleTranslationRequest(url, authorization, body);
    }

    public String getUrl() {
        return mUrl;
    }

    /** Header value containing the key; never log or persist it. */
    public String getAuthorization() {
        return mAuthorization;
    }

    public String getBody() {
        return mBody;
    }

    /** The system instruction that belongs to this request. */
    public String getSystemInstruction(SubtitleTranslationConfig config, String userStyle) {
        return mSystemInstruction != null ? mSystemInstruction
                : SubtitleProtocolInstruction.systemInstruction(
                        config != null ? config.getTargetLanguage() : null, userStyle);
    }

    @Override
    public String toString() {
        return "SubtitleTranslationRequest{prepared=true}";
    }
}
