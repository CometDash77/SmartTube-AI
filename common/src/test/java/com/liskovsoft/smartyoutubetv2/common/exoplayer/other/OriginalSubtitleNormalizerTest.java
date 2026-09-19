package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import com.google.android.exoplayer2.text.Cue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;

/**
 * Characterization baseline (T01) for the original subtitle path that the player already had.
 *
 * <p>These expectations describe what the player drew before the AI feature existed, verified
 * against the pre-refactor implementation. They must keep passing when AI translation is off, and
 * they pin the incremental scrolling state that the prefetch code must not disturb.
 */
public class OriginalSubtitleNormalizerTest {
    private static List<Cue> cues(String... texts) {
        List<Cue> result = new ArrayList<>();

        for (String text : texts) {
            result.add(new Cue(text));
        }

        return result;
    }

    private static List<String> texts(List<Cue> cues) {
        List<String> result = new ArrayList<>();

        for (Cue cue : cues) {
            result.add(cue.text == null ? null : cue.text.toString());
        }

        return result;
    }

    @Test
    public void plainCueIsDisplayedUnchanged() {
        assertEquals(Arrays.asList("Hello"), texts(new OriginalSubtitleNormalizer().normalize(cues("Hello"))));
    }

    @Test
    public void displayedCueIsRebuiltAndCenteredByDefault() {
        List<Cue> result = new OriginalSubtitleNormalizer().normalize(cues("Hello"));

        assertNull(result.get(0).textAlignment); // no alignment -> SubtitleView centers it
        assertNotSame("normalize must not hand the decoder cue back to the view", cues("Hello").get(0), result.get(0));
    }

    @Test
    public void appendedLineDropsTheAlreadyDisplayedText() {
        OriginalSubtitleNormalizer normalizer = new OriginalSubtitleNormalizer();

        assertEquals(Arrays.asList("Hello"), texts(normalizer.normalize(cues("Hello"))));
        assertEquals(Arrays.asList(" world"), texts(normalizer.normalize(cues("Hello world"))));
    }

    @Test
    public void scrollingAsrPartialLinesOnlyUpdateTheBuffer() {
        OriginalSubtitleNormalizer normalizer = new OriginalSubtitleNormalizer();

        assertEquals(Collections.emptyList(), texts(normalizer.normalize(cues("Hello\n"))));
        assertEquals(Collections.emptyList(), texts(normalizer.normalize(cues("Hello world\n"))));
        assertEquals(Arrays.asList("Hello world"), texts(normalizer.normalize(cues("Hello world"))));
    }

    @Test
    public void trailingSpaceIsTreatedLikeTheVttScrollingForm() {
        OriginalSubtitleNormalizer normalizer = new OriginalSubtitleNormalizer();

        assertEquals(Collections.emptyList(), texts(normalizer.normalize(cues("Hello world "))));
        // The buffered text keeps the trailing space, so the completed line is not stripped.
        assertEquals(Arrays.asList("Hello world"), texts(normalizer.normalize(cues("Hello world"))));
    }

    @Test
    public void ttmlTwoLineCueDropsTheBufferedPreviousLine() {
        OriginalSubtitleNormalizer normalizer = new OriginalSubtitleNormalizer();

        assertEquals(Arrays.asList("Line one"), texts(normalizer.normalize(cues("Line one"))));
        assertEquals(Arrays.asList("Line two"), texts(normalizer.normalize(cues("Line one\nLine two"))));
    }

    @Test
    public void ttmlTwoLineCueWithoutBufferedPreviousLineIsDisplayedVerbatim() {
        assertEquals(Arrays.asList("Line one\nLine two"),
                texts(new OriginalSubtitleNormalizer().normalize(cues("Line one\nLine two"))));
    }

    @Test
    public void newSentenceAfterScrollingBufferIsNotStripped() {
        OriginalSubtitleNormalizer normalizer = new OriginalSubtitleNormalizer();

        assertEquals(Collections.emptyList(), texts(normalizer.normalize(cues("Hello world\n"))));
        assertEquals(Arrays.asList("Totally different"), texts(normalizer.normalize(cues("Totally different"))));
    }

    @Test
    public void resetDropsTheCarriedScrollingText() {
        OriginalSubtitleNormalizer normalizer = new OriginalSubtitleNormalizer();
        assertEquals(Arrays.asList("Line one"), texts(normalizer.normalize(cues("Line one"))));

        normalizer.reset();

        assertEquals(Arrays.asList("Line one again"), texts(normalizer.normalize(cues("Line one again"))));
    }

    @Test
    public void emptyCueListClearsTheScreen() {
        assertEquals(Collections.emptyList(), texts(new OriginalSubtitleNormalizer().normalize(Collections.<Cue>emptyList())));
        assertEquals(Collections.emptyList(), texts(new OriginalSubtitleNormalizer().normalize(null)));
    }

    @Test
    public void multipleCuesProduceOneDisplayableCueEach() {
        OriginalSubtitleNormalizer normalizer = new OriginalSubtitleNormalizer();

        assertEquals(Arrays.asList("First", "Second"), texts(normalizer.normalize(cues("First", "Second"))));
    }

    @Test
    public void inputListIsNotReusedOrMutated() {
        List<Cue> input = cues("Hello");
        List<Cue> result = new OriginalSubtitleNormalizer().normalize(input);

        assertEquals(1, input.size());
        assertNotSame(input, result);
    }

    /**
     * Differential check: the class above must return exactly what the removed inline code returned
     * for the same cue stream. The former implementation is copied verbatim here on purpose.
     */
    @Test
    public void matchesTheFormerInlineImplementationForDecoderShapedCueStreams() {
        String[][] streams = {
                {"Hello", "Hello world", "Next sentence"},
                {"Hello\n", "Hello world\n", "Hello world", "More"},
                {"Line one", "Line one\nLine two", "Line two", "Line two\nLine three"},
                {"A", "B", "A", "A\nB"},
                {"Hello world ", "Hello world", "Hello world again"},
                {"First", "Second", "", "Third"},
                {"Line one\nLine two", "Line two", "New"},
                {"Overlap one", "Overlap one", "Overlap two"},
                {"\u53e5\u5b50\u4e00", "\u53e5\u5b50\u4e00\u53e5\u5b50\u4e8c", "\u53e5\u5b50\u4e00\u53e5\u5b50\u4e8c"},
                {"Emoji \ud83d\ude00", "Emoji \ud83d\ude00 is here"},
                {"One", "One", "One two", "One two three", "Two three"}
        };

        for (String[] stream : streams) {
            OriginalSubtitleNormalizer normalizer = new OriginalSubtitleNormalizer();
            FormerInlineImplementation former = new FormerInlineImplementation();

            for (String text : stream) {
                assertEquals("stream " + Arrays.toString(stream),
                        former.normalize(cues(text)), texts(normalizer.normalize(cues(text))));
            }
        }
    }

    /** Verbatim copy of the removed {@code SubtitleManager.forceCenterAlignment} text handling. */
    private static class FormerInlineImplementation {
        private CharSequence subsBuffer;

        List<String> normalize(List<Cue> cues) {
            List<Cue> result = new ArrayList<>();

            for (Cue cue : cues) {
                // Autogenerated subs repeated lines fix
                final String textStr = cue.text.toString();
                if (com.liskovsoft.sharedutils.helpers.Helpers.endsWithAny(textStr, "\n", " ")) { // vtt subs format
                    subsBuffer = textStr;
                } else if (textStr.contains("\n")) { // ttml subs format
                    CharSequence text;

                    if (subsBuffer != null && textStr.contains(subsBuffer)) {
                        text = textStr.replace(subsBuffer, "").replace("\n", "");
                    } else {
                        text = textStr;
                    }

                    result.add(new Cue(text)); // sub centered by default

                    String[] split = textStr.split("\n");
                    subsBuffer = split.length == 2 ? split[1] : textStr;
                } else {
                    CharSequence text = subsBuffer != null ? textStr.replace(subsBuffer, "") : textStr;
                    result.add(new Cue(text)); // sub centered by default
                    subsBuffer = text;
                }
            }

            return texts(result);
        }
    }
}
