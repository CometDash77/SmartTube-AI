package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * N5 regression: the manual connection test of the AI settings menu.
 *
 * <p>It must classify the service answer without ever sending the subtitles being watched, and it
 * must refuse to call out at all when no key or an unusable address is configured.
 */
@RunWith(RobolectricTestRunner.class)
public class SubtitleConnectionTestTest {
    private SubtitleTranslationClient.ResponseHandler mHandler;
    private SubtitleTranslationRequest mSentRequest;
    private boolean mCancelled;
    private final List<SubtitleConnectionTest.Outcome> mOutcomes = new ArrayList<>();

    private SubtitleConnectionTest tester() {
        SubtitleTranslationClient client = (request, instruction, handler) -> {
            mSentRequest = request;
            mHandler = handler;

            return () -> mCancelled = true;
        };

        return new SubtitleConnectionTest(client);
    }

    private static SubtitleTranslationConfig config() {
        return new SubtitleTranslationConfig("https://api.deepseek.com", null, "zh-Hans", null);
    }

    private static String validAnswer() {
        return "{\"items\":[{\"id\":\"connection-test-0\",\"translation\":\"Good morning.\"},"
                + "{\"id\":\"connection-test-1\",\"translation\":\"See you tomorrow.\"}]}";
    }

    private SubtitleConnectionTest.Outcome last() {
        return mOutcomes.get(mOutcomes.size() - 1);
    }

    private void setUpTest(SubtitleTranslationConfig config, String key) {
        mHandler = null;
        mSentRequest = null;
        tester().test(config, key, null, outcome -> mOutcomes.add(outcome));
    }

    @Test
    public void aValidAnswerIsReportedAsOk() {
        setUpTest(config(), "sk-test");

        assertNotNull("a configured test must call out", mSentRequest);
        mHandler.onResponse(200, -1, false, validAnswer());

        assertEquals(SubtitleConnectionTest.Outcome.OK, last());
    }

    @Test
    public void officialWrappedResponseUsesConfiguredModelAndReportsHttpStatus() throws Exception {
        List<Integer> statuses = new ArrayList<>();
        SubtitleTranslationConfig config = new SubtitleTranslationConfig(
                "https://api.deepseek.com/v1", "deepseek-v4-pro", "zh-Hans", null);
        tester().test(config, "local-placeholder", null, new SubtitleConnectionTest.Listener() {
            @Override public void onFinished(SubtitleConnectionTest.Outcome outcome) { mOutcomes.add(outcome); }
            @Override public void onHttpFinished(SubtitleConnectionTest.Outcome outcome, int status) {
                statuses.add(status);
                onFinished(outcome);
            }
        });
        assertEquals("https://api.deepseek.com/v1/chat/completions", mSentRequest.getUrl());
        assertEquals("deepseek-v4-pro", new org.json.JSONObject(mSentRequest.getBody()).getString("model"));
        mHandler.onResponse(200, -1, false, SubtitleResponseParserTest.completion(validAnswer(), "stop"));
        assertEquals(SubtitleConnectionTest.Outcome.OK, last());
        assertEquals(Integer.valueOf(200), statuses.get(0));
    }

    @Test
    public void platformWebsiteIsRejectedWithoutSendingTheKey() {
        setUpTest(new SubtitleTranslationConfig("https://platform.deepseek.com", null, "zh-Hans", null), "local-placeholder");
        assertNull(mSentRequest);
        assertEquals(SubtitleConnectionTest.Outcome.NOT_CONFIGURED, last());
    }

    @Test
    public void noTranslatedItemsCannotPassConnectionTest() {
        setUpTest(config(), "local-placeholder");
        mHandler.onResponse(200, -1, false, "{\"items\":[]}");
        assertEquals(SubtitleConnectionTest.Outcome.PROTOCOL, last());
    }

    @Test
    public void aMissingKeyNeverCallsOut() {
        setUpTest(config(), null);

        assertNull(mSentRequest);
        assertEquals(SubtitleConnectionTest.Outcome.NOT_CONFIGURED, last());
    }

    @Test
    public void anUnusableAddressIsRefusedBeforeAnyCall() {
        setUpTest(new SubtitleTranslationConfig("http://api.deepseek.com", null, "zh-Hans", null), "sk-test");

        assertNull("plain HTTP must never carry a key", mSentRequest);
        assertEquals(SubtitleConnectionTest.Outcome.NOT_CONFIGURED, last());
    }

    @Test
    public void everyFailureStatusHasItsOwnActionableOutcome() {
        setUpTest(config(), "sk-test");
        mHandler.onResponse(401, -1, false, "");
        assertEquals(SubtitleConnectionTest.Outcome.AUTH_FAILED, last());

        setUpTest(config(), "sk-test");
        mHandler.onResponse(403, -1, false, "");
        assertEquals(SubtitleConnectionTest.Outcome.AUTH_FAILED, last());

        setUpTest(config(), "sk-test");
        mHandler.onResponse(402, -1, false, "");
        assertEquals(SubtitleConnectionTest.Outcome.NO_BALANCE, last());

        setUpTest(config(), "sk-test");
        mHandler.onResponse(429, 5_000, false, "");
        assertEquals(SubtitleConnectionTest.Outcome.RATE_LIMITED, last());

        setUpTest(config(), "sk-test");
        mHandler.onResponse(503, -1, false, "");
        assertEquals(SubtitleConnectionTest.Outcome.SERVER_ERROR, last());

        setUpTest(config(), "sk-test");
        mHandler.onResponse(404, -1, false, "");
        assertEquals(SubtitleConnectionTest.Outcome.BAD_REQUEST, last());
    }

    @Test
    public void aTransportFailureIsReportedAsNetwork() {
        setUpTest(config(), "sk-test");

        mHandler.onTransportFailure();

        assertEquals(SubtitleConnectionTest.Outcome.NETWORK, last());
    }

    @Test
    public void aDamagedAnswerIsReportedAsProtocol() {
        setUpTest(config(), "sk-test");

        mHandler.onResponse(200, -1, false, "not json");

        assertEquals(SubtitleConnectionTest.Outcome.PROTOCOL, last());
    }

    @Test
    public void theSyntheticBatchNeverCarriesTheWatchedSubtitles() {
        setUpTest(config(), "sk-test");

        String body = mSentRequest.getBody();

        assertTrue(body.contains("connection-test-0"));
        assertTrue(body.contains("Good morning."));
        assertFalse("no real subtitle text may be sent", body.contains("-->"));
        assertEquals(2, SubtitleConnectionTest.sampleBatch().getItems().size());
    }

    @Test
    public void theAttemptIsCancellable() {
        SubtitleTranslationClient.Cancellable call = tester().test(config(), "sk-test", null,
                outcome -> mOutcomes.add(outcome));

        call.cancel();

        assertTrue(mCancelled);
    }
}
