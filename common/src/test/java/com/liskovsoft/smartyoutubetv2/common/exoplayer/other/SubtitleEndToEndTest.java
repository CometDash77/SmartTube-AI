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
import static org.junit.Assert.assertTrue;

/**
 * T06+T07 integration: planner, dispatcher, cache and the prepared request/response chain work
 * together without any network or player, driven by a fake transport and a fake clock.
 *
 * <p>Round 33 recorded two failures in a draft of this test; both were test defects and are fixed
 * here: the in-flight window legitimately yields its next non-pending items (only the dispatcher's
 * single-call gate stops a second call), and the item id is read with the JSON parser instead of a
 * hand-counted string offset.
 */
@RunWith(RobolectricTestRunner.class)
public class SubtitleEndToEndTest {
    private static class FakeClock implements SubtitleTranslationDispatcher.Clock {
        private long nowMs = 10_000;

        @Override
        public long elapsedRealtimeMs() {
            return nowMs;
        }

        void advance(long deltaMs) {
            nowMs += deltaMs;
        }
    }

    private static class FakeTransport implements SubtitleTranslationClient {
        private final List<SubtitleTranslationRequest> requests = new ArrayList<>();
        private final List<ResponseHandler> handlers = new ArrayList<>();
        private boolean busy;

        @Override
        public Cancellable send(SubtitleTranslationRequest request, String systemInstruction, ResponseHandler handler) {
            if (busy) {
                throw new AssertionError("a second call must never overlap the first");
            }

            busy = true;
            requests.add(request);
            handlers.add(handler);

            return () -> busy = false;
        }

        void succeed(int index, String jsonBody) {
            busy = false;
            handlers.get(index).onResponse(200, -1, false, jsonBody);
        }

        void fail(int index, int status) {
            busy = false;
            handlers.get(index).onResponse(status, -1, false, "");
        }
    }

    private static SubtitleTimeline timeline() {
        List<SubtitleEvent> events = new ArrayList<>();

        for (int i = 0; i < 12; i++) {
            events.add(new SubtitleEvent(i * 10_000_000L,
                    Collections.singletonList(new com.google.android.exoplayer2.text.Cue("n" + (i < 10 ? "0" + i : String.valueOf(i))))));
        }

        return new SubtitleTimelineBuilder().build(events, 120_000_000L);
    }

    private static SubtitleTranslationService service(FakeTransport transport) {
        return new SubtitleTranslationService(transport,
                () -> new SubtitleTranslationConfig("https://api.deepseek.com", null, "zh-Hans", null),
                () -> "sk-test", "en", null);
    }

    private static List<String> idsInRequest(SubtitleTranslationRequest request) throws Exception {
        JSONObject body = new JSONObject(request.getBody());
        List<String> ids = new ArrayList<>();

        for (int i = 0; i < body.getJSONArray("items").length(); i++) {
            ids.add(body.getJSONArray("items").getJSONObject(i).getString("id"));
        }

        return ids;
    }

    @Test
    public void oneTickSendsAPreparedRequestAndItsAnswerReachesTheCacheAligned() throws Exception {
        FakeClock clock = new FakeClock();
        FakeTransport transport = new FakeTransport();
        SubtitleBatchPlanner planner = new SubtitleBatchPlanner();
        SubtitleTranslationCache cache = new SubtitleTranslationCache();
        SubtitleTranslationDispatcher dispatcher = new SubtitleTranslationDispatcher(planner, cache, service(transport), clock, null);

        dispatcher.setTimeline(timeline());
        dispatcher.setPosition(0);

        assertTrue(dispatcher.tick());
        assertEquals(1, transport.requests.size());

        SubtitleTranslationRequest sent = transport.requests.get(0);
        assertEquals("https://api.deepseek.com/chat/completions", sent.getUrl());
        assertEquals("Bearer sk-test", sent.getAuthorization());
        assertTrue(sent.getBody().contains("sourceLanguage"));
        assertFalse("the key never enters the body", sent.getBody().contains("sk-test"));

        List<String> inFlight = idsInRequest(sent);

        SubtitleBatch other = planner.nextBatch(timeline(), 0, false);
        assertTrue(other == null || Collections.disjoint(other.getItemIds(), inFlight));

        transport.succeed(0, "{\"items\":[{\"id\":\"" + inFlight.get(0) + "\",\"translation\":\"\u4e00\"}]}");

        assertEquals("\u4e00", cache.get(inFlight.get(0)));
        assertEquals(1, cache.size());

        List<String> aligned = SubtitleFrameTranslations.align(
                Arrays.asList(new SubtitleItem(inFlight.get(0), "n00"), new SubtitleItem("other-id", "n01")), cache.asLookup());
        assertEquals(Arrays.asList("\u4e00", null), aligned);
    }

    @Test
    public void rejectionIsRecordedAsAFailureAndRetriedOnce() throws Exception {
        FakeClock clock = new FakeClock();
        FakeTransport transport = new FakeTransport();
        SubtitleTranslationCache cache = new SubtitleTranslationCache();
        SubtitleTranslationDispatcher dispatcher = new SubtitleTranslationDispatcher(
                new SubtitleBatchPlanner(), cache, service(transport), clock, null);

        dispatcher.setTimeline(timeline());
        dispatcher.setPosition(0);
        assertTrue(dispatcher.tick());

        String firstId = idsInRequest(transport.requests.get(0)).get(0);
        transport.fail(0, 503);

        assertEquals("the failed attempt is recorded for every item", 1, cache.attempts(firstId));
        assertEquals(0, cache.size());

        clock.advance(2_000);
        assertTrue("a retryable failure is planned again", dispatcher.tick());
        assertEquals(2, transport.requests.size());
    }

    @Test
    public void stoppedSessionStatusIsRecordedButNeverCached() throws Exception {
        FakeClock clock = new FakeClock();
        FakeTransport transport = new FakeTransport();
        SubtitleTranslationCache cache = new SubtitleTranslationCache();
        SubtitleTranslationDispatcher dispatcher = new SubtitleTranslationDispatcher(
                new SubtitleBatchPlanner(), cache, service(transport), clock, null);

        dispatcher.setTimeline(timeline());
        dispatcher.setPosition(0);
        dispatcher.tick();

        transport.fail(0, 401);

        assertEquals(0, cache.size());
        assertEquals("a rejected attempt still counts against the item budget", 1,
                cache.attempts(idsInRequest(transport.requests.get(0)).get(0)));
    }
}
