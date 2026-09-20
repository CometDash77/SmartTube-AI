package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** T07 slice: one HTTP attempt mapped onto the next scheduler action. */
@RunWith(RobolectricTestRunner.class)
public class SubtitleResponseHandlerTest {
    private static final List<String> IDS = Arrays.asList("a", "b");

    @Test
    public void successfulAnswerDeliversTranslationsWithNoFollowUp() {
        SubtitleResponseHandler.Outcome outcome = SubtitleResponseHandler.handle(200, -1, 0, false,
                "{\"items\":[{\"id\":\"a\",\"translation\":\"\u4e00\"}]}", IDS);

        assertTrue(outcome.isDelivered());
        assertNull(outcome.getAction());
        assertEquals("\u4e00", outcome.getTranslations().get("a"));
    }

    @Test
    public void partlyValidAnswerKeepsOnlyItsValidEntries() {
        SubtitleResponseHandler.Outcome outcome = SubtitleResponseHandler.handle(200, -1, 0, false,
                "{\"items\":[{\"id\":\"a\",\"translation\":\"\u4e00\"},{\"id\":\"b\",\"translation\":\"  \"}]}", IDS);

        assertTrue(outcome.isDelivered());
        assertEquals(1, outcome.getTranslations().size());
        assertFalse(outcome.getTranslations().containsKey("b"));
    }

    @Test
    public void damagedBatchIsNotRetriedAutomatically() {
        SubtitleResponseHandler.Outcome outcome = SubtitleResponseHandler.handle(200, -1, 0, false, "{\"items\":[", IDS);

        assertFalse(outcome.isDelivered());
        assertEquals(SubtitleRetryPolicy.Action.FAIL_CONFIGURATION, outcome.getAction());
        assertEquals(SubtitleResponseParser.FAILURE_MALFORMED, outcome.getFailureCode());
        assertEquals(0, outcome.getTranslations().size());
    }

    @Test
    public void truncatedBatchIsRefusedAsAWhole() {
        SubtitleResponseHandler.Outcome outcome = SubtitleResponseHandler.handle(200, -1, 0, true,
                "{\"items\":[{\"id\":\"a\",\"translation\":\"\u4e00\"}]}", IDS);

        assertEquals(SubtitleResponseParser.FAILURE_TRUNCATED, outcome.getFailureCode());
        assertEquals(SubtitleRetryPolicy.Action.FAIL_CONFIGURATION, outcome.getAction());
    }

    @Test
    public void authenticationFailuresStopTheSession() {
        assertEquals(SubtitleRetryPolicy.Action.STOP_SESSION, SubtitleResponseHandler.handle(401, -1, 0, false, "", IDS).getAction());
        assertEquals(SubtitleRetryPolicy.Action.STOP_SESSION, SubtitleResponseHandler.handle(402, -1, 0, false, "", IDS).getAction());
    }

    @Test
    public void rateLimitAndServerErrorsKeepTheirOwnPolicy() {
        SubtitleResponseHandler.Outcome limited = SubtitleResponseHandler.handle(429, 5_000, 0, false, "", IDS);
        assertEquals(SubtitleRetryPolicy.Action.RETRY, limited.getAction());
        assertEquals(5_000, limited.getDelayMs());

        assertEquals(SubtitleRetryPolicy.Action.RETRY, SubtitleResponseHandler.handle(503, -1, 0, false, "", IDS).getAction());
    }
}
