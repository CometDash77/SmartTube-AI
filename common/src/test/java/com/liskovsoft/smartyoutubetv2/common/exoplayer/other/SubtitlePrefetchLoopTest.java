package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** T06 slice: the periodic clock drives the pipeline, idle ticks only repaint. */
public class SubtitlePrefetchLoopTest {
    private static class FakeScheduler implements SubtitlePrefetchTicker.Scheduler {
        private final List<Runnable> pending = new ArrayList<>();

        @Override
        public void postDelayed(Runnable task, long delayMs) {
            pending.add(task);
        }

        @Override
        public void removeCallbacks(Runnable task) {
            pending.remove(task);
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

    private static class FakePipeline implements SubtitlePrefetchLoop.Pipeline {
        private boolean startsCalls;
        private final List<Long> ticks = new ArrayList<>();
        private int repaints;

        @Override
        public boolean onTick(long positionMs) {
            ticks.add(positionMs);
            return startsCalls;
        }

        @Override
        public boolean applyCurrentFrame() {
            repaints++;
            return true;
        }
    }

    private FakeScheduler mScheduler;
    private FakePipeline mPipeline;
    private SubtitlePrefetchLoop mLoop;
    private long mPositionMs = 12_345;

    @Before
    public void setUp() {
        mScheduler = new FakeScheduler();
        mPipeline = new FakePipeline();
        mLoop = new SubtitlePrefetchLoop(mScheduler, mPipeline, () -> mPositionMs);
    }

    @Test
    public void startSchedulesTheClockAndTicksForwardThePosition() {
        mLoop.start();

        assertTrue(mLoop.isRunning());
        assertEquals(1, mScheduler.pendingCount());

        mScheduler.runPending();

        assertEquals(1, mPipeline.ticks.size());
        assertEquals(12_345L, (long) mPipeline.ticks.get(0));
        assertEquals("a second check is scheduled", 1, mScheduler.pendingCount());
    }

    @Test
    public void anIdleTickRepaintsTheCurrentFrameFromTheCache() {
        mPipeline.startsCalls = false;
        mLoop.start();

        mScheduler.runPending();

        assertEquals(1, mPipeline.repaints);
    }

    @Test
    public void aTickThatStartedACallDoesNotRepaint() {
        mPipeline.startsCalls = true;
        mLoop.start();

        mScheduler.runPending();

        assertEquals(0, mPipeline.repaints);
    }

    @Test
    public void stopEndsTheClockAndLeavesNoPendingCallback() {
        mLoop.start();
        mLoop.stop();

        assertFalse(mLoop.isRunning());
        assertEquals(0, mScheduler.pendingCount());

        mScheduler.runPending();

        assertEquals(0, mPipeline.ticks.size());
    }

    @Test
    public void tickingWithoutAPipelineIsHarmless() {
        SubtitlePrefetchLoop loop = new SubtitlePrefetchLoop(mScheduler, null, null);

        assertFalse(loop.tick());
    }
}
