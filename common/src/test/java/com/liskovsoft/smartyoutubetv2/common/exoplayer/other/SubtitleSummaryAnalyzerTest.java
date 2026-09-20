package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Plan 4.2 acceptance for the one-shot context analysis: bounded input, one call per attempt, one
 * terminal outcome, the 5 s budget and the failure fallback.
 */
@RunWith(RobolectricTestRunner.class)
public class SubtitleSummaryAnalyzerTest {
    private SubtitleTranslationConfig mConfig;
    private FakeClient mClient;
    private FakeScheduler mScheduler;
    private SubtitleSummaryAnalyzer mAnalyzer;
    private final List<String> mStatuses = new ArrayList<>();
    private final List<SubtitleSummary> mSummaries = new ArrayList<>();

    private static class FakeClient implements SubtitleTranslationClient {
        private final List<SubtitleTranslationRequest> requests = new ArrayList<>();
        private final List<String> instructions = new ArrayList<>();
        private final List<ResponseHandler> handlers = new ArrayList<>();
        private final List<Boolean> cancelled = new ArrayList<>();
        private boolean answerImmediately = true;

        @Override
        public Cancellable send(SubtitleTranslationRequest request, String systemInstruction, ResponseHandler handler) {
            requests.add(request);
            instructions.add(systemInstruction);
            handlers.add(handler);
            final int index = cancelled.size();
            cancelled.add(false);

            if (answerImmediately) {
                handler.onResponse(200, -1, false, validBody());
            }

            return () -> cancelled.set(index, true);
        }

        void answer(int index, int status, String body) {
            handlers.get(index).onResponse(status, -1, false, body);
        }

        void fail(int index) {
            handlers.get(index).onTransportFailure();
        }
    }

    private static class FakeScheduler implements SubtitleSummaryAnalyzer.TimeoutScheduler {
        private Runnable mTask;
        private boolean mCancelled;

        @Override
        public SubtitleTranslationClient.Cancellable schedule(long delayMs, Runnable task) {
            mTask = task;

            return () -> mCancelled = true;
        }

        void fire() {
            Runnable task = mTask;
            mTask = null;

            if (task != null) {
                task.run();
            }
        }

        boolean isCancelled() {
            return mCancelled;
        }
    }

    private static String validBody() {
        return "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":"
                + JSONObject.quote("{\"topic\":\"Greetings\",\"terms\":[{\"source\":\"hi\",\"translation\":\"hallo\"}]}")
                + "}}]}";
    }

    /** 200 code points of usable sample: the plan's minimum. */
    private static List<String> enoughSamples() {
        StringBuilder line = new StringBuilder();

        for (int i = 0; i < 50; i++) {
            line.append("sample ");
        }

        return Arrays.asList(line.toString(), line.toString(), line.toString(), line.toString());
    }

    @org.junit.Before
    public void setUp() {
        mConfig = new SubtitleTranslationConfig(SubtitleEndpoint.DEFAULT_BASE_URL,
                SubtitleTranslationConfig.DEFAULT_MODEL, "de", null,
                SubtitleAiSettings.CONTEXT_VIDEO_ENHANCED, 0);
        mClient = new FakeClient();
        mScheduler = new FakeScheduler();
        mAnalyzer = new SubtitleSummaryAnalyzer(mClient, mScheduler);
        mStatuses.clear();
        mSummaries.clear();
    }

    private SubtitleTranslationClient.Cancellable analyze(List<String> samples) {
        return mAnalyzer.analyze(mConfig, "sk-test-placeholder", "en", "Title", "Description", samples,
                (status, summary) -> {
                    mStatuses.add(status.name());
                    mSummaries.add(summary);
                });
    }

    @Test
    public void oneCallCarriesTheBoundedInputAndTheSummaryProtocol() throws Exception {
        analyze(enoughSamples());

        assertEquals("exactly one call per attempt", 1, mClient.requests.size());
        assertEquals(Collections.singletonList("SUCCESS"), mStatuses);
        assertTrue(mClient.instructions.get(0).contains("You analyse the topic of a video"));

        JSONObject request = new JSONObject(mClient.requests.get(0).getBody());
        assertEquals("the analysis shares the configured model",
                SubtitleTranslationConfig.DEFAULT_MODEL, request.getString("model"));
        assertEquals(SubtitleRequestBuilder.MAX_SUMMARY_OUTPUT_TOKENS, request.getInt("max_tokens"));
        assertFalse("the summary request is not a streaming one", request.getBoolean("stream"));
        String payload = request.getJSONArray("messages").getJSONObject(1).getString("content");
        JSONObject user = new JSONObject(payload);
        assertEquals("Title", user.getString("title"));
        assertEquals("Description", user.getString("description"));
        assertTrue("the sample must carry the original lines", user.getJSONArray("samples").length() > 0);
    }

    @Test
    public void tooLittleSampleNeverLeavesTheDevice() {
        analyze(Arrays.asList("short", ""));

        assertEquals(Collections.singletonList("NOT_ENOUGH_SAMPLES"), mStatuses);
        assertEquals(0, mClient.requests.size());
    }

    @Test
    public void theInputStaysInsideThePlanBudget() throws Exception {
        StringBuilder big = new StringBuilder();

        for (int i = 0; i < SubtitleSummaryAnalyzer.MAX_SAMPLE_CODE_POINTS + 5_000; i++) {
            big.append('x');
        }

        analyze(Arrays.asList(big.toString()));

        assertEquals(Collections.singletonList("NOT_ENOUGH_SAMPLES"), mStatuses);
        assertEquals("an over-long line alone is not a usable sample", 0, mClient.requests.size());
    }

    @Test
    public void aTimeoutCancelsTheCallAndReportsOnce() {
        mClient.answerImmediately = false;
        analyze(enoughSamples());
        mScheduler.fire();

        assertEquals(Collections.singletonList("TIMEOUT"), mStatuses);

        // A late answer of the abandoned attempt must not report again or replace the fallback.
        mClient.answer(0, 200, validBody());

        assertEquals(Collections.singletonList("TIMEOUT"), mStatuses);
    }

    @Test
    public void aTransportFailureIsReportedOnceAndNotRetried() {
        mClient.answerImmediately = false;
        analyze(enoughSamples());
        mClient.fail(0);
        mClient.fail(0);

        assertEquals(Collections.singletonList("TRANSPORT_FAILED"), mStatuses);
        assertEquals("a failure is never retried automatically", 1, mClient.requests.size());
    }

    @Test
    public void aNon200AnswerIsAClassifiedFailureWithoutRetry() {
        mClient.answerImmediately = false;
        analyze(enoughSamples());
        mClient.answer(0, 429, "");

        assertEquals(Collections.singletonList("BAD_STATUS"), mStatuses);
        assertEquals(1, mClient.requests.size());
    }

    @Test
    public void aDamagedAnswerFallsBackInsteadOfHalfEnteringTheContext() {
        mClient.answerImmediately = false;
        analyze(enoughSamples());
        mClient.answer(0, 200, "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{}\"}}]}");

        assertEquals(Collections.singletonList("PROTOCOL"), mStatuses);
        assertNull(mSummaries.get(0));
    }

    @Test
    public void abandoningAnAttemptReportsCancelledOnce() {
        mClient.answerImmediately = false;
        SubtitleTranslationClient.Cancellable handle = analyze(enoughSamples());
        handle.cancel();

        assertEquals(Collections.singletonList("CANCELLED"), mStatuses);

        mClient.answer(0, 200, validBody());

        assertEquals(Collections.singletonList("CANCELLED"), mStatuses);
    }

    @Test
    public void aSuccessfulAttemptCarriesTheBoundedSummary() {
        analyze(enoughSamples());

        assertFalse(mSummaries.get(0).isEmpty());
        assertEquals("Greetings", mSummaries.get(0).getTopic());
        assertEquals(1, mSummaries.get(0).getTerms().size());
    }
}
