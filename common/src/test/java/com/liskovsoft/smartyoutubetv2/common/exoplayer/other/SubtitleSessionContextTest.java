package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Plan 4.2 acceptance for the session context: verified examples and the frozen summary. */
public class SubtitleSessionContextTest {
    private static SubtitleBatch batch(long startUs, String... texts) {
        List<SubtitleItem> items = new ArrayList<>();
        List<Long> starts = new ArrayList<>();

        for (int i = 0; i < texts.length; i++) {
            items.add(new SubtitleItem("id-" + startUs + "-" + i, texts[i]));
            starts.add(startUs);
        }

        return SubtitleBatch.withContext(items, starts, SubtitleContext.EMPTY);
    }

    @Test
    public void onlyVerifiedNonBlankResultsBecomeExamples() {
        SubtitleSessionContext context = new SubtitleSessionContext();
        context.recordBatch(batch(1_000_000L, "one", "two", "three"),
                Arrays.asList("eins", "  ", null));

        List<SubtitleTextPair> examples = context.examplesBefore(10_000_000L, 1_000);

        assertEquals("a blank result is not a verified example", 1, examples.size());
        assertEquals("one", examples.get(0).getSource());
        assertEquals("eins", examples.get(0).getTarget());
    }

    @Test
    public void onlyItemsThatAlreadyPlayedBecomeExamples() {
        SubtitleSessionContext context = new SubtitleSessionContext();
        context.recordBatch(batch(1_000_000L, "played"), Collections.singletonList("gespielt"));
        context.recordBatch(batch(50_000_000L, "later"), Collections.singletonList("spaeter"));

        List<SubtitleTextPair> examples = context.examplesBefore(10_000_000L, 1_000);

        assertEquals("the future line must not become context of the current request", 1, examples.size());
        assertEquals("played", examples.get(0).getSource());
    }

    @Test
    public void examplesStayInsideTheirBoundsAndInTimeOrder() {
        SubtitleSessionContext context = new SubtitleSessionContext();
        String[] texts = new String[12];
        String[] translations = new String[12];

        for (int i = 0; i < texts.length; i++) {
            texts[i] = "line" + i;
            translations[i] = "zeile" + i;
        }

        context.recordBatch(batch(1_000_000L, texts), Arrays.asList(translations));

        List<SubtitleTextPair> examples = context.examplesBefore(1_000_000_000L, 1_000);

        assertEquals(SubtitleContext.MAX_EXAMPLES, examples.size());
        assertEquals("the newest eight within the bound win, still in time order",
                "line4", examples.get(0).getSource());
        assertEquals("line11", examples.get(examples.size() - 1).getSource());

        assertTrue("a tight code-point bound keeps fewer pairs",
                context.examplesBefore(1_000_000_000L, 10).size() < SubtitleContext.MAX_EXAMPLES);
    }

    @Test
    public void onlyTheTwoMostRecentBatchesProvideExamples() {
        SubtitleSessionContext context = new SubtitleSessionContext();

        for (int i = 0; i < 3; i++) {
            context.recordBatch(batch(i * 1_000_000L, "line" + i), Collections.singletonList("zeile" + i));
        }

        List<SubtitleTextPair> examples = context.examplesBefore(1_000_000_000L, 1_000);

        assertEquals(2, examples.size());
        assertEquals("line1", examples.get(0).getSource());
        assertEquals("line2", examples.get(1).getSource());
    }

    @Test
    public void aSeekDropsTheExamplesButKeepsTheSummary() {
        SubtitleSessionContext context = new SubtitleSessionContext();
        context.recordBatch(batch(1_000_000L, "played"), Collections.singletonList("gespielt"));
        context.freezeSummary(SubtitleSummary.of("A talk", null));

        context.resetExamples();

        assertEquals(Collections.emptyList(), context.examplesBefore(1_000_000_000L, 1_000));
        assertTrue("the summary belongs to the source, not to the position", context.hasSummary());
    }

    @Test
    public void aNewSourceOrConfigurationDropsEverything() {
        SubtitleSessionContext context = new SubtitleSessionContext();
        context.recordBatch(batch(1_000_000L, "played"), Collections.singletonList("gespielt"));
        context.freezeSummary(SubtitleSummary.of("A talk", null));

        context.reset();

        assertEquals(Collections.emptyList(), context.examplesBefore(1_000_000_000L, 1_000));
        assertFalse(context.hasSummary());
    }

    @Test
    public void theSummaryIsFrozenOnceAndBounded() {
        SubtitleSessionContext context = new SubtitleSessionContext();

        assertTrue(context.freezeSummary(SubtitleSummary.of("First topic", null)));
        assertFalse("a late answer must not replace the frozen summary",
                context.freezeSummary(SubtitleSummary.of("Second topic", null)));
        assertEquals("First topic", context.getSummary().getTopic());

        assertFalse("an empty summary is not a freeze", context.freezeSummary(null));
    }

    @Test
    public void theSummaryAppliesThePlanCaps() {
        List<SubtitleTextPair> terms = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            terms.add(new SubtitleTextPair("term" + i, "begriff" + i));
        }

        SubtitleSummary summary = SubtitleSummary.of(new String(new char[500]).replace('\0', 'x'), terms);

        assertTrue(summary.getTopic().length() <= SubtitleSummary.MAX_TOPIC_CODE_POINTS);
        assertTrue(summary.getTerms().size() <= SubtitleSummary.MAX_TERMS);
        assertTrue(summary.codePoints() <= SubtitleSummary.MAX_CODE_POINTS);

        assertNull("nothing usable means no summary", SubtitleSummary.of(null, Collections.singletonList(
                new SubtitleTextPair("term", " "))));
    }
}
