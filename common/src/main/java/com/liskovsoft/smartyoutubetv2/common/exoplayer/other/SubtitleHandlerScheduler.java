package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

/**
 * Production scheduler of the prefetch clock: the application's existing main-looper handler.
 *
 * <p>It reuses the project's {@link Utils} helpers instead of creating a private Handler, so the
 * periodic check shares the queue the player already uses and a stopped loop leaves nothing behind:
 * {@code Utils.postDelayed} removes a previous callback before posting.
 */
public class SubtitleHandlerScheduler implements SubtitlePrefetchTicker.Scheduler {
    @Override
    public void postDelayed(Runnable task, long delayMs) {
        Utils.postDelayed(task, delayMs);
    }

    @Override
    public void removeCallbacks(Runnable task) {
        Utils.removeCallbacks(task);
    }
}
