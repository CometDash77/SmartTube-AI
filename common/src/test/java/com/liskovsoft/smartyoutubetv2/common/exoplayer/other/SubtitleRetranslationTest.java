package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Plan 4.4 acceptance: the forced-retranslation transaction really asks the service again, drops the
 * previous generation in every direction (cache, display, examples) and keeps what is independent of
 * the translation results (original timeline and the frozen summary).
 */
@RunWith(RobolectricTestRunner.class)
public class SubtitleRetranslationTest {
    private static class RecordingDisplay implements SubtitleDisplay {
        List<String> translations = Collections.emptyList();
        int writes;

        @Override
        public void setTranslations(List<String> values) {
            translations = new ArrayList<>(values);
            writes++;
        }

        @Override
        public void clearTranslations() {
            translations = Collections.emptyList();
            writes++;
        }

        @Override
        public void setAiDisplayMode(int mode) {
        }

        @Override
        public void resetOriginalCueState() {
        }
    }

    private static class FakeService implements SubtitleTranslationDispatcher.TranslationService {
        private final List<SubtitleBatch> batches = new ArrayList<>();
        private final List<SubtitleTranslationDispatcher.Callback> callbacks = new ArrayList<>();
        private final List<Boolean> cancelled = new ArrayList<>();

        @Override
        public SubtitleTranslationDispatcher.TranslationCall translate(SubtitleBatch batch,
                                                                      SubtitleTranslationDispatcher.Callback callback) {
            batches.add(batch);
            callbacks.add(callback);
            final int index = cancelled.size();
            cancelled.add(false);

            return () -> cancelled.set(index, true);
        }

        int startCount() {
            return batches.size();
        }

        void succeed(int index, String translation) {
            callbacks.get(index).onSuccess(batches.get(index), Collections.singletonList(translation));
        }
    }

    private static class FixedClock implements SubtitleTranslationDispatcher.Clock {
        private long nowMs = 10_000;

        @Override
        public long elapsedRealtimeMs() {
            return nowMs;
        }

        void advance(long deltaMs) {
            nowMs += deltaMs;
        }
    }

    private static SubtitleTimeline timeline() {
        return new SubtitleTimelineBuilder().build(Collections.singletonList(new SubtitleEvent(0,
                Collections.singletonList(new com.google.android.exoplayer2.text.Cue("Hello")))), 2_000_000L);
    }

    private static SelectedSubtitleSource source() {
        return new SelectedSubtitleSource(1, "https://example.com/subs",
                "en", "en", "English", "text/vtt", null, null, true);
    }

    @Test
    public void aStoredSuccessIsRequestedAgainAndTheOriginalIsShown() {
        RecordingDisplay display = new RecordingDisplay();
        AiSubtitleSessionBinder binder = new AiSubtitleSessionBinder(display, SubtitleRetranslationTest::source);
        SubtitleTranslationCache cache = new SubtitleTranslationCache();
        FakeService service = new FakeService();
        FixedClock clock = new FixedClock();
        SubtitleTranslationDispatcher dispatcher = new SubtitleTranslationDispatcher(
                new SubtitleBatchPlanner(), cache, service, clock, null);
        SubtitleTimeline timeline = timeline();

        binder.setTranslationCache(cache);
        binder.installDispatcher(dispatcher);
        binder.setTimeline(timeline);
        binder.onVideoLoaded();
        binder.onAiEnabled(true);

        assertTrue("the first batch starts", binder.onTick(0));
        service.succeed(0, "Hallo");

        String itemId = timeline.frameAt(0).getItems().get(0).getItemId();
        assertTrue("the answer is cached", cache.hasSuccess(itemId));

        binder.onFrameItems(timeline.frameAt(0).getItems());
        assertTrue(binder.applyCurrentFrame());
        assertEquals(Collections.singletonList("Hallo"), display.translations);

        assertTrue(binder.retranslateCurrentSource());

        assertEquals("the stored translation of this source is gone", 0, cache.size());
        assertEquals("the frame falls back to the original text", 1, display.translations.size());
        assertNull(display.translations.get(0));
        assertSame("the original timeline is reused, not downloaded again", timeline, binder.getTimeline());

        clock.advance(2_000);

        assertTrue("a successful item really produces a new request", binder.onTick(0));
        assertEquals(2, service.startCount());
    }

    @Test
    public void aLateAnswerOfThePreviousGenerationIsDropped() {
        RecordingDisplay display = new RecordingDisplay();
        AiSubtitleSessionBinder binder = new AiSubtitleSessionBinder(display, SubtitleRetranslationTest::source);
        SubtitleTranslationCache cache = new SubtitleTranslationCache();
        FakeService service = new FakeService();
        SubtitleTranslationDispatcher dispatcher = new SubtitleTranslationDispatcher(
                new SubtitleBatchPlanner(), cache, service, new FixedClock(), null);

        binder.setTranslationCache(cache);
        binder.installDispatcher(dispatcher);
        binder.setTimeline(timeline());
        binder.onVideoLoaded();
        binder.onAiEnabled(true);

        assertTrue(binder.onTick(0));
        assertTrue(binder.retranslateCurrentSource());
        int writesAfterRetranslation = display.writes;

        service.succeed(0, "Hallo"); // the abandoned generation answers anyway

        assertEquals("a late answer must not enter the cache", 0, cache.size());
        assertEquals("nor repaint the frame", writesAfterRetranslation, display.writes);
    }

    @Test
    public void theFrozenSummarySurvivesWhileTheExamplesDoNot() {
        RecordingDisplay display = new RecordingDisplay();
        AiSubtitleSessionBinder binder = new AiSubtitleSessionBinder(display, SubtitleRetranslationTest::source);
        SubtitleTranslationCache cache = new SubtitleTranslationCache();
        FakeService service = new FakeService();
        SubtitleTranslationDispatcher dispatcher = new SubtitleTranslationDispatcher(
                new SubtitleBatchPlanner(), cache, service, new FixedClock(), null);
        SubtitleSessionContext sessionContext = new SubtitleSessionContext();

        binder.setTranslationCache(cache);
        binder.setSessionContext(sessionContext);
        binder.installDispatcher(dispatcher);
        binder.setTimeline(timeline());
        binder.onVideoLoaded();
        binder.onAiEnabled(true);

        assertTrue(binder.onTick(0));
        service.succeed(0, "Hallo");

        assertFalse("the verified success became an example",
                sessionContext.examplesBefore(1_000_000_000L, 1_000).isEmpty());
        sessionContext.freezeSummary(SubtitleSummary.of("A greeting", null));

        assertTrue(binder.retranslateCurrentSource());

        assertTrue("the frozen summary is independent of the translations", sessionContext.hasSummary());
        assertTrue("the examples of the old generation are gone",
                sessionContext.examplesBefore(1_000_000_000L, 1_000).isEmpty());
    }

    @Test
    public void anUnchangedConfigurationNeverReRequestsAFinishedItem() {
        RecordingDisplay display = new RecordingDisplay();
        AiSubtitleSessionBinder binder = new AiSubtitleSessionBinder(display, SubtitleRetranslationTest::source);
        SubtitleTranslationCache cache = new SubtitleTranslationCache();
        FakeService service = new FakeService();
        FixedClock clock = new FixedClock();
        SubtitleTranslationDispatcher dispatcher = new SubtitleTranslationDispatcher(
                new SubtitleBatchPlanner(), cache, service, clock, null);

        binder.setTranslationCache(cache);
        binder.installDispatcher(dispatcher);
        binder.setTimeline(timeline());
        binder.onVideoLoaded();
        binder.onAiEnabled(true);

        assertTrue(binder.onTick(0));
        service.succeed(0, "Hallo");

        assertEquals(1, service.startCount());

        // An unrelated event re-syncs a configuration that did not change (a display-mode switch, a
        // snapshot settle, the same rule value again). Finished work must stay finished.
        binder.onDisplayMode(SubtitleComposer.MODE_BILINGUAL);
        binder.setRuleSegmentation(false);
        binder.setRuleSegmentation(false);
        clock.advance(2_000);

        assertFalse("no new work while nothing changed", binder.onTick(0));
        assertEquals("a finished item must not be requested again", 1, service.startCount());
    }

    @Test
    public void retranslationIsRefusedWithoutAiOrWithoutASource() {
        RecordingDisplay display = new RecordingDisplay();
        SubtitleTimeline timeline = timeline();
        AiSubtitleSessionBinder binder = new AiSubtitleSessionBinder(display, SubtitleRetranslationTest::source);
        binder.setTranslationCache(new SubtitleTranslationCache());
        binder.installDispatcher(new SubtitleTranslationDispatcher(new SubtitleBatchPlanner(),
                new SubtitleTranslationCache(), new FakeService(), new FixedClock(), null));
        binder.setTimeline(timeline);
        binder.onVideoLoaded();

        assertFalse("AI off has nothing to retranslate", binder.retranslateCurrentSource());

        binder.onAiEnabled(true);

        AiSubtitleSessionBinder withoutSource = new AiSubtitleSessionBinder(display, () -> null);
        withoutSource.setTimeline(timeline);
        withoutSource.onVideoLoaded();
        withoutSource.onAiEnabled(true);

        assertFalse("no selected source has nothing to retranslate", withoutSource.retranslateCurrentSource());
    }
}
