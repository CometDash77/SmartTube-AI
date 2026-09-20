package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** T05 acceptance for the player-event to session mapping. */
public class AiSubtitleSessionBinderTest {
    private static final String ORIGIN_URL = "https://example.com/api/timedtext?v=abc&lang=en";
    private static final String OTHER_URL = ORIGIN_URL + "&tlang=zh-Hans";

    private RecordingDisplay mDisplay;
    private SelectedSubtitleSource mSource;
    private AiSubtitleSessionBinder mBinder;

    private static class RecordingDisplay implements SubtitleDisplay {
        private final List<String> actions = new ArrayList<>();
        private List<String> translations = Collections.emptyList();

        @Override
        public void setTranslations(List<String> values) {
            translations = new ArrayList<>(values);
            actions.add("set");
        }

        @Override
        public void clearTranslations() {
            translations = Collections.emptyList();
            actions.add("clear");
        }

        @Override
        public void setAiDisplayMode(int mode) {
            actions.add("mode:" + mode);
        }

        @Override
        public void resetOriginalCueState() {
            actions.add("reset");
        }
    }

    private static SelectedSubtitleSource source(String url) {
        return new SelectedSubtitleSource(1, url, "en", "zh-Hans", "Chinese (Simplified)*",
                "application/x-mp4-vtt", null, null, true);
    }

    @Before
    public void setUp() {
        mDisplay = new RecordingDisplay();
        mBinder = new AiSubtitleSessionBinder(mDisplay, () -> mSource);
        mSource = source(ORIGIN_URL);
    }

    @Test
    public void videoLoadStartsANewSessionAndResolvesTheSource() {
        mBinder.onVideoLoaded();

        assertTrue(mBinder.getController().hasActiveSession());
        assertTrue(mBinder.getController().applyTranslations(mBinder.getController().currentToken(), Arrays.asList("\u4f60\u597d")));
        assertEquals(Arrays.asList("\u4f60\u597d"), mDisplay.translations);
    }

    @Test
    public void seekClearsTheFrameAndResetsTheOriginalTextState() {
        mBinder.onVideoLoaded();
        AiSubtitleController.Token token = mBinder.getController().currentToken();
        mBinder.getController().applyTranslations(token, Arrays.asList("\u4f60\u597d"));

        mBinder.onSeekEnd();

        assertEquals(Collections.emptyList(), mDisplay.translations);
        assertEquals("reset", mDisplay.actions.get(mDisplay.actions.size() - 1));
        assertFalse(mBinder.getController().applyTranslations(token, Arrays.asList("\u4f60\u597d")));
    }

    @Test
    public void sourceChangeInvalidatesThePreviousResult() {
        mBinder.onVideoLoaded();
        AiSubtitleController.Token token = mBinder.getController().currentToken();

        mSource = source(OTHER_URL);
        mBinder.onTrackChanged();

        assertFalse(mBinder.getController().applyTranslations(token, Arrays.asList("\u4f60\u597d")));
        assertTrue(mBinder.getController().hasActiveSession());
    }

    @Test
    public void mediaSourceReplacementResolvesTheSourceAgain() {
        mBinder.onVideoLoaded();

        mBinder.onSourceChanged();

        assertTrue(mBinder.getController().hasActiveSession());
    }

    @Test
    public void subtitlesHiddenEndTheSession() {
        mBinder.onVideoLoaded();

        mBinder.onSubtitlesShown(false);

        assertFalse(mBinder.getController().hasActiveSession());
        assertEquals(Collections.emptyList(), mDisplay.translations);
    }

    @Test
    public void missingSourceMeansNoSession() {
        mSource = null;

        mBinder.onVideoLoaded();

        assertFalse(mBinder.getController().hasActiveSession());
        assertFalse(mBinder.getController().applyTranslations(mBinder.getController().currentToken(), Arrays.asList("\u4f60\u597d")));
    }

    @Test
    public void engineReleaseEndsTheSession() {
        mBinder.onVideoLoaded();

        mBinder.onEngineReleased();

        assertFalse(mBinder.getController().hasActiveSession());
        assertFalse(mBinder.getController().isAiEnabled());
    }

    @Test
    public void tickFeedsMicrosecondsOnlyWhileAiIsOn() {
        RecordingTickTarget target = new RecordingTickTarget();
        mBinder.setTickTarget(target);
        mBinder.onVideoLoaded();

        assertFalse("AI starts disabled", mBinder.onTick(1_500));

        mBinder.onAiEnabled(true);
        assertTrue(mBinder.onTick(1_500));
        assertEquals(1_500_000L, target.positionUs);
        assertEquals(1, target.calls);

        mBinder.onAiEnabled(false);
        assertFalse(mBinder.onTick(9_000));
        assertEquals("no ticks while AI is off", 1, target.calls);
    }

    @Test
    public void tickWithoutADriverDoesNothing() {
        mBinder.onVideoLoaded();
        mBinder.onAiEnabled(true);

        assertFalse(mBinder.onTick(1_000));
    }

    private static class RecordingTickTarget implements AiSubtitleSessionBinder.TickTarget {
        private long positionUs = -1;
        private int calls;

        @Override
        public boolean tick(long positionUs) {
            this.positionUs = positionUs;
            calls++;

            return true;
        }
    }

    @Test
    public void currentFrameIsWrittenFromTheCacheOfTheLiveSession() {
        SubtitleTranslationCache cache = new SubtitleTranslationCache();
        cache.put("b", "\u4e8c");
        mBinder.setTranslationCache(cache);
        mBinder.onVideoLoaded();
        mBinder.onAiEnabled(true);
        mBinder.onFrameItems(Arrays.asList(new SubtitleItem("a", "One"), new SubtitleItem("b", "Two")));

        assertTrue(mBinder.applyCurrentFrame());
        assertEquals(Arrays.asList(null, "\u4e8c"), mDisplay.translations);
    }

    @Test
    public void currentFrameIsNotWrittenWhileAiIsOff() {
        SubtitleTranslationCache cache = new SubtitleTranslationCache();
        cache.put("a", "\u4e00");
        mBinder.setTranslationCache(cache);
        mBinder.onVideoLoaded();
        mBinder.onFrameItems(Collections.singletonList(new SubtitleItem("a", "One")));

        assertFalse(mBinder.applyCurrentFrame());
    }

    @Test
    public void currentFrameWithoutItemsOrCacheIsANoOp() {
        mBinder.onVideoLoaded();
        mBinder.onAiEnabled(true);

        assertFalse("no cache installed", mBinder.applyCurrentFrame());

        mBinder.setTranslationCache(new SubtitleTranslationCache());

        assertFalse("no frame items recorded", mBinder.applyCurrentFrame());
    }

    @Test
    public void showingSubtitlesAgainRestoresSessionAndCachedFrame() {
        SubtitleTranslationCache cache = new SubtitleTranslationCache();
        mBinder.setTranslationCache(cache);
        mBinder.onVideoLoaded();
        mBinder.onAiEnabled(true);
        mBinder.onFrameItems(Collections.singletonList(new SubtitleItem("a", "One")));
        cache.put("a", "\u4e00");

        mBinder.onSubtitlesShown(false);
        assertFalse(mBinder.getController().hasActiveSession());

        mBinder.onSubtitlesShown(true);

        assertTrue("the source session is restored", mBinder.getController().hasActiveSession());
        assertEquals("the cached frame is repainted without a new request", Arrays.asList("\u4e00"), mDisplay.translations);
    }

    @Test
    public void showingSubtitlesWithoutACachedFrameIsHarmless() {
        mBinder.onVideoLoaded();
        mBinder.onAiEnabled(true);
        mBinder.onSubtitlesShown(false);

        mBinder.onSubtitlesShown(true);

        assertTrue(mBinder.getController().hasActiveSession());
        assertEquals(Collections.emptyList(), mDisplay.translations);
    }

    @Test
    public void installedDispatcherDrivesTicksAndHonoursTheRetryDelay() {
        FakeClock clock = new FakeClock();
        SubtitleTranslationDispatcher dispatcher = new SubtitleTranslationDispatcher(
                new SubtitleBatchPlanner(), new SubtitleTranslationCache(), (batch, callback) -> null, clock, null);
        mBinder.installDispatcher(dispatcher);
        mBinder.onVideoLoaded();
        mBinder.onAiEnabled(true);

        assertFalse("no timeline yet: nothing starts", mBinder.onTick(1_000));

        mBinder.onRetryDelay(5_000);

        assertTrue("the retry delay becomes a dispatcher pause", dispatcher.isCoolingDown());
        assertEquals(5_000, dispatcher.getCooldownRemainingMs());
        assertFalse(mBinder.onTick(2_000));
    }

    @Test
    public void retryDelayWithoutADispatcherIsHarmless() {
        mBinder.onRetryDelay(5_000); // no dispatcher installed

        assertFalse(mBinder.onTick(1_000));
    }

    private static class FakeClock implements SubtitleTranslationDispatcher.Clock {
        private long mNowMs = 10_000;

        @Override
        public long elapsedRealtimeMs() {
            return mNowMs;
        }

        void advance(long deltaMs) {
            mNowMs += deltaMs;
        }
    }

    @Test
    public void loopCreationIsSafeWithoutASession() {
        java.util.List<Runnable> pending = new java.util.ArrayList<>();
        SubtitlePrefetchTicker.Scheduler scheduler = new SubtitlePrefetchTicker.Scheduler() {
            @Override
            public void postDelayed(Runnable task, long delayMs) {
                pending.add(task);
            }

            @Override
            public void removeCallbacks(Runnable task) {
                pending.remove(task);
            }
        };

        SubtitlePrefetchLoop loop = mBinder.createLoop(scheduler, () -> 1_000);

        loop.start();
        assertTrue(loop.isRunning());
        assertEquals(1, pending.size());

        loop.stop();
        assertFalse(loop.isRunning());
        assertEquals(0, pending.size());
    }


    @Test
    public void aTickTracksTheFrameOfTheTimelineAndRepaintsItsCachedTranslation() {
        SubtitleTimeline timeline = new SubtitleTimelineBuilder().build(
                java.util.Arrays.asList(new SubtitleEvent(0, Collections.singletonList(new com.google.android.exoplayer2.text.Cue("One"))),
                        new SubtitleEvent(10_000_000L, Collections.singletonList(new com.google.android.exoplayer2.text.Cue("Two")))),
                20_000_000L);
        String secondItemId = timeline.frameAt(10_000_000L).getItems().get(0).getItemId();
        SubtitleTranslationCache cache = new SubtitleTranslationCache();
        cache.put(secondItemId, "\u4e8c");

        mBinder.setTranslationCache(cache);
        mBinder.setTimeline(timeline);
        mBinder.setTickTarget(positionUs -> true); // a call is started, so the loop would not repaint
        mBinder.onVideoLoaded();
        mBinder.onAiEnabled(true);

        assertTrue(mBinder.onTick(10_000)); // position 10 s

        assertEquals(Arrays.asList("Two"), textsOf(mBinder.getCurrentFrameItems()));
        assertTrue(mBinder.applyCurrentFrame());
        assertEquals(Arrays.asList("\u4e8c"), mDisplay.translations);
    }

    private static java.util.List<String> textsOf(java.util.List<SubtitleItem> items) {
        java.util.List<String> texts = new java.util.ArrayList<>();

        for (SubtitleItem item : items) {
            texts.add(item.getText());
        }

        return texts;
    }

    @Test
    public void aiAndModeChangesReachTheController() {
        mBinder.onVideoLoaded();

        mBinder.onAiEnabled(true);
        mBinder.onDisplayMode(SubtitleComposer.MODE_BILINGUAL);

        assertTrue(mBinder.getController().isAiEnabled());
        assertEquals(SubtitleComposer.MODE_BILINGUAL, mBinder.getController().getDisplayMode());
        assertTrue(mDisplay.actions.contains("mode:" + SubtitleComposer.MODE_BILINGUAL));
    }
}
