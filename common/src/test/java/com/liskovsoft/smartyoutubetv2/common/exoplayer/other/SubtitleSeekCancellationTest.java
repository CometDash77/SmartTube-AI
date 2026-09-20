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
import static org.junit.Assert.assertTrue;

/**
 * T05/T06/T10 integration (final review path): a seek during an in-flight batch must cancel it, and
 * its late answer must neither reach the cache nor repaint the frame.
 */
@RunWith(RobolectricTestRunner.class)
public class SubtitleSeekCancellationTest {
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
        private SubtitleTranslationDispatcher.Callback callback;
        private SubtitleBatch batch;
        private boolean cancelled;

        @Override
        public SubtitleTranslationDispatcher.TranslationCall translate(SubtitleBatch batch,
                                                                      SubtitleTranslationDispatcher.Callback callback) {
            this.batch = batch;
            this.callback = callback;

            return () -> cancelled = true;
        }

        void succeed(String translation) {
            callback.onSuccess(batch, Collections.singletonList(translation));
        }
    }

    private static class FixedClock implements SubtitleTranslationDispatcher.Clock {
        private long nowMs = 10_000;

        @Override
        public long elapsedRealtimeMs() {
            return nowMs;
        }
    }

    @Test
    public void aLateAnswerAfterASubtitleSourceChangeIsDropped() {
        RecordingDisplay display = new RecordingDisplay();
        SelectedSubtitleSource source = new SelectedSubtitleSource(1, "https://example.com/subs",
                "en", "en", "English", "text/vtt", null, null, true);
        SelectedSubtitleSource otherSource = new SelectedSubtitleSource(1, "https://example.com/subs2",
                "en", "en", "English", "text/vtt", null, null, true);
        final SelectedSubtitleSource[] current = {source};
        AiSubtitleSessionBinder binder = new AiSubtitleSessionBinder(display, () -> current[0]);
        SubtitleTranslationCache cache = new SubtitleTranslationCache();
        FakeService service = new FakeService();
        SubtitleTranslationDispatcher dispatcher = new SubtitleTranslationDispatcher(
                new SubtitleBatchPlanner(), cache, service, new FixedClock(), null);

        SubtitleTimeline timeline = new SubtitleTimelineBuilder().build(
                Collections.singletonList(new SubtitleEvent(0,
                        Collections.singletonList(new com.google.android.exoplayer2.text.Cue("Hello")))), 2_000_000L);

        binder.setTranslationCache(cache);
        binder.installDispatcher(dispatcher);
        binder.setTimeline(timeline);
        binder.onVideoLoaded();
        binder.onAiEnabled(true);

        assertTrue("the tick starts the batch", binder.onTick(0));

        // The user selects another subtitle track while the batch is still in flight.
        current[0] = otherSource;
        binder.onTrackChanged();

        // The abandoned batch answers anyway.
        service.succeed("\u4f60\u597d");

        assertEquals("a batch of the previous source must not be cached", 0, cache.size());
        assertEquals(Collections.emptyList(), display.translations);
    }

    @Test
    public void aLateAnswerAfterAContentConfigurationChangeIsDropped() {
        RecordingDisplay display = new RecordingDisplay();
        SelectedSubtitleSource source = new SelectedSubtitleSource(1, "https://example.com/subs",
                "en", "en", "English", "text/vtt", null, null, true);
        AiSubtitleSessionBinder binder = new AiSubtitleSessionBinder(display, () -> source);
        SubtitleTranslationCache cache = new SubtitleTranslationCache();
        FakeService service = new FakeService();
        SubtitleTranslationDispatcher dispatcher = new SubtitleTranslationDispatcher(
                new SubtitleBatchPlanner(), cache, service, new FixedClock(), null);

        SubtitleTimeline timeline = new SubtitleTimelineBuilder().build(
                Collections.singletonList(new SubtitleEvent(0,
                        Collections.singletonList(new com.google.android.exoplayer2.text.Cue("Hello")))), 2_000_000L);

        binder.setTranslationCache(cache);
        binder.installDispatcher(dispatcher);
        binder.setTimeline(timeline);
        binder.onVideoLoaded();
        binder.onAiEnabled(true);

        assertTrue("the tick starts the batch", binder.onTick(0));
        cache.put("previous", "\u65e7");

        // The user changes the target language (or another content setting): one transaction moves
        // the identity on, cancels the in-flight call and drops the old configuration's cache.
        binder.onConfigurationChanged();

        assertEquals("the old configuration's cache is dropped", 0, cache.size());

        // The old configuration's answer arrives anyway.
        service.succeed("\u4f60\u597d");

        assertEquals("a late answer of the old content must not be cached", 0, cache.size());
        assertEquals("nor repaint the frame", Collections.emptyList(), display.translations);
    }

    @Test
    public void aLateAnswerAfterSeekIsDroppedAndNothingIsRepainted() {
        RecordingDisplay display = new RecordingDisplay();
        SelectedSubtitleSource source = new SelectedSubtitleSource(1, "https://example.com/subs",
                "en", "en", "English", "text/vtt", null, null, true);
        AiSubtitleSessionBinder binder = new AiSubtitleSessionBinder(display, () -> source);
        SubtitleTranslationCache cache = new SubtitleTranslationCache();
        FakeService service = new FakeService();
        SubtitleTranslationDispatcher dispatcher = new SubtitleTranslationDispatcher(
                new SubtitleBatchPlanner(), cache, service, new FixedClock(), null);

        SubtitleTimeline timeline = new SubtitleTimelineBuilder().build(
                Collections.singletonList(new SubtitleEvent(0,
                        Collections.singletonList(new com.google.android.exoplayer2.text.Cue("Hello")))), 2_000_000L);

        binder.setTranslationCache(cache);
        binder.installDispatcher(dispatcher);
        binder.setTimeline(timeline);
        binder.onVideoLoaded();
        binder.onAiEnabled(true);

        assertTrue("the tick starts the batch", binder.onTick(0));
        assertFalse(cache.hasSuccess(timeline.frameAt(0).getItems().get(0).getItemId()));
        int writesBeforeSeek = display.writes;

        // The user seeks: the session identity is invalidated and the in-flight call is cancelled.
        binder.onSeekEnd();

        // The cancelled call answers anyway.
        service.succeed("\u4f60\u597d");

        assertEquals("a late answer must not be cached", 0, cache.size());
        assertEquals("nor repaint the frame", writesBeforeSeek + 1, display.writes);
        assertEquals(Collections.emptyList(), display.translations);
        assertNull(cache.get(timeline.frameAt(0).getItems().get(0).getItemId()));
    }
}
