package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** T06/T07 boundary acceptance for the retry rules of section 6.3. */
public class SubtitleRetryPolicyTest {
    private static SubtitleRetryPolicy.Decision decide(int status) {
        return SubtitleRetryPolicy.decide(status, -1, 0);
    }

    @Test
    public void authenticationAndBillingStopTheSession() {
        assertEquals(SubtitleRetryPolicy.Action.STOP_SESSION, decide(401).getAction());
        assertEquals(SubtitleRetryPolicy.Action.STOP_SESSION, decide(403).getAction());
        assertEquals(SubtitleRetryPolicy.Action.STOP_SESSION, decide(402).getAction());
    }

    @Test
    public void badRequestAndUnknownEndpointDoNotRetryAutomatically() {
        assertEquals(SubtitleRetryPolicy.Action.FAIL_CONFIGURATION, decide(400).getAction());
        assertEquals(SubtitleRetryPolicy.Action.FAIL_CONFIGURATION, decide(404).getAction());
        assertEquals(SubtitleRetryPolicy.Action.FAIL_CONFIGURATION, decide(418).getAction());
    }

    @Test
    public void serverErrorsAndTransportFailuresAreRetriedOnce() {
        assertEquals(SubtitleRetryPolicy.Action.RETRY, decide(500).getAction());
        assertEquals(SubtitleRetryPolicy.Action.RETRY, decide(503).getAction());
        assertEquals(SubtitleRetryPolicy.Action.RETRY, decide(SubtitleRetryPolicy.STATUS_NETWORK_ERROR).getAction());
        assertEquals(0, decide(500).getDelayMs());
    }

    @Test
    public void rateLimitHonoursRetryAfter() {
        SubtitleRetryPolicy.Decision decision = SubtitleRetryPolicy.decide(429, 5_000, 250);

        assertEquals(SubtitleRetryPolicy.Action.RETRY, decision.getAction());
        assertEquals(5_000, decision.getDelayMs());
    }

    @Test
    public void rateLimitWithoutHeaderUsesTwoSecondsPlusJitter() {
        SubtitleRetryPolicy.Decision decision = SubtitleRetryPolicy.decide(429, -1, 250);

        assertEquals(SubtitleRetryPolicy.Action.RETRY, decision.getAction());
        assertEquals(SubtitleRetryPolicy.RETRY_AFTER_DEFAULT_MS + 250, decision.getDelayMs());
    }

    @Test
    public void veryLongRateLimitPausesInsteadOfRetrying() {
        SubtitleRetryPolicy.Decision decision = SubtitleRetryPolicy.decide(429, SubtitleRetryPolicy.RETRY_AFTER_PAUSE_MS + 1, 0);

        assertEquals(SubtitleRetryPolicy.Action.PAUSE, decision.getAction());
    }

    @Test
    public void exactlyThePauseBoundaryStillRetries() {
        assertEquals(SubtitleRetryPolicy.Action.RETRY,
                SubtitleRetryPolicy.decide(429, SubtitleRetryPolicy.RETRY_AFTER_PAUSE_MS, 0).getAction());
    }
}
