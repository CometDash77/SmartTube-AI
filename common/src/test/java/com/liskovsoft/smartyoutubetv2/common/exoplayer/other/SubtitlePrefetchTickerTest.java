package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** T06 slice: one periodic callback, no residual timer. */
public class SubtitlePrefetchTickerTest {
    private static class FakeScheduler implements SubtitlePrefetchTicker.Scheduler {
        private final List<Runnable> pending = new ArrayList<>();
        private final List<Long> delays = new ArrayList<>();
        private int removals;

        @Override
        public void postDelayed(Runnable task, long delayMs) {
            pending.add(task);
            delays.add(delayMs);
        }

        @Override
        public void removeCallbacks(Runnable task) {
            pending.remove(task);
            removals++;
        }

        int pendingCount() {
            return pending.size();
        }

        void runPending() {
            List<Runnable> tasks = new ArrayList<>(pending);
            pending.clear();

            for (Runnable task : tasks) {
                task.run();
            }
        }
    }

    private FakeScheduler mScheduler;
    private int mTicks;
    private SubtitlePrefetchTicker mTicker;

    @Test
    public void aCustomIntervalDrivesTheSameSingleCallbackClock() {
        SubtitlePrefetchTicker ticker = new SubtitlePrefetchTicker(mScheduler, () -> mTicks++,
                SubtitlePrefetchTicker.DERIVED_DISPLAY_INTERVAL_MS);

        ticker.start();
        ticker.start();

        assertEquals("one pending callback", 1, mScheduler.pendingCount());
        assertEquals(SubtitlePrefetchTicker.DERIVED_DISPLAY_INTERVAL_MS,
                mScheduler.delays.get(0).longValue());
    }

    @Before
    public void setUp() {
        mScheduler = new FakeScheduler();
        mTicks = 0;
        mTicker = new SubtitlePrefetchTicker(mScheduler, () -> mTicks++);
    }

    @Test
    public void startPostsOneCallbackAtTheOneSecondInterval() {
        mTicker.start();

        assertTrue(mTicker.isRunning());
        assertEquals(1, mScheduler.pendingCount());
        assertEquals(SubtitlePrefetchTicker.INTERVAL_MS, (long) mScheduler.delays.get(0));
    }

    @Test
    public void startingTwiceDoesNotCreateASecondTimer() {
        mTicker.start();
        mTicker.start();

        assertEquals(1, mScheduler.pendingCount());
    }

    @Test
    public void everyCallbackTicksOnceAndReschedulesItself() {
        mTicker.start();
        mScheduler.runPending();

        assertEquals(1, mTicks);
        assertEquals("the next check is scheduled", 1, mScheduler.pendingCount());

        mScheduler.runPending();

        assertEquals(2, mTicks);
    }

    @Test
    public void stopRemovesThePendingCallbackAndStopsTicking() {
        mTicker.start();
        mTicker.stop();

        assertFalse(mTicker.isRunning());
        assertEquals(0, mScheduler.pendingCount());
        assertEquals(1, mScheduler.removals);

        mScheduler.runPending();

        assertEquals("a stopped ticker never ticks", 0, mTicks);
        assertEquals(0, mScheduler.pendingCount());
    }

    @Test
    public void aCallbackThatRacedWithStopDoesNotReschedule() {
        mTicker.start();
        Runnable pending = mScheduler.pending.get(0);

        mTicker.stop();
        pending.run(); // the scheduler had already dispatched it

        assertEquals(0, mTicks);
        assertEquals(0, mScheduler.pendingCount());
    }

    @Test
    public void restartingAfterStopWorksAgain() {
        mTicker.start();
        mTicker.stop();
        mTicker.start();

        assertTrue(mTicker.isRunning());
        assertEquals(1, mScheduler.pendingCount());
    }

    @Test
    public void aMissingSchedulerIsHarmless() {
        SubtitlePrefetchTicker ticker = new SubtitlePrefetchTicker(null, () -> mTicks++);

        ticker.start();

        assertFalse(ticker.isRunning());
        assertEquals(0, mTicks);
    }
}
