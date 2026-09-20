package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import com.google.android.exoplayer2.C;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** T13 acceptance for the SRT text of one export: boundaries, Unicode and partial translations. */
public class SubtitleSrtFormatterTest {
    private static final String EOL = "\r\n";

    private static SubtitleItem item(String id, String text) {
        return new SubtitleItem(id, text);
    }

    private static SubtitleFrame frame(long startUs, long endUs, SubtitleItem... items) {
        return new SubtitleFrame(startUs, endUs, Arrays.asList(items));
    }

    private static SubtitleTimeline timeline(SubtitleFrame... frames) {
        return new SubtitleTimeline(Arrays.asList(frames), "fingerprint");
    }

    private static Map<String, String> translations(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();

        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }

        return map;
    }

    @Test
    public void timestampFormattingCoversTheBoundaries() {
        assertEquals("00:00:00,000", SubtitleSrtFormatter.formatTimestamp(0));
        assertEquals("00:00:00,001", SubtitleSrtFormatter.formatTimestamp(1_000));
        assertEquals("00:00:01,500", SubtitleSrtFormatter.formatTimestamp(1_500_000));
        assertEquals("00:01:01,001", SubtitleSrtFormatter.formatTimestamp(61_001_000));
        assertEquals("01:00:00,000", SubtitleSrtFormatter.formatTimestamp(3_600_000_000L));
        assertEquals("00:00:00,000", SubtitleSrtFormatter.formatTimestamp(-5_000));
    }

    @Test
    public void originalSrtUsesTheSourceTimeline() {
        SubtitleTimeline timeline = timeline(
                frame(0, 1_000_000, item("a", "Hello")),
                frame(1_000_000, 2_500_000, item("b", "World")));

        String srt = SubtitleSrtFormatter.formatOriginal(timeline);

        assertEquals("1" + EOL + "00:00:00,000 --> 00:00:01,000" + EOL + "Hello" + EOL + EOL
                + "2" + EOL + "00:00:01,000 --> 00:00:02,500" + EOL + "World" + EOL + EOL, srt);
    }

    @Test
    public void anEmptyFrameIsAClearingBoundaryNotACue() {
        SubtitleTimeline timeline = timeline(
                frame(0, 1_000_000, item("a", "First")),
                frame(1_000_000, 2_000_000),
                frame(2_000_000, 3_000_000, item("c", "Second")));

        String srt = SubtitleSrtFormatter.formatOriginal(timeline);

        assertTrue(srt.contains("00:00:00,000 --> 00:00:01,000"));
        assertTrue(srt.contains("00:00:02,000 --> 00:00:03,000"));
        assertEquals("no cue is produced for the clearing frame", 2, SubtitleSrtFormatter.measure(timeline, null).getFrames());
        assertTrue("cue numbering restarts at 1", srt.startsWith("1" + EOL));
        assertTrue("the second cue keeps index 2", srt.contains("2" + EOL + "00:00:02,000 --> 00:00:03,000"));
    }

    @Test
    public void aZeroLengthFrameIsNotWritten() {
        SubtitleTimeline timeline = timeline(
                frame(0, 0, item("a", "Invisible")),
                frame(1_000_000, 2_000_000, item("b", "Visible")));

        String srt = SubtitleSrtFormatter.formatOriginal(timeline);

        assertFalse(srt.contains("Invisible"));
        assertTrue(srt.startsWith("1" + EOL));
        assertTrue(srt.contains("Visible"));
    }

    @Test
    public void theLastCueEndIsEstimatedFromTheLongestKnownCue() {
        SubtitleTimeline timeline = timeline(
                frame(0, 2_000_000, item("a", "One")),
                frame(5_000_000, C.TIME_UNSET, item("b", "Two")));

        String srt = SubtitleSrtFormatter.formatOriginal(timeline);

        assertTrue("the last cue borrows the two seconds of the previous one",
                srt.contains("00:00:05,000 --> 00:00:07,000"));
    }

    @Test
    public void anUnknownEndWithoutAPreviousCueFallsBackToTheDefaultEstimate() {
        SubtitleTimeline timeline = timeline(frame(1_000_000, C.TIME_UNSET, item("a", "Only")));

        String srt = SubtitleSrtFormatter.formatOriginal(timeline);

        assertTrue(srt.contains("00:00:01,000 --> 00:00:04,000"));
    }

    @Test
    public void multiLineAndUnicodeTextSurvivesTheRoundTrip() {
        SubtitleTimeline timeline = timeline(frame(0, 1_000_000,
                item("a", "\u7b2c\u4e00\u884c\n\u7b2c\u4e8c\u884c"), item("b", "emoji \uD83D\uDE00")));

        String srt = SubtitleSrtFormatter.formatOriginal(timeline);

        assertTrue(srt.contains("\u7b2c\u4e00\u884c\n\u7b2c\u4e8c\u884c"));
        assertTrue(srt.contains("emoji \uD83D\uDE00"));
    }

    @Test
    public void translatedSrtFallsBackToTheOriginalForMissingEntries() {
        SubtitleTimeline timeline = timeline(frame(0, 1_000_000,
                item("a", "One"), item("b", "Two")));

        String srt = SubtitleSrtFormatter.formatTranslated(timeline, translations("a", "\u4e00"));

        assertTrue(srt.contains("\u4e00"));
        assertTrue("the untranslated item keeps the original line", srt.contains("Two"));
    }

    @Test
    public void bilingualSrtKeepsTheOriginalAboveTheTranslation() {
        SubtitleTimeline timeline = timeline(frame(0, 1_000_000,
                item("a", "One"), item("b", "Two")));

        String srt = SubtitleSrtFormatter.formatBilingual(timeline, translations("a", "\u4e00"));

        assertTrue(srt.contains("One\n\u4e00"));
        assertFalse("a missing translation never adds a placeholder", srt.contains("Two\n"));
        assertTrue(srt.contains("Two"));
    }

    @Test
    public void aBlankTranslationIsTreatedAsMissing() {
        SubtitleTimeline timeline = timeline(frame(0, 1_000_000, item("a", "One")));

        String srt = SubtitleSrtFormatter.formatBilingual(timeline, translations("a", "   "));

        assertTrue(srt.contains("One"));
        assertFalse(srt.contains("One\n"));
    }

    @Test
    public void coverageCountsDistinctItemsAcrossFrames() {
        SubtitleTimeline timeline = timeline(
                frame(0, 1_000_000, item("a", "One"), item("b", "Two")),
                frame(1_000_000, 2_000_000, item("a", "One"), item("c", "Three")),
                frame(2_000_000, 3_000_000));

        SubtitleSrtFormatter.Coverage coverage =
                SubtitleSrtFormatter.measure(timeline, translations("a", "\u4e00", "z", "unused"));

        assertEquals(2, coverage.getFrames());
        assertEquals(3, coverage.getItems());
        assertEquals(1, coverage.getTranslatedItems());
        assertEquals(2, coverage.getMissingItems());
        assertEquals(33, coverage.getCoveragePercent());
    }

    @Test
    public void noTimelineMeansNoText() {
        assertEquals("", SubtitleSrtFormatter.formatOriginal(null));
        assertEquals("", SubtitleSrtFormatter.formatOriginal(
                new SubtitleTimeline(Collections.<SubtitleFrame>emptyList(), "fp")));
        assertEquals(0, SubtitleSrtFormatter.measure(null, null).getItems());
        assertEquals(0, SubtitleSrtFormatter.measure(null, null).getCoveragePercent());
    }
}
