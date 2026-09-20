package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/**
 * The manual "test connection" action of the AI settings menu (plan 18.3).
 *
 * <p>It sends one minimal synthetic batch through the same transport the translations use and reduces
 * the answer to one outcome code the menu can explain. It never sends the subtitles being watched,
 * never reuses the video's track and never carries a key, a URL or response text into the outcome.
 */
public class SubtitleConnectionTest {
    /** One classified connection result; the menu maps it to a localised action. */
    public enum Outcome {
        /** The service answered with a valid translation for the synthetic batch. */
        OK,
        /** No key or an unusable endpoint: nothing was sent. */
        NOT_CONFIGURED,
        /** 401/403: the key is missing, wrong or not authorized for this endpoint. */
        AUTH_FAILED,
        /** 402: the account has no balance for this service. */
        NO_BALANCE,
        /** 400/404 or an unexpected status: the endpoint or the model is wrong. */
        BAD_REQUEST,
        /** 429: the service is rate limiting this account. */
        RATE_LIMITED,
        /** 5xx: the service is unavailable right now. */
        SERVER_ERROR,
        /** The request never completed (offline, timeout, DNS, refused connection). */
        NETWORK,
        /** 200 but the answer did not follow the agreed protocol. */
        PROTOCOL
    }

    /** Called exactly once, possibly on the transport's own thread. */
    public interface Listener {
        void onFinished(Outcome outcome);

        default void onHttpFinished(Outcome outcome, int status) {
            onFinished(outcome);
        }
    }

    /** Fixed sample text: two short lines with stable ids, no context, no real subtitle content. */
    private static final String[] SAMPLE_TEXTS = { "Good morning.", "See you tomorrow." };
    private static final String SAMPLE_SOURCE_LANGUAGE = "en";

    private final SubtitleTranslationClient mClient;

    public SubtitleConnectionTest(SubtitleTranslationClient client) {
        mClient = client;
    }

    static SubtitleBatch sampleBatch() {
        List<SubtitleItem> items = new ArrayList<>(SAMPLE_TEXTS.length);

        for (int i = 0; i < SAMPLE_TEXTS.length; i++) {
            items.add(new SubtitleItem("connection-test-" + i, SAMPLE_TEXTS[i]));
        }

        return new SubtitleBatch(items, null, null);
    }

    /**
     * @param apiKey the key for this attempt only; it is not stored here
     * @return a handle the caller may cancel, or an inert handle when nothing was sent
     */
    public SubtitleTranslationClient.Cancellable test(SubtitleTranslationConfig config, String apiKey,
                                                      String userStyle, final Listener listener) {
        final SubtitleBatch batch = sampleBatch();
        SubtitleTranslationRequest request;

        try {
            request = SubtitleTranslationRequest.create(config, apiKey, SAMPLE_SOURCE_LANGUAGE, batch);
        } catch (JSONException e) {
            finish(listener, Outcome.PROTOCOL);

            return () -> { };
        }

        if (request == null) {
            // No key or an endpoint the product refuses: nothing may leave the device.
            finish(listener, Outcome.NOT_CONFIGURED);

            return () -> { };
        }

        SubtitleTranslationClient.Cancellable call = mClient.send(request,
                request.getSystemInstruction(config, userStyle), new SubtitleTranslationClient.ResponseHandler() {
                    @Override
                    public void onResponse(int status, long retryAfterMs, boolean truncated, String body) {
                        if (listener != null) {
                            listener.onHttpFinished(classify(status, truncated, body, batch.getItemIds()), status);
                        }
                    }

                    @Override
                    public void onTransportFailure() {
                        finish(listener, Outcome.NETWORK);
                    }
                });

        return call != null ? call : () -> { };
    }

    static Outcome classify(int status, boolean truncated, String body, List<String> requestedIds) {
        if (status != SubtitleResponseHandler.STATUS_OK) {
            return statusOutcome(status);
        }

        SubtitleResponseParser.Result parsed = SubtitleResponseParser.parse(body, truncated, requestedIds);

        // A damaged answer means the endpoint is reachable but not speaking the agreed protocol.
        return parsed.isBatchFailed() || parsed.getTranslations().size() != requestedIds.size()
                ? Outcome.PROTOCOL : Outcome.OK;
    }

    static Outcome statusOutcome(int status) {
        if (status == 401 || status == 403) {
            return Outcome.AUTH_FAILED;
        }

        if (status == 402) {
            return Outcome.NO_BALANCE;
        }

        if (status == 429) {
            return Outcome.RATE_LIMITED;
        }

        if (status >= 500 && status <= 599) {
            return Outcome.SERVER_ERROR;
        }

        return Outcome.BAD_REQUEST;
    }

    private static void finish(Listener listener, Outcome outcome) {
        if (listener != null) {
            listener.onFinished(outcome);
        }
    }
}
