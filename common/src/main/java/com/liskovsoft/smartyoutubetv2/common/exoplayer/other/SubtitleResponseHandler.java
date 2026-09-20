package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Turns one finished HTTP attempt into what the scheduler must do next.
 *
 * <p>A 200 answer is validated by {@link SubtitleResponseParser}: when it is usable the valid
 * translations are returned with no follow-up action, and when the batch is damaged (malformed,
 * truncated, empty or without items) it is refused as a whole with a non-retryable configuration
 * failure, because the plan forbids automatic semantic repair of a half answer. Non-200 statuses are
 * classified by {@link SubtitleRetryPolicy}, so authentication, rate limiting and server errors keep
 * their own policies.
 */
public final class SubtitleResponseHandler {
    public static final int STATUS_OK = 200;

    public static final class Outcome {
        private final Map<String, String> mTranslations;
        private final SubtitleRetryPolicy.Action mAction;
        private final long mDelayMs;
        private final String mFailureCode;

        Outcome(Map<String, String> translations, SubtitleRetryPolicy.Action action, long delayMs, String failureCode) {
            mTranslations = Collections.unmodifiableMap(translations);
            mAction = action;
            mDelayMs = delayMs;
            mFailureCode = failureCode;
        }

        public Map<String, String> getTranslations() {
            return mTranslations;
        }

        /** Follow-up action, or null when the batch succeeded. */
        public SubtitleRetryPolicy.Action getAction() {
            return mAction;
        }

        public long getDelayMs() {
            return mDelayMs;
        }

        /** Small failure code from the parser, or null. */
        public String getFailureCode() {
            return mFailureCode;
        }

        public boolean isDelivered() {
            return mAction == null;
        }
    }

    private SubtitleResponseHandler() {
    }

    public static Outcome handle(int status, long retryAfterMs, long jitterMs, boolean truncated, String body,
                                 Collection<String> requestedIds) {
        if (status != STATUS_OK) {
            SubtitleRetryPolicy.Decision decision = SubtitleRetryPolicy.decide(status, retryAfterMs, jitterMs);

            return new Outcome(Collections.<String, String>emptyMap(), decision.getAction(), decision.getDelayMs(), null);
        }

        SubtitleResponseParser.Result parsed = SubtitleResponseParser.parse(body, truncated, requestedIds);

        if (parsed.isBatchFailed()) {
            // Damaged batch: keep the originals, do not retry automatically, allow a manual retry.
            return new Outcome(Collections.<String, String>emptyMap(),
                    SubtitleRetryPolicy.Action.FAIL_CONFIGURATION, 0, parsed.getFailure());
        }

        return new Outcome(parsed.getTranslations(), null, 0, null);
    }
}
