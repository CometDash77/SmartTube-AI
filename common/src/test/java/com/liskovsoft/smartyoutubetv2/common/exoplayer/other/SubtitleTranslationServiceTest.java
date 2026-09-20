package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** T07 slice: the transport seam mapped onto the dispatcher contract. */
@RunWith(RobolectricTestRunner.class)
public class SubtitleTranslationServiceTest {
    private boolean mCancelled;
    private SubtitleTranslationClient.ResponseHandler mHandler;
    private SubtitleTranslationRequest mSentRequest;

    private class RecordingCallback implements SubtitleTranslationDispatcher.Callback {
        List<String> translations;
        boolean failed;
        int failures;

        @Override
        public void onSuccess(SubtitleBatch batch, List<String> values) {
            translations = values;
        }

        @Override
        public void onFailure(SubtitleBatch batch) {
            failed = true;
            failures++;
        }
    }

    private SubtitleTranslationService service(String key) {
        SubtitleTranslationClient client = (request, instruction, handler) -> {
            mSentRequest = request;
            mHandler = handler;

            return () -> mCancelled = true;
        };

        return new SubtitleTranslationService(client,
                () -> new SubtitleTranslationConfig("https://api.deepseek.com", null, "zh-Hans", null),
                () -> key, "en", null);
    }

    private static SubtitleBatch batch() {
        return new SubtitleBatch(Arrays.asList(new SubtitleItem("a", "One"), new SubtitleItem("b", "Two")), null, null);
    }

    @Test
    public void deliveredAnswerIsAlignedToTheBatchSlots() {
        RecordingCallback callback = new RecordingCallback();
        service("sk-test").translate(batch(), callback);

        mHandler.onResponse(200, -1, false, "{\"items\":[{\"id\":\"b\",\"translation\":\"\u4e8c\"}]}");

        assertEquals(Arrays.asList(null, "\u4e8c"), callback.translations);
        assertFalse(callback.failed);
    }

    @Test
    public void damagedAnswerBecomesAFailureForTheDispatcher() {
        RecordingCallback callback = new RecordingCallback();
        service("sk-test").translate(batch(), callback);

        mHandler.onResponse(200, -1, false, "not json");

        assertTrue(callback.failed);
        assertEquals(null, callback.translations);
    }

    @Test
    public void transportFailureBecomesAFailure() {
        RecordingCallback callback = new RecordingCallback();
        service("sk-test").translate(batch(), callback);

        mHandler.onTransportFailure();

        assertTrue(callback.failed);
    }

    @Test
    public void stopSessionStatusesAlsoBecomeAFailureSoTheControllerDecides() {
        RecordingCallback callback = new RecordingCallback();
        service("sk-test").translate(batch(), callback);

        mHandler.onResponse(401, -1, false, "");

        assertTrue(callback.failed);
    }

    @Test
    public void missingKeyNeverCallsOut() {
        RecordingCallback callback = new RecordingCallback();

        SubtitleTranslationDispatcher.TranslationCall call = service(null).translate(batch(), callback);

        assertTrue(callback.failed);
        assertEquals(1, callback.failures);
        assertEquals(null, mSentRequest);
        call.cancel(); // the returned handle is inert but safe
    }

    @Test
    public void retryDelayOfAFailedAttemptIsExposedForTheDispatcher() {
        RecordingCallback callback = new RecordingCallback();
        SubtitleTranslationService service = service("sk-test");
        service.translate(batch(), callback);

        mHandler.onResponse(429, 5_000, false, "");

        assertTrue(callback.failed);
        assertEquals(5_000, service.getLastRetryDelayMs());
    }

    @Test
    public void successfulAttemptClearsTheRetryDelay() {
        RecordingCallback callback = new RecordingCallback();
        SubtitleTranslationService service = service("sk-test");
        service.translate(batch(), callback);
        mHandler.onResponse(429, 5_000, false, "");

        service.translate(batch(), callback);
        mHandler.onResponse(200, -1, false, "{\"items\":[]}");

        assertEquals(0, service.getLastRetryDelayMs());
    }

    @Test
    public void theSourceLanguageOfTheSelectedTrackReachesTheRequest() {
        RecordingCallback callback = new RecordingCallback();
        SubtitleTranslationService provider = new SubtitleTranslationService(
                (request, instruction, handler) -> {
                    mSentRequest = request;
                    mHandler = handler;

                    return () -> mCancelled = true;
                },
                () -> new SubtitleTranslationConfig("https://api.deepseek.com", null, "zh-Hans", null),
                () -> "sk-test", () -> "de", null);

        provider.translate(batch(), callback);

        try {
            org.json.JSONObject envelope = new org.json.JSONObject(mSentRequest.getBody());
            org.json.JSONObject payload = new org.json.JSONObject(
                    envelope.getJSONArray("messages").getJSONObject(1).getString("content"));
            assertEquals("de", payload.getString("sourceLanguage"));
        } catch (org.json.JSONException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void cancellationIsForwardedToTheTransport() {
        SubtitleTranslationDispatcher.TranslationCall call = service("sk-test").translate(batch(), new RecordingCallback());

        call.cancel();

        assertTrue(mCancelled);
    }

    @Test
    public void anAuthenticationFailureStopsTheSessionUntilAnAnswerSucceeds() {
        RecordingCallback callback = new RecordingCallback();
        SubtitleTranslationService service = service("sk-test");
        service.translate(batch(), callback);

        mHandler.onResponse(401, -1, false, "");

        assertTrue("401/403/402 must stop the session, not be retried", service.isAuthorizationStopped());

        service.translate(batch(), callback);
        mHandler.onResponse(200, -1, false, "{\"items\":[]}");

        assertFalse("a delivered answer clears the stop", service.isAuthorizationStopped());
    }

    @Test
    public void anUnrelatedFailureDoesNotStopTheSession() {
        RecordingCallback callback = new RecordingCallback();
        SubtitleTranslationService service = service("sk-test");
        service.translate(batch(), callback);

        mHandler.onResponse(400, -1, false, "");

        assertFalse(service.isAuthorizationStopped());
    }

    @Test
    public void requestCarriesTheSystemInstructionOfTheConfiguration() {
        service("sk-test").translate(batch(), new RecordingCallback());

        assertTrue(mSentRequest.getSystemInstruction(new SubtitleTranslationConfig(null, null, "zh-Hans", null), "s")
                .contains("Target language: zh-Hans."));

        List<String> ids = new ArrayList<>(mSentRequest.getBody().length() > 0 ? Collections.singletonList("ok") : Collections.<String>emptyList());
        assertEquals(Collections.singletonList("ok"), ids);
    }
}
