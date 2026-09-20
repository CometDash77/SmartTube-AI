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

    private SubtitleTranslationRequest(String url, String authorization, String body) {
        mUrl = url;
        mAuthorization = authorization;
        mBody = body;
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

        return new SubtitleTranslationRequest(url, authorization, SubtitleRequestBuilder.buildPayload(
                sourceLanguage, config.getTargetLanguage(), batch));
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

    /** The system instruction that belongs to the same configuration. */
    public String getSystemInstruction(SubtitleTranslationConfig config, String userStyle) {
        return SubtitleProtocolInstruction.systemInstruction(config != null ? config.getTargetLanguage() : null, userStyle);
    }

    @Override
    public String toString() {
        return "SubtitleTranslationRequest{url=" + mUrl + ", body=" + mBody + "}";
    }
}
