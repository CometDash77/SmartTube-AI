package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

/**
 * Transport seam of the translation service.
 *
 * <p>The real implementation uses a dedicated clean HTTP client; tests provide a fake. The request
 * already contains the endpoint, the authorization header and the body, so an implementation must
 * not add headers, follow redirects implicitly or retry by itself - retry policy belongs to
 * {@link SubtitleRetryPolicy}.
 */
public interface SubtitleTranslationClient {
    /** Handle of one in-flight call; the caller (the dispatcher) owns cancellation. */
    interface Cancellable {
        void cancel();
    }

    /** Exactly one of the two methods is called per request. */
    interface ResponseHandler {
        /** @param retryAfterMs parsed {@code Retry-After} delay, or a negative value when absent */
        void onResponse(int status, long retryAfterMs, boolean truncated, String body);

        void onTransportFailure();
    }

    Cancellable send(SubtitleTranslationRequest request, String systemInstruction, ResponseHandler handler);
}
