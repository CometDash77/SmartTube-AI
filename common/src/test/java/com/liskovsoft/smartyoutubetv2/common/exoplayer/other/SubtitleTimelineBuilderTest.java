package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.text.Cue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** T03 acceptance for the immutable, order-stable subtitle timeline. */
public class SubtitleTimelineBuilderTest {
    private static SubtitleEvent event(long timeUs, String... texts) {
        List<Cue> cues = new ArrayList<>();

        for (String text : texts) {
            cues.add(new Cue(text));
        }

        return new SubtitleEvent(timeUs, cues);
    }

    /** A clearing event: the decoder reports no cues at all. */
    private static SubtitleEvent event(long timeUs) {
        return new SubtitleEvent(timeUs, Collections.<Cue>emptyList());
    }

    private static List<String> texts(SubtitleFrame frame) {
        return frame.getTexts();
    }

    @Test
    public void buildsOneFramePerEventBoundaryAndKeepsClearingFrames() {
        SubtitleTimeline timeline = new SubtitleTimelineBuilder().build(
                Arrays.asList(event(0, "Hello"), event(1_000_000), event(2_000_000, "World")), 3_000_000);

        assertEquals(3, timeline.size());
        assertEquals(Arrays.asList("Hello"), texts(timeline.frameAt(0)));
        assertEquals(1_000_000, timeline.frameAt(999_999).getEndUs());
        assertTrue(timeline.frameAt(1_000_000).isEmpty()); // real clearing boundary, not a missing frame
        assertEquals(Arrays.asList("World"), texts(timeline.frameAt(2_000_000)));
        assertEquals(3_000_000, timeline.frameAt(2_500_000).getEndUs());
    }

    @Test
    public void unknownEndStaysUnsetInsteadOfBeingGuessed() {
        SubtitleTimeline timeline = new SubtitleTimelineBuilder().build(
                Collections.singletonList(event(1_000_000, "Hello")), C.TIME_UNSET);

        assertEquals(C.TIME_UNSET, timeline.frameAt(1_000_000).getEndUs());
    }

    @Test
    public void keepsTheItemIdWhileTextStaysInTheSameSlot() {
        SubtitleTimeline timeline = new SubtitleTimelineBuilder().build(
                Arrays.asList(event(0, "Alpha", "Bravo"), event(1_000_000, "Alpha", "Charlie")), 2_000_000);

        SubtitleItem stable = timeline.frameAt(0).getItems().get(0);
        SubtitleItem changedNeighbour = timeline.frameAt(0).getItems().get(1);
        SubtitleItem stillStable = timeline.frameAt(1_000_000).getItems().get(0);
        SubtitleItem newNeighbour = timeline.frameAt(1_000_000).getItems().get(1);

        assertEquals("Alpha", stillStable.getText());
        assertEquals(stable.getItemId(), stillStable.getItemId()); // overlapping cue change must not retranslate
        assertNotEquals(changedNeighbour.getItemId(), newNeighbour.getItemId());
    }

    @Test
    public void repeatedTextAfterAGapIsANewEvent() {
        SubtitleTimeline timeline = new SubtitleTimelineBuilder().build(
                Arrays.asList(event(0, "Alpha", "Bravo"), event(1_000_000), event(2_000_000, "Alpha", "Bravo")), 3_000_000);

        assertTrue(timeline.frameAt(1_000_000).isEmpty());
        assertEquals(Arrays.asList("Alpha", "Bravo"), texts(timeline.frameAt(2_000_000)));
        assertNotEquals(timeline.frameAt(0).getItems().get(0).getItemId(),
                timeline.frameAt(2_000_000).getItems().get(0).getItemId());
    }

    @Test
    public void appliesTheSameOriginalTextRuleTheScreenUses() {
        // Raw TTML streaming form: the previous line is repeated in the next cue.
        SubtitleTimeline timeline = new SubtitleTimelineBuilder().build(
                Arrays.asList(event(0, "Line one"), event(1_000_000, "Line one\nLine two")), 2_000_000);

        assertEquals(Arrays.asList("Line one"), texts(timeline.frameAt(0)));
        assertEquals(Arrays.asList("Line two"), texts(timeline.frameAt(1_000_000)));
    }

    @Test
    public void doesNotAssumeMonotonicEndTimesAndSortsEvents() {
        SubtitleTimeline timeline = new SubtitleTimelineBuilder().build(
                Arrays.asList(event(2_000_000, "Second"), event(0, "First")), 3_000_000);

        assertEquals(Arrays.asList("First"), texts(timeline.frameAt(0)));
        assertEquals(2_000_000, timeline.frameAt(0).getEndUs());
        assertEquals(Arrays.asList("Second"), texts(timeline.frameAt(2_000_000)));
    }

    @Test
    public void frameLookupBeforeTheFirstEventIsNull() {
        SubtitleTimeline timeline = new SubtitleTimelineBuilder().build(
                Collections.singletonList(event(1_000_000, "Hello")), 2_000_000);

        assertNull(timeline.frameAt(999_999));
        assertNotNull(timeline.frameAt(1_000_000));
    }

    @Test
    public void windowUsesEndGreaterThanPositionAndStartBeforeTheAheadLimit() {
        SubtitleTimeline timeline = new SubtitleTimelineBuilder().build(
                Arrays.asList(event(0, "Now"), event(30_000_000, "Next"), event(90_000_000, "Far")), 120_000_000);

        List<SubtitleFrame> window = timeline.framesInWindow(0, 60_000_000);

        assertEquals(2, window.size());
        assertEquals(Arrays.asList("Now"), texts(window.get(0)));
        assertEquals(Arrays.asList("Next"), texts(window.get(1)));

        // The frame that already ended before the position is not prefetched again.
        List<SubtitleFrame> later = timeline.framesInWindow(35_000_000, 60_000_000);
        assertEquals(2, later.size());
        assertEquals(Arrays.asList("Next"), texts(later.get(0)));
    }

    @Test
    public void fingerprintIsStableForEqualContentAndChangesWithText() {
        SubtitleTimeline first = new SubtitleTimelineBuilder().build(Collections.singletonList(event(0, "Hello")), 1_000_000);
        SubtitleTimeline same = new SubtitleTimelineBuilder().build(Collections.singletonList(event(0, "Hello")), 1_000_000);
        SubtitleTimeline different = new SubtitleTimelineBuilder().build(Collections.singletonList(event(0, "Hallo")), 1_000_000);

        assertEquals(first.getContentFingerprint(), same.getContentFingerprint());
        assertNotEquals(first.getContentFingerprint(), different.getContentFingerprint());
        assertFalse(first.isEmpty());
    }

    @Test
    public void emptyInputYieldsAnEmptyTimeline() {
        SubtitleTimeline timeline = new SubtitleTimelineBuilder().build(Collections.<SubtitleEvent>emptyList(), C.TIME_UNSET);

        assertTrue(timeline.isEmpty());
        assertNull(timeline.frameAt(0));
        assertNotNull(timeline.getContentFingerprint());
    }

    @Test
    public void framesExposeTheirItemsWithoutSharingMutableState() {
        SubtitleTimeline timeline = new SubtitleTimelineBuilder().build(Collections.singletonList(event(0, "Hello")), 1_000_000);
        List<SubtitleItem> items = timeline.frameAt(0).getItems();

        assertSame(items, timeline.frameAt(0).getItems());
        assertEquals(1, items.size());
    }
}
