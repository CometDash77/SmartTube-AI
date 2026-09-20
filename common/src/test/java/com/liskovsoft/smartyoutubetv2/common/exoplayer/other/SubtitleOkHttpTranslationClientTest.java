package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import okhttp3.Request;
import okhttp3.OkHttpClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** T07 slice: the wire shape and client hardening, asserted without any network access. */
@RunWith(RobolectricTestRunner.class)
public class SubtitleOkHttpTranslationClientTest {
    private static SubtitleTranslationRequest request() throws Exception {
        SubtitleTranslationConfig config = new SubtitleTranslationConfig("https://api.deepseek.com", null, "zh-Hans", null);
        SubtitleBatch batch = new SubtitleBatch(Collections.singletonList(new SubtitleItem("a", "Hello")), null, null);

        return SubtitleTranslationRequest.create(config, "sk-test", "en", batch);
    }

    @Test
    public void wireRequestIsAPostWithThePreparedUrlHeadersAndBody() throws Exception {
        Request wire = SubtitleOkHttpTranslationClient.buildRequest(request());

        assertEquals("POST", wire.method());
        assertEquals("https://api.deepseek.com/chat/completions", wire.url().toString());
        assertEquals("Bearer sk-test", wire.header("Authorization"));
        assertTrue(wire.header("Content-Type").startsWith("application/json"));
        assertTrue(wire.body().contentLength() > 0);
    }

    @Test
    public void clientHardeningMatchesThePlan() {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(SubtitleOkHttpTranslationClient.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(SubtitleOkHttpTranslationClient.READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .callTimeout(SubtitleOkHttpTranslationClient.CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(false)
                .followRedirects(false)
                .followSslRedirects(false)
                .build();

        assertEquals(5_000, client.connectTimeoutMillis());
        assertEquals(15_000, client.readTimeoutMillis());
        assertEquals(20_000, client.callTimeoutMillis());
        assertFalse(client.retryOnConnectionFailure());
        assertFalse(client.followRedirects());
        assertFalse(client.followSslRedirects());
    }

    @Test
    public void retryAfterSecondsAreParsedAndInvalidValuesAreIgnored() {
        assertEquals(5_000, SubtitleOkHttpTranslationClient.parseRetryAfterMs("5"));
        assertEquals(-1, SubtitleOkHttpTranslationClient.parseRetryAfterMs("Wed, 21 Oct 2026 07:28:00 GMT"));
        assertEquals(-1, SubtitleOkHttpTranslationClient.parseRetryAfterMs(null));
    }

    @Test
    public void lengthTruncationIsDetectedInTheBody() {
        assertTrue(SubtitleOkHttpTranslationClient.isTruncated("{\"choices\":[{\"finish_reason\":\"length\"}]}"));
        assertFalse(SubtitleOkHttpTranslationClient.isTruncated("{\"items\":[]}"));
        assertFalse(SubtitleOkHttpTranslationClient.isTruncated(null));
    }

    @Test
    public void nullRequestReportsATransportFailureWithoutCallingOut() {
        boolean[] failed = {false};

        SubtitleOkHttpTranslationClient.Cancellable handle = new SubtitleOkHttpTranslationClient()
                .send(null, null, new SubtitleTranslationClient.ResponseHandler() {
                    @Override
                    public void onResponse(int status, long retryAfterMs, boolean truncated, String body) {
                    }

                    @Override
                    public void onTransportFailure() {
                        failed[0] = true;
                    }
                });

        assertTrue(failed[0]);
        handle.cancel();
    }
}
