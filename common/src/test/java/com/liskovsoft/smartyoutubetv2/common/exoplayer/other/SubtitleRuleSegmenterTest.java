package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Plan 4.3 acceptance for the rule segmentation algorithm. */
public class SubtitleRuleSegmenterTest {
    private static final long SECOND = 1_000_000L;

    private static SubtitleItem item(String id, String text) {
        return new SubtitleItem(id, text);
    }

    private static SubtitleFrame frame(long startUs, long endUs, SubtitleItem... items) {
        return new SubtitleFrame(startUs, endUs, Arrays.asList(items));
    }

    private static SubtitleFrame clear(long startUs, long endUs) {
        return new SubtitleFrame(startUs, endUs, Collections.<SubtitleItem>emptyList());
    }

    private static SubtitleTimeline timeline(SubtitleFrame... frames) {
        return new SubtitleTimeline(Arrays.asList(frames), "fp");
    }

    private static List<String> texts(SubtitleRuleSegmenter.Result result) {
        List<String> texts = new ArrayList<>();

        for (SubtitleRuleSegmenter.Segment segment : result.getSegments()) {
            texts.add(segment.getSourceText());
        }

        return texts;
    }

    @Test
    public void sentenceEndPunctuationEndsASentence() {
        SubtitleRuleSegmenter.Result result = new SubtitleRuleSegmenter().segment(timeline(
                frame(0, SECOND, item("a", "Hello.")),
                frame(SECOND, 2 * SECOND, item("b", "World."))), "en");

        assertTrue(result.isDerived());
        assertEquals(Arrays.asList("Hello.", "World."), texts(result));
        assertEquals(Collections.singletonList("a"), result.getSegments().get(0).getMemberItemIds());
    }

    @Test
    public void shortLinesWithoutPunctuationBecomeOneSentence() {
        SubtitleRuleSegmenter.Result result = new SubtitleRuleSegmenter().segment(timeline(
                frame(0, SECOND, item("a", "Hello")),
                frame(SECOND, 2 * SECOND, item("b", "World")),
                frame(2 * SECOND, 3 * SECOND, item("c", "Again"))), "en");

        assertEquals(Collections.singletonList("Hello World Again"), texts(result));
        assertEquals(Arrays.asList("a", "b", "c"), result.getSegments().get(0).getMemberItemIds());
    }

    @Test
    public void abbreviationsAndDecimalsDoNotEndASentence() {
        assertFalse(SubtitleRuleSegmenter.endsSentence("See Dr."));
        assertFalse(SubtitleRuleSegmenter.endsSentence("Pi is 3.14"));
        assertTrue(SubtitleRuleSegmenter.endsSentence("Hello."));
        assertTrue(SubtitleRuleSegmenter.endsSentence("Hello.\u201d"));

        SubtitleRuleSegmenter.Result result = new SubtitleRuleSegmenter().segment(timeline(
                frame(0, SECOND, item("a", "See Dr.")),
                frame(SECOND, 2 * SECOND, item("b", "Smith tomorrow"))), "en");

        assertEquals(Collections.singletonList("See Dr. Smith tomorrow"), texts(result));
    }

    @Test
    public void aPauseLongerThanOneSecondIsABoundary() {
        SubtitleRuleSegmenter.Result result = new SubtitleRuleSegmenter().segment(timeline(
                frame(0, SECOND, item("a", "Hello")),
                frame(3 * SECOND, 4 * SECOND, item("b", "World"))), "en");

        assertEquals(Arrays.asList("Hello", "World"), texts(result));
    }

    @Test
    public void theDurationCapEndsASentence() {
        SubtitleRuleSegmenter.Result result = new SubtitleRuleSegmenter().segment(timeline(
                frame(0, 4 * SECOND, item("a", "One")),
                frame(4 * SECOND, 8 * SECOND, item("b", "Two")),
                frame(8 * SECOND, 12 * SECOND, item("c", "Three"))), "en");

        assertEquals(Arrays.asList("One Two", "Three"), texts(result));
        assertEquals(2, result.getSegments().get(0).getMemberItemIds().size());
    }

    @Test
    public void aClearingFrameIsAHardBoundaryAndStaysAClearingFrame() {
        SubtitleTimeline raw = timeline(
                frame(0, SECOND, item("a", "Hello")),
                clear(SECOND, 2 * SECOND),
                frame(2 * SECOND, 3 * SECOND, item("b", "World")));

        SubtitleRuleSegmenter.Result result = new SubtitleRuleSegmenter().segment(raw, "en");

        assertEquals(Arrays.asList("Hello", "World"), texts(result));
        assertEquals("the blank region is preserved", 3, result.getTimeline().getFrames().size());
        assertTrue("the middle frame clears the screen", result.getTimeline().getFrames().get(1).isEmpty());
    }

    @Test
    public void aMultiSlotFrameIsNeverMerged() {
        SubtitleRuleSegmenter.Result result = new SubtitleRuleSegmenter().segment(timeline(
                frame(0, 2 * SECOND, item("a", "Left side"), item("b", "Right side"))), "en");

        assertEquals(2, result.getSegmentCount());
        assertEquals(Arrays.asList("Left side", "Right side"), texts(result));
    }

    @Test
    public void nonSpeechAndSpeakerChangesAreTheirOwnSentence() {
        SubtitleRuleSegmenter.Result result = new SubtitleRuleSegmenter().segment(timeline(
                frame(0, SECOND, item("a", "[Music]")),
                frame(SECOND, 2 * SECOND, item("b", "Hello there")),
                frame(2 * SECOND, 3 * SECOND, item("c", "- And then?"))), "en");

        assertEquals(Arrays.asList("[Music]", "Hello there", "- And then?"), texts(result));
    }

    @Test
    public void oneStableItemAcrossConsecutiveFramesIsOneMember() {
        SubtitleRuleSegmenter.Result result = new SubtitleRuleSegmenter().segment(timeline(
                frame(0, SECOND, item("a", "Hello")),
                frame(SECOND, 2 * SECOND, item("a", "Hello")),
                frame(2 * SECOND, 3 * SECOND, item("b", "World."))), "en");

        assertEquals(1, result.getSegmentCount());
        assertEquals(Arrays.asList("a", "b"), result.getSegments().get(0).getMemberItemIds());
        assertEquals(0, result.getSegments().get(0).getStartUs());
        assertEquals(3 * SECOND, result.getSegments().get(0).getEndUs());
    }

    @Test
    public void identicalTextWithDifferentIdsIsNotDeduplicated() {
        SubtitleRuleSegmenter.Result result = new SubtitleRuleSegmenter().segment(timeline(
                frame(0, SECOND, item("a", "Hello")),
                frame(SECOND, 2 * SECOND, item("b", "Hello"))), "en");

        assertEquals(Arrays.asList("a", "b"), result.getSegments().get(0).getMemberItemIds());
    }

    @Test
    public void cjkIsJoinedWithoutASpaceAndKeepsItsClosingQuote() {
        SubtitleRuleSegmenter.Result result = new SubtitleRuleSegmenter().segment(timeline(
                frame(0, SECOND, item("a", "\u4f60\u597d")),
                frame(SECOND, 2 * SECOND, item("b", "\u4e16\u754c\u3002"))), "zh-Hans");

        assertEquals(Collections.singletonList("\u4f60\u597d\u4e16\u754c\u3002"), texts(result));
        assertTrue(SubtitleRuleSegmenter.endsSentence("\u4f60\u597d\u3002\u201d"));
    }

    @Test
    public void koreanKeepsItsSpaces() {
        SubtitleRuleSegmenter.Result result = new SubtitleRuleSegmenter().segment(timeline(
                frame(0, SECOND, item("a", "\uc548\ub155\ud558\uc138\uc694")),
                frame(SECOND, 2 * SECOND, item("b", "\ubc18\uac11\uc2b5\ub2c8\ub2e4"))), "ko");

        assertEquals(Collections.singletonList("\uc548\ub155\ud558\uc138\uc694 \ubc18\uac11\uc2b5\ub2c8\ub2e4"),
                texts(result));
    }

    @Test
    public void anOverLongSingleItemStaysWholeAndIsCounted() {
        StringBuilder long_ = new StringBuilder();

        for (int i = 0; i < 150; i++) {
            long_.append('x');
        }

        SubtitleRuleSegmenter.Result result = new SubtitleRuleSegmenter().segment(timeline(
                frame(0, 2 * SECOND, item("a", long_.toString()))), "en");

        assertEquals(1, result.getSegmentCount());
        assertEquals(1, result.getLongUnsplit());
        assertEquals(long_.toString(), texts(result).get(0));
    }

    @Test
    public void emojiIsCountedAsACodePointAndSurvivesTheJoin() {
        SubtitleRuleSegmenter.Result result = new SubtitleRuleSegmenter().segment(timeline(
                frame(0, SECOND, item("a", "\ud83d\ude00 Hello")),
                frame(SECOND, 2 * SECOND, item("b", "World."))), "en");

        assertEquals(Collections.singletonList("\ud83d\ude00 Hello World."), texts(result));
    }

    @Test
    public void theDerivedTimelineCoversTheSameSpanAsTheSnapshot() {
        SubtitleTimeline raw = timeline(
                frame(0, SECOND, item("a", "Hello")),
                frame(SECOND, 2 * SECOND, item("b", "World")));

        SubtitleRuleSegmenter.Result result = new SubtitleRuleSegmenter().segment(raw, "en");
        SubtitleTimeline derived = result.getTimeline();

        assertTrue(result.isDerived());
        assertNull(result.getFallback());
        assertEquals(raw.getFrames().get(0).getStartUs(), derived.getFrames().get(0).getStartUs());
        assertEquals(raw.getFrames().get(raw.size() - 1).getEndUs(),
                derived.getFrames().get(derived.size() - 1).getEndUs());
        assertEquals("the raw snapshot is untouched", 2, raw.size());
        assertNotNull(derived.frameAt(SECOND));
    }

    @Test
    public void anEmptySnapshotFallsBackToTheRawTimeline() {
        SubtitleRuleSegmenter.Result result = new SubtitleRuleSegmenter().segment(
                new SubtitleTimeline(Collections.<SubtitleFrame>emptyList(), "fp"), "en");

        assertFalse(result.isDerived());
        assertEquals(SubtitleRuleSegmenter.FAILURE_NO_FRAMES, result.getFallback());
        assertEquals(0, result.getRuleVersion());
    }

    @Test
    public void anUnknownEndFallsBackInsteadOfInventingTiming() {
        SubtitleRuleSegmenter.Result result = new SubtitleRuleSegmenter().segment(timeline(
                frame(0, com.google.android.exoplayer2.C.TIME_UNSET, item("a", "Hello"))), "en");

        assertFalse(result.isDerived());
        assertEquals(SubtitleRuleSegmenter.FAILURE_INVALID, result.getFallback());
    }
}
