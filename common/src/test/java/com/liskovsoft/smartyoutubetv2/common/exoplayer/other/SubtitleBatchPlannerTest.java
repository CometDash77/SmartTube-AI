package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** T06 acceptance for window selection, budgets, dedupe and oversize handling. */
public class SubtitleBatchPlannerTest {
    private static String text(int length) {
        StringBuilder builder = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            builder.append('a');
        }

        return builder.toString();
    }

    private static SubtitleEvent event(long timeUs, String... texts) {
        List<com.google.android.exoplayer2.text.Cue> cues = new ArrayList<>();

        for (String value : texts) {
            cues.add(new com.google.android.exoplayer2.text.Cue(value));
        }

        return new SubtitleEvent(timeUs, cues);
    }

    /** Frames with distinct, non-interfering texts (the original-text rule never strips them). */
    private static SubtitleTimeline timeline(int itemCount, int textLength) {
        List<SubtitleEvent> events = new ArrayList<>();

        for (int i = 0; i < itemCount; i++) {
            // Texts differ by their trailing marker so consecutive entries never share a prefix.
            events.add(event(i * 5_000_000L, text(textLength - 1) + (char) ('a' + (i % 26))));
        }

        return new SubtitleTimelineBuilder().build(events, itemCount * 5_000_000L);
    }

    @Test
    public void firstBatchIsSmallAndLaterBatchesUseTheFullCeiling() {
        SubtitleBatchPlanner planner = new SubtitleBatchPlanner();
        SubtitleTimeline timeline = timeline(40, 5);

        SubtitleBatch first = planner.nextBatch(timeline, 0, true);
        assertNotNull(first);
        assertTrue("first batch must be small", first.getItems().size() <= SubtitleBatchPlanner.FIRST_BATCH_ITEMS);
        planner.markQueued(first);

        SubtitleBatch second = planner.nextBatch(timeline, 0, false);
        assertNotNull(second);
        assertTrue(second.getItems().size() <= SubtitleBatchPlanner.MAX_ITEMS);
        assertTrue(second.getItems().size() > first.getItems().size());
    }

    @Test
    public void queuedAndFinishedItemsAreNeverPlannedAgain() {
        SubtitleBatchPlanner planner = new SubtitleBatchPlanner();
        SubtitleTimeline timeline = timeline(8, 5); // two small first-batches cover the window

        SubtitleBatch first = planner.nextBatch(timeline, 0, true);
        planner.markQueued(first);

        SubtitleBatch second = planner.nextBatch(timeline, 0, true);
        assertNotNull(second);

        for (String id : first.getItemIds()) {
            assertFalse("queued item replanned", second.getItemIds().contains(id));
        }

        planner.markQueued(second);
        planner.markFinished(second, true);

        assertNull("nothing new left in the window", planner.nextBatch(timeline, 0, false));
    }

    @Test
    public void itemBudgetStopsAtSixThousandCodePoints() {
        SubtitleBatchPlanner planner = new SubtitleBatchPlanner();
        SubtitleTimeline timeline = timeline(30, 1_000);

        SubtitleBatch batch = planner.nextBatch(timeline, 0, false);

        assertNotNull(batch);
        assertTrue(batch.getCodePoints() <= SubtitleBatchPlanner.MAX_CODE_POINTS);
        assertEquals(6, batch.getItems().size()); // 6 x 1,000 fits exactly, the 7th would exceed
    }

    @Test
    public void emojiAndMultilineTextAreCountedAsCodePoints() {
        SubtitleBatchPlanner planner = new SubtitleBatchPlanner();
        SubtitleTimeline timeline = new SubtitleTimelineBuilder().build(
                Collections.singletonList(event(0, "\ud83d\ude00 Line one\nLine two")), 1_000_000);

        SubtitleBatch batch = planner.nextBatch(timeline, 0, true);

        assertNotNull(batch);
        assertEquals(1, batch.getItems().size());
        assertEquals("\ud83d\ude00 Line one\nLine two".codePointCount(0, "\ud83d\ude00 Line one\nLine two".length()),
                batch.getCodePoints() - SubtitleBatch.codePoints(batch.getContextBefore().isEmpty() ? "" : batch.getContextBefore().get(0)));
    }

    @Test
    public void oversizeItemIsMarkedAndDoesNotBlockLaterItems() {
        SubtitleBatchPlanner planner = new SubtitleBatchPlanner();
        String oversizeText = text(SubtitleBatchPlanner.MAX_ITEM_CODE_POINTS + 1);
        SubtitleTimeline timeline = new SubtitleTimelineBuilder().build(
                java.util.Arrays.asList(event(0, oversizeText), event(5_000_000L, "Short one"), event(10_000_000L, "Short two")),
                20_000_000L);

        SubtitleBatch batch = planner.nextBatch(timeline, 0, false);

        assertNotNull(batch);
        assertEquals(2, batch.getItems().size());
        assertTrue(planner.isOversize(batch == null ? null : firstOversizeId(timeline)));
        assertNull("an oversize-only window yields nothing", planner.nextBatch(new SubtitleTimelineBuilder().build(
                Collections.singletonList(event(0, oversizeText)), 1_000_000), 0, true));
    }

    private static String firstOversizeId(SubtitleTimeline timeline) {
        return timeline.getFrames().get(0).getItems().get(0).getItemId();
    }

    @Test
    public void windowIsSixtySecondsAndGapsDoNotHideLaterItems() {
        SubtitleBatchPlanner planner = new SubtitleBatchPlanner();
        SubtitleTimeline timeline = new SubtitleTimelineBuilder().build(
                java.util.Arrays.asList(event(0, "Now"), event(1_000_000), event(30_000_000L, "Next"),
                        event(90_000_000L, "Far")), 120_000_000L);

        SubtitleBatch batch = planner.nextBatch(timeline, 0, false);

        assertNotNull(batch);
        assertEquals(2, batch.getItems().size());
        assertEquals("Now", batch.getItems().get(0).getText());
        assertEquals("Next", batch.getItems().get(1).getText());
    }

    @Test
    public void contextIsOptionalAndNeverBreaksTheBudget() {
        SubtitleBatchPlanner planner = new SubtitleBatchPlanner();
        SubtitleTimeline timeline = timeline(30, 1_800);

        SubtitleBatch batch = planner.nextBatch(timeline, 0, false);

        assertNotNull(batch);
        assertTrue(batch.getCodePoints() <= SubtitleBatchPlanner.MAX_CODE_POINTS);
        assertTrue(batch.getContextBefore().size() <= SubtitleBatchPlanner.CONTEXT_BEFORE);
        assertTrue(batch.getContextAfter().size() <= SubtitleBatchPlanner.CONTEXT_AFTER);
    }

    @Test
    public void resetClearsPendingState() {
        SubtitleBatchPlanner planner = new SubtitleBatchPlanner();
        SubtitleTimeline timeline = timeline(10, 5);
        planner.markQueued(planner.nextBatch(timeline, 0, true));

        planner.reset();

        assertEquals(0, planner.getPendingCount());
        assertNotNull(planner.nextBatch(timeline, 0, true));
    }
}
