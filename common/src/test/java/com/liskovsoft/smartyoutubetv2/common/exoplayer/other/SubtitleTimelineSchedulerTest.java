package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * N1 regression: the request identity of the timeline fetch.
 *
 * <p>These cases pin the exact defect the device log exposed: a shared cancellation flag let a new
 * request resurrect an abandoned one, and repeated same-source events restarted the read.
 */
public class SubtitleTimelineSchedulerTest {
    @Test
    public void theSameSourceIsReusedInsteadOfRestarted() {
        SubtitleTimelineScheduler scheduler = new SubtitleTimelineScheduler();

        SubtitleTimelineScheduler.Request first = scheduler.begin(1, "key-a", "locator-a");
        SubtitleTimelineScheduler.Request second = scheduler.begin(1, "key-a", "locator-a");

        assertNull("the same identity must not start a second read", second);
        assertSame(first, scheduler.getInFlight());
        assertFalse("reusing an attempt must not cancel it", first.isCancelled());
    }

    @Test
    public void aDifferentSourceCancelsThePreviousAttempt() {
        SubtitleTimelineScheduler scheduler = new SubtitleTimelineScheduler();

        SubtitleTimelineScheduler.Request first = scheduler.begin(1, "key-a", "locator-a");
        SubtitleTimelineScheduler.Request second = scheduler.begin(1, "key-b", "locator-b");

        assertNotSame(first, second);
        assertTrue("the abandoned attempt must see its own cancellation", first.isCancelled());
        assertFalse(second.isCancelled());
        assertTrue(scheduler.isCurrent(second));
    }

    @Test
    public void aNewAttemptDoesNotResurrectTheCancellationOfTheOldOne() {
        SubtitleTimelineScheduler scheduler = new SubtitleTimelineScheduler();
        SubtitleTimelineScheduler.Request first = scheduler.begin(1, "key-a", "locator-a");

        assertTrue(scheduler.cancel());
        SubtitleTimelineScheduler.Request second = scheduler.begin(1, "key-a", "locator-a");

        assertNotSame(first, second);
        assertFalse("the replacement attempt starts uncancelled", second.isCancelled());
        assertTrue("the abandoned attempt stays cancelled", first.isCancelled());
    }

    @Test
    public void aSupersededAttemptCannotClearTheNewerRequestsSlot() {
        SubtitleTimelineScheduler scheduler = new SubtitleTimelineScheduler();
        SubtitleTimelineScheduler.Request first = scheduler.begin(1, "key-a", "locator-a");

        scheduler.cancel();
        SubtitleTimelineScheduler.Request second = scheduler.begin(1, "key-a", "locator-a");

        assertFalse("a late settle of the old attempt must be rejected", scheduler.settle(first));
        assertTrue("the new attempt is still the live one", scheduler.isCurrent(second));
        assertSame(second, scheduler.getInFlight());
    }

    @Test
    public void settlingReleasesTheSlotForAnExplicitLaterEvent() {
        SubtitleTimelineScheduler scheduler = new SubtitleTimelineScheduler();
        SubtitleTimelineScheduler.Request first = scheduler.begin(1, "key-a", "locator-a");

        assertTrue(scheduler.settle(first));
        assertNull(scheduler.getInFlight());

        SubtitleTimelineScheduler.Request retry = scheduler.begin(1, "key-a", "locator-a");

        assertNotSame(first, retry);
        assertFalse(retry.isCancelled());
    }

    @Test
    public void aNewMediaGenerationStartsANewAttemptForTheSameTrack() {
        SubtitleTimelineScheduler scheduler = new SubtitleTimelineScheduler();
        SubtitleTimelineScheduler.Request first = scheduler.begin(1, "key-a", "locator-a");

        SubtitleTimelineScheduler.Request second = scheduler.begin(2, "key-a", "locator-a");

        assertNotSame(first, second);
        assertTrue(first.isCancelled());
    }

    @Test
    public void nothingIsScheduledWithoutASourceOrALocator() {
        SubtitleTimelineScheduler scheduler = new SubtitleTimelineScheduler();

        assertNull(scheduler.begin(1, null, "locator-a"));
        assertNull(scheduler.begin(1, "key-a", null));
        assertNull(scheduler.getInFlight());
    }

    @Test
    public void thePrintableFormCarriesNoSourceOrLocator() {
        SubtitleTimelineScheduler.Request request =
                new SubtitleTimelineScheduler().begin(1, "secret-key", "https://example.com/secret");

        String printed = request.toString();

        assertFalse(printed.contains("secret-key"));
        assertFalse(printed.contains("example.com"));
        assertEquals(0, request.getId());
    }
}
