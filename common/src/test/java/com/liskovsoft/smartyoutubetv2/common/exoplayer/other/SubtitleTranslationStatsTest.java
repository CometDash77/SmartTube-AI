package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** T10 slice: the session counters the plan asks the integration round to record. */
public class SubtitleTranslationStatsTest {
    @Test
    public void aFreshSessionHasNoNumbersToShow() {
        SubtitleTranslationStats stats = new SubtitleTranslationStats();

        assertTrue(stats.isEmpty());
        assertEquals(0, stats.getRequests());
        assertEquals(0, stats.getDeliveredItems());
    }

    @Test
    public void countersAccumulate() {
        SubtitleTranslationStats stats = new SubtitleTranslationStats();

        stats.onRequestStarted();
        stats.onBatchDelivered(4);
        stats.onRequestStarted();
        stats.onBatchDelivered(6);
        stats.onBatchFailed();
        stats.onBatchCancelled();

        assertEquals(2, stats.getRequests());
        assertEquals(10, stats.getDeliveredItems());
        assertEquals(1, stats.getFailedBatches());
        assertEquals(1, stats.getCancelledBatches());
        assertFalse(stats.isEmpty());
    }

    @Test
    public void negativeDeliveryIsIgnored() {
        SubtitleTranslationStats stats = new SubtitleTranslationStats();

        stats.onBatchDelivered(-3);

        assertEquals(0, stats.getDeliveredItems());
    }

    @Test
    public void cancellationIsNotCountedAsFailure() {
        SubtitleTranslationStats stats = new SubtitleTranslationStats();

        stats.onBatchCancelled();
        stats.onBatchCancelled();

        assertEquals(0, stats.getFailedBatches());
        assertEquals(2, stats.getCancelledBatches());
    }

    @Test
    public void resetClearsTheSessionNumbers() {
        SubtitleTranslationStats stats = new SubtitleTranslationStats();
        stats.onRequestStarted();
        stats.onBatchDelivered(2);

        stats.reset();

        assertTrue(stats.isEmpty());
        assertEquals(0, stats.getDeliveredItems());
    }

    @Test
    public void thePrintedFormCarriesCountersOnly() {
        SubtitleTranslationStats stats = new SubtitleTranslationStats();
        stats.onRequestStarted();

        String printed = stats.toString();

        assertTrue(printed.contains("requests=1"));
        assertFalse(printed.contains("sk-"));
        assertFalse(printed.contains("translation="));
    }
}
