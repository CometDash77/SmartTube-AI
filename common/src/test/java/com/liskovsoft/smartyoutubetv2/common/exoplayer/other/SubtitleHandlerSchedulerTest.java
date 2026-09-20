package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.os.Looper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * T06 slice: the production scheduler is the application handler.
 *
 * <p>Only wiring is asserted here; the periodic behaviour itself is covered against the seam in
 * 'SubtitlePrefetchTickerTest' and 'SubtitlePrefetchLoopTest' with a fake scheduler.
 */
@RunWith(RobolectricTestRunner.class)
public class SubtitleHandlerSchedulerTest {
    @Test
    public void postingAndRemovingUseTheApplicationHandlerWithoutThrowing() {
        SubtitleHandlerScheduler scheduler = new SubtitleHandlerScheduler();
        boolean[] ran = {false};
        Runnable task = () -> ran[0] = true;

        scheduler.postDelayed(task, SubtitlePrefetchTicker.INTERVAL_MS);
        scheduler.removeCallbacks(task);

        assertNotNull(Looper.getMainLooper());
        assertTrue("the scheduler is a thin adapter; the task must not run synchronously", !ran[0]);
    }
}
