package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

/**
 * Periodic clock of the AI subtitle prefetch, on the player's existing scheduler.
 *
 * <p>One second is the plan's initial interval. The ticker owns exactly one pending callback: starting
 * it twice does not create a second timer, and stopping it always removes the pending callback, which
 * is what the "no residual timers" requirement needs on release, on subtitles-off and when AI is
 * switched off.
 */
public class SubtitlePrefetchTicker {
    public static final long INTERVAL_MS = 1_000;

    /** Scheduler seam of the player (for example the existing Handler/Utils helpers). */
    public interface Scheduler {
        void postDelayed(Runnable task, long delayMs);

        void removeCallbacks(Runnable task);
    }

    public interface Tick {
        void onTick();
    }

    private final Scheduler mScheduler;
    private final Tick mTick;
    private final Runnable mTask = this::runTick;
    private boolean mRunning;

    public SubtitlePrefetchTicker(Scheduler scheduler, Tick tick) {
        mScheduler = scheduler;
        mTick = tick;
    }

    /** Starts the periodic checks; a second start while running is ignored. */
    public void start() {
        if (mRunning || mScheduler == null) {
            return;
        }

        mRunning = true;
        mScheduler.postDelayed(mTask, INTERVAL_MS);
    }

    /** Stops the periodic checks and removes the pending callback. */
    public void stop() {
        if (!mRunning) {
            return;
        }

        mRunning = false;

        if (mScheduler != null) {
            mScheduler.removeCallbacks(mTask);
        }
    }

    public boolean isRunning() {
        return mRunning;
    }

    private void runTick() {
        if (!mRunning) {
            return; // a callback that raced with stop() must not run or reschedule
        }

        if (mTick != null) {
            mTick.onTick();
        }

        if (mRunning) {
            mScheduler.postDelayed(mTask, INTERVAL_MS);
        }
    }
}
