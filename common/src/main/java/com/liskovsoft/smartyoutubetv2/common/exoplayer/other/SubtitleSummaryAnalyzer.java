package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The one-shot video context analysis of the {@code VIDEO_ENHANCED} tier (plan 4.2).
 *
 * <p>It reuses the configured endpoint, model, key and transport but has its own fixed instruction,
 * its own output and response bounds and its own 5 s timeout, after which the attempt is cancelled and
 * reported as {@link Status#TIMEOUT}. Exactly one terminal outcome is reported per attempt, there is no
 * automatic retry, and a failure simply leaves the session on the coherent context. The class reports
 * small codes only and never carries the sample text or the credential into its result.
 */
public class SubtitleSummaryAnalyzer {
    /** Plan 4.2 bounds: input, sample, metadata, timeout and output. */
    public static final int MAX_INPUT_CODE_POINTS = 6_700;
    public static final int MAX_SAMPLE_CODE_POINTS = 6_000;
    public static final int MIN_SAMPLE_CODE_POINTS = 200;
    public static final int MAX_TITLE_CODE_POINTS = 200;
    public static final int MAX_DESCRIPTION_CODE_POINTS = 500;
    public static final long TIMEOUT_MS = 5_000;

    /** One classified outcome of the analysis; the session only needs to know whether to use it. */
    public enum Status {
        SUCCESS,
        /** Fewer than 200 code points of usable sample: nothing was sent. */
        NOT_ENOUGH_SAMPLES,
        /** No key or an unusable endpoint: nothing was sent. */
        NOT_CONFIGURED,
        /** The 5 s budget elapsed; the call was cancelled. */
        TIMEOUT,
        /** The request never completed. */
        TRANSPORT_FAILED,
        /** The service answered with a non-200 status. */
        BAD_STATUS,
        /** The answer did not follow the fixed schema. */
        PROTOCOL,
        /** The caller abandoned the attempt before it settled. */
        CANCELLED
    }

    public interface Listener {
        void onFinished(Status status, SubtitleSummary summary);
    }

    /** Schedules the single timeout task; the caller owns the returned handle. */
    public interface TimeoutScheduler {
        SubtitleTranslationClient.Cancellable schedule(long delayMs, Runnable task);
    }

    private static final SubtitleTranslationClient.Cancellable NO_OP = () -> { };

    private final SubtitleTranslationClient mClient;
    private final TimeoutScheduler mScheduler;

    public SubtitleSummaryAnalyzer(SubtitleTranslationClient client, TimeoutScheduler scheduler) {
        mClient = client;
        mScheduler = scheduler;
    }

    /**
     * Starts one analysis attempt.
     *
     * @return a handle the caller may use to abandon the attempt (source or configuration change)
     */
    public SubtitleTranslationClient.Cancellable analyze(SubtitleTranslationConfig config, String apiKey, String sourceLanguage,
                               String title, String description, List<String> samples,
                               final Listener listener) {
        List<String> bounded = boundedSamples(samples);

        if (codePoints(bounded) < MIN_SAMPLE_CODE_POINTS) {
            finish(listener, Status.NOT_ENOUGH_SAMPLES, null); // too little evidence: never guess

            return NO_OP;
        }

        if (mClient == null) {
            finish(listener, Status.NOT_CONFIGURED, null);

            return NO_OP;
        }

        final String payload;

        try {
            payload = SubtitleRequestBuilder.buildSummaryPayload(sourceLanguage,
                    cap(title, MAX_TITLE_CODE_POINTS), cap(description, MAX_DESCRIPTION_CODE_POINTS),
                    bounded);
        } catch (JSONException e) {
            finish(listener, Status.PROTOCOL, null);

            return NO_OP;
        }

        final SubtitleTranslationRequest request;

        try {
            request = SubtitleTranslationRequest.createSummary(config, apiKey, payload);
        } catch (JSONException e) {
            finish(listener, Status.PROTOCOL, null);

            return NO_OP;
        }

        if (request == null) {
            finish(listener, Status.NOT_CONFIGURED, null);

            return NO_OP;
        }

        final AtomicBoolean settled = new AtomicBoolean();
        final SubtitleTranslationClient.Cancellable[] call = new SubtitleTranslationClient.Cancellable[1];
        final SubtitleTranslationClient.Cancellable timeout = mScheduler != null ? mScheduler.schedule(TIMEOUT_MS, () -> {
            if (settled.compareAndSet(false, true)) {
                if (call[0] != null) {
                    call[0].cancel(); // the 5 s budget elapsed: the attempt is abandoned, not retried
                }

                finish(listener, Status.TIMEOUT, null);
            }
        }) : null;

        if (settled.get()) {
            return NO_OP; // the timeout budget already elapsed before anything left the device
        }

        SubtitleTranslationClient.Cancellable sent = mClient.send(request, request.getSystemInstruction(config, null),
                new SubtitleTranslationClient.ResponseHandler() {
                    @Override
                    public void onResponse(int status, long retryAfterMs, boolean truncated, String body) {
                        if (!settled.compareAndSet(false, true)) {
                            return; // the timeout already reported this attempt
                        }

                        if (timeout != null) {
                            timeout.cancel();
                        }

                        if (status != SubtitleResponseHandler.STATUS_OK) {
                            finish(listener, Status.BAD_STATUS, null);
                            return;
                        }

                        SubtitleSummaryParser.Result parsed = SubtitleSummaryParser.parse(body, truncated);

                        if (parsed.isFailed()) {
                            finish(listener, Status.PROTOCOL, null);
                        } else {
                            finish(listener, Status.SUCCESS, parsed.getSummary());
                        }
                    }

                    @Override
                    public void onTransportFailure() {
                        if (!settled.compareAndSet(false, true)) {
                            return;
                        }

                        if (timeout != null) {
                            timeout.cancel();
                        }

                        finish(listener, Status.TRANSPORT_FAILED, null);
                    }
                });

        call[0] = sent;

        if (settled.get() && sent != null) {
            sent.cancel(); // a timeout that fired before the call was registered
        }

        return () -> {
            if (settled.compareAndSet(false, true)) {
                if (sent != null) {
                    sent.cancel();
                }

                if (timeout != null) {
                    timeout.cancel();
                }

                finish(listener, Status.CANCELLED, null);
            }
        };
    }

    private static void finish(Listener listener, Status status, SubtitleSummary summary) {
        if (listener != null) {
            listener.onFinished(status, summary);
        }
    }

    /**
     * The sample of plan 4.2: at most 6,000 code points from the beginning of the source, and never
     * more than the whole 6,700 code-point input budget allows.
     */
    static List<String> boundedSamples(List<String> samples) {
        List<String> bounded = new ArrayList<>();
        int codePoints = 0;

        if (samples != null) {
            for (String sample : samples) {
                if (sample == null || sample.trim().isEmpty()) {
                    continue;
                }

                String text = sample.trim();
                int textCodePoints = SubtitleBatch.codePoints(text);

                if (codePoints + textCodePoints > MAX_SAMPLE_CODE_POINTS
                        || codePoints + textCodePoints > MAX_INPUT_CODE_POINTS) {
                    continue;
                }

                bounded.add(text);
                codePoints += textCodePoints;
            }
        }

        return bounded;
    }

    private static int codePoints(List<String> texts) {
        int codePoints = 0;

        for (String text : texts) {
            codePoints += SubtitleBatch.codePoints(text);
        }

        return codePoints;
    }

    private static String cap(String text, int maxCodePoints) {
        if (text == null) {
            return null;
        }

        String trimmed = text.trim();

        if (trimmed.isEmpty()) {
            return null;
        }

        return trimmed.codePointCount(0, trimmed.length()) <= maxCodePoints ? trimmed
                : trimmed.substring(0, trimmed.offsetByCodePoints(0, maxCodePoints));
    }
}
