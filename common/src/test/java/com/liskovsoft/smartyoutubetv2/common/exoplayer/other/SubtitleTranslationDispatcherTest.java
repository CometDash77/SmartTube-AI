package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** T06 acceptance for dispatch: one call at a time, interval, cancellation and retry budget. */
public class SubtitleTranslationDispatcherTest {
    private FakeClock mClock;
    private FakeService mService;
    private SubtitleBatchPlanner mPlanner;
    private SubtitleTranslationCache mCache;
    private SubtitleTranslationDispatcher mDispatcher;

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

    private static class FakeService implements SubtitleTranslationDispatcher.TranslationService {
        private final List<String> summaries = new ArrayList<>();
        private final List<SubtitleTranslationDispatcher.Callback> callbacks = new ArrayList<>();
        private final List<Boolean> cancelled = new ArrayList<>();
        private final List<SubtitleBatch> batches = new ArrayList<>();

        @Override
        public SubtitleTranslationDispatcher.TranslationCall translate(SubtitleBatch batch,
                                                                      SubtitleTranslationDispatcher.Callback callback) {
            batches.add(batch);
            callbacks.add(callback);
            summaries.add(batch.getItems().size() + ":" + batch.getItems().get(0).getText());
            cancelled.add(false);

            final int index = cancelled.size() - 1;

            return () -> cancelled.set(index, true);
        }

        int startCount() {
            return batches.size();
        }

        void succeed(int index, String... translations) {
            callbacks.get(index).onSuccess(batches.get(index), Arrays.asList(translations));
        }

        void fail(int index) {
            callbacks.get(index).onFailure(batches.get(index));
        }
    }

    private static class RecordingListener implements SubtitleTranslationDispatcher.ResultListener {
        private int results;
        private boolean lastSuccess;

        @Override
        public void onBatchResult(SubtitleBatch batch, List<String> translations, boolean success) {
            results++;
            lastSuccess = success;
        }
    }

    private static SubtitleTimeline timeline(int itemCount) {
        List<SubtitleEvent> events = new ArrayList<>();

        for (int i = 0; i < itemCount; i++) {
            events.add(new SubtitleEvent(i * 10_000_000L,
                    Collections.singletonList(new com.google.android.exoplayer2.text.Cue(
                            "n" + (i < 10 ? "0" + i : String.valueOf(i))))));
        }

        return new SubtitleTimelineBuilder().build(events, itemCount * 10_000_000L);
    }

    @Before
    public void setUp() {
        mClock = new FakeClock();
        mService = new FakeService();
        mPlanner = new SubtitleBatchPlanner();
        mCache = new SubtitleTranslationCache();
        mDispatcher = new SubtitleTranslationDispatcher(mPlanner, mCache, mService, mClock, new RecordingListener());
        mDispatcher.setTimeline(timeline(60));
        mDispatcher.setPosition(0);
    }

    @Test
    public void startsAtMostOneCallAtATime() {
        assertTrue(mDispatcher.tick());
        assertTrue(mDispatcher.isBusy());

        mClock.advance(5_000);

        assertFalse("a second call must wait for the first", mDispatcher.tick());
        assertEquals(1, mService.startCount());
    }

    @Test
    public void respectsTheMinimumStartInterval() {
        assertTrue(mDispatcher.tick());
        mService.succeed(0, "a");
        mClock.advance(999);

        assertFalse(mDispatcher.tick());

        mClock.advance(1);

        assertTrue(mDispatcher.tick());
        assertEquals(2, mService.startCount());
    }

    @Test
    public void cancelledCallBlocksTheSlotUntilItReportsBack() {
        mDispatcher.tick();

        mDispatcher.cancel();
        mClock.advance(5_000);

        assertTrue("cancelled list", mService.cancelled.get(0));
        assertFalse("a slow cancellation must not become a second concurrent call", mDispatcher.tick());
        assertEquals(1, mService.startCount());

        mService.succeed(0, "late"); // the cancelled call finally reports

        assertFalse("the slot is free once the cancelled call reports back", mDispatcher.isBusy());
        assertEquals("a cancelled batch's success is dropped", 0, mCache.size());
    }

    @Test
    public void successIsCachedAndItsItemsAreNotPlannedAgain() {
        mDispatcher.tick();
        SubtitleBatch batch = mService.batches.get(0);
        List<String> translations = new ArrayList<>();

        for (int i = 0; i < batch.getItems().size(); i++) {
            translations.add("t" + i);
        }

        mService.succeed(0, translations.toArray(new String[0]));

        assertEquals(batch.getItems().size(), 0 + batch.getItems().size());
        for (int i = 0; i < batch.getItems().size(); i++) {
            assertEquals("t" + i, mCache.get(batch.getItems().get(i).getItemId()));
        }

        mClock.advance(2_000);
        mDispatcher.tick();

        assertEquals(2, mService.startCount());
        for (String id : batch.getItemIds()) {
            assertFalse(mService.batches.get(1).getItemIds().contains(id));
        }
    }

    @Test
    public void retryableFailureIsPlannedAgainAndThenAbandoned() {
        mDispatcher.tick();
        SubtitleBatch batch = mService.batches.get(0);

        mService.fail(0); // attempt 1

        mClock.advance(2_000);
        assertTrue(mDispatcher.tick());
        assertTrue("failed items are retried", mService.batches.get(1).getItemIds().containsAll(batch.getItemIds()));

        mService.fail(1); // attempt 2 exhausts the budget and starts the cooldown

        mClock.advance(40_000); // past the cooldown
        mDispatcher.tick();

        for (String id : batch.getItemIds()) {
            assertTrue(mCache.hasExhausted(id));
        }

        // The window may hold other, untranslated items; the abandoned ones must not reappear.
        assertTrue("abandoned items are never resubmitted",
                java.util.Collections.disjoint(mService.batches.get(2).getItemIds(), batch.getItemIds()));
    }

    @Test
    public void seekPrioritisesTheItemAtTheNewPosition() {
        mDispatcher.tick();
        SubtitleBatch firstBatch = mService.batches.get(0);
        assertEquals("n00", firstBatch.getItems().get(0).getText());

        mDispatcher.cancel();
        mService.fail(0); // the cancelled call reports back and frees the slot
        mDispatcher.setPosition(50 * 10_000_000L);
        mClock.advance(2_000);

        assertTrue(mDispatcher.tick());
        SubtitleBatch afterSeek = mService.batches.get(1);
        assertEquals("n50", afterSeek.getItems().get(0).getText());
    }

    @Test
    public void consecutiveFailuresEnterACooldownAndSuccessClearsIt() {
        mDispatcher.tick();
        mService.fail(0);
        mClock.advance(2_000);
        mDispatcher.tick();
        mService.fail(1); // second consecutive failure starts the cooldown

        assertTrue(mDispatcher.isCoolingDown());
        mClock.advance(1_000);
        assertFalse("no requests during the cooldown", mDispatcher.tick());

        mClock.advance(SubtitleTranslationDispatcher.FAILURE_COOLDOWN_MS);
        assertTrue(mDispatcher.tick());

        mService.succeed(2, "a");
        assertFalse(mDispatcher.isCoolingDown());
    }

    @Test
    public void successResetsTheFailureStreak() {
        mDispatcher.tick();
        mService.fail(0);
        mClock.advance(2_000);
        mDispatcher.tick();
        mService.succeed(1, "a"); // breaks the streak before the second failure
        mClock.advance(2_000);
        mDispatcher.tick();

        if (mService.startCount() > 2) {
            mService.fail(mService.startCount() - 1); // first failure of a fresh streak
        }

        assertFalse("one failure after a success is not a cooldown", mDispatcher.isCoolingDown());
    }

    @Test
    public void externalRateLimitDelayPausesDispatch() {
        mDispatcher.pauseFor(5_000);

        assertTrue(mDispatcher.isCoolingDown());
        assertFalse("no call starts during the retry-after pause", mDispatcher.tick());

        mClock.advance(5_000);

        assertFalse(mDispatcher.isCoolingDown());
        assertTrue(mDispatcher.tick());
    }

    @Test
    public void aShorterExternalDelayCannotShortenAnExistingCooldown() {
        mDispatcher.pauseFor(30_000);
        mDispatcher.pauseFor(1_000);

        mClock.advance(10_000);

        assertTrue("the longer pause is kept", mDispatcher.isCoolingDown());
    }

    @Test
    public void cooldownRemainingTracksThePauseWithoutGoingNegative() {
        assertEquals(0, mDispatcher.getCooldownRemainingMs());

        mDispatcher.pauseFor(5_000);
        assertEquals(5_000, mDispatcher.getCooldownRemainingMs());

        mClock.advance(2_000);
        assertEquals(3_000, mDispatcher.getCooldownRemainingMs());

        mClock.advance(2_000);
        assertEquals(1_000, mDispatcher.getCooldownRemainingMs());

        mClock.advance(5_000);
        assertEquals(0, mDispatcher.getCooldownRemainingMs());
        assertFalse(mDispatcher.isCoolingDown());
    }

    @Test
    public void nothingIsStartedWithoutATimeline() {
        SubtitleTranslationDispatcher dispatcher = new SubtitleTranslationDispatcher(mPlanner, mCache, mService, mClock, null);

        assertFalse(dispatcher.tick());
        assertFalse(dispatcher.isBusy());
    }

    @Test
    public void resultForAnotherBatchIsIgnored() {
        RecordingListener listener = new RecordingListener();
        mDispatcher = new SubtitleTranslationDispatcher(mPlanner, mCache, mService, mClock, listener);
        mDispatcher.setTimeline(timeline(60));
        mDispatcher.setPosition(0);
        mDispatcher.tick();

        SubtitleBatch foreign = new SubtitleBatch(
                Arrays.asList(new SubtitleItem("foreign", "x")), null, null);

        mService.callbacks.get(0).onSuccess(foreign, Arrays.asList("y"));

        assertEquals(0, listener.results);
        assertNotNull(mService.batches.get(0));
    }
}
