package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

/**
 * Maps a failed translation attempt onto the plan's retry rules (section 6.3).
 *
 * <p>Authentication and billing failures stop the session instead of being retried, a rate limit is
 * honoured through {@code Retry-After} (default two seconds plus a little jitter), and transport or
 * server errors may be tried once more. A very long rate-limit delay is reported as a pause so the
 * menu can show it instead of hammering the service.
 */
public final class SubtitleRetryPolicy {
    /** Network error, timeout or an unreadable response. */
    public static final int STATUS_NETWORK_ERROR = 0;
    public static final long RETRY_AFTER_DEFAULT_MS = 2_000;
    /** Longest rate-limit wait that is still handled silently; beyond it the session pauses. */
    public static final long RETRY_AFTER_PAUSE_MS = 60_000;

    public enum Action {
        /** Retry the same batch after {@link Decision#getDelayMs()}. */
        RETRY,
        /** Stop this configuration session and ask the user to fix the key or billing. */
        STOP_SESSION,
        /** Bad request, unknown endpoint or model: no automatic retry, tell the user. */
        FAIL_CONFIGURATION,
        /** Rate limited for too long: keep the original subtitles and show a paused state. */
        PAUSE
    }

    public static final class Decision {
        private final Action mAction;
        private final long mDelayMs;

        Decision(Action action, long delayMs) {
            mAction = action;
            mDelayMs = delayMs;
        }

        public Action getAction() {
            return mAction;
        }

        public long getDelayMs() {
            return mDelayMs;
        }

        @Override
        public String toString() {
            return "Decision{" + mAction + ", delay=" + mDelayMs + "}";
        }
    }

    private SubtitleRetryPolicy() {
    }

    /**
     * @param status       HTTP status, or {@link #STATUS_NETWORK_ERROR} for transport failures
     * @param retryAfterMs parsed {@code Retry-After} delay, or a negative value when absent
     * @param jitterMs     small positive random component added to the default delay
     */
    public static Decision decide(int status, long retryAfterMs, long jitterMs) {
        if (status == 401 || status == 403 || status == 402) {
            return new Decision(Action.STOP_SESSION, 0);
        }

        if (status == 400 || status == 404) {
            return new Decision(Action.FAIL_CONFIGURATION, 0);
        }

        if (status == 429) {
            long delayMs = retryAfterMs >= 0 ? retryAfterMs : RETRY_AFTER_DEFAULT_MS + Math.max(0, jitterMs);

            return delayMs > RETRY_AFTER_PAUSE_MS
                    ? new Decision(Action.PAUSE, delayMs)
                    : new Decision(Action.RETRY, delayMs);
        }

        if (status == STATUS_NETWORK_ERROR || (status >= 500 && status <= 599)) {
            return new Decision(Action.RETRY, 0);
        }

        // Unknown status: degrade conservatively without a retry storm.
        return new Decision(Action.FAIL_CONFIGURATION, 0);
    }
}
