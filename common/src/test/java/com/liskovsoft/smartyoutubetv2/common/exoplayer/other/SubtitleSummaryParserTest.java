package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Plan 4.2 acceptance for the fixed summary schema: bounded, strict and closed. */
@RunWith(RobolectricTestRunner.class)
public class SubtitleSummaryParserTest {
    private static String envelope(String content, String finishReason) {
        return "{\"choices\":[{\"finish_reason\":\"" + finishReason + "\",\"message\":{\"content\":"
                + org.json.JSONObject.quote(content) + "}}]}";
    }

    @Test
    public void aValidAnswerBecomesABoundedSummary() {
        SubtitleSummaryParser.Result result = SubtitleSummaryParser.parse(envelope(
                "{\"topic\":\"A talk about greetings\",\"terms\":[{\"source\":\"hi\",\"translation\":\"hallo\"}]}",
                "stop"), false);

        assertTrue(!result.isFailed());
        assertEquals("A talk about greetings", result.getSummary().getTopic());
        assertEquals(1, result.getSummary().getTerms().size());
    }

    @Test
    public void aTruncatedOrMalformedAnswerIsRefusedAsAWhole() {
        assertTrue(SubtitleSummaryParser.parse(envelope("{\"topic\":\"x\"}", "length"), false).isFailed());
        assertTrue(SubtitleSummaryParser.parse("not json", false).isFailed());
        assertTrue(SubtitleSummaryParser.parse(envelope("not json", "stop"), false).isFailed());
        assertTrue(SubtitleSummaryParser.parse(null, false).isFailed());
        assertTrue(SubtitleSummaryParser.parse("", false).isFailed());
        assertTrue(SubtitleSummaryParser.parse(envelope("{\"topic\":\"x\"}", "stop"), true).isFailed());
    }

    @Test
    public void anOversizedAnswerIsRefusedBeforeParsing() {
        StringBuilder huge = new StringBuilder();

        for (int i = 0; i < SubtitleSummaryParser.MAX_RESPONSE_CHARS + 1; i++) {
            huge.append('x');
        }

        assertEquals(SubtitleSummaryParser.FAILURE_TOO_LARGE,
                SubtitleSummaryParser.parse(huge.toString(), false).getFailure());
    }

    @Test
    public void unusableTermsAreDroppedAndTheResultStaysBounded() {
        SubtitleSummaryParser.Result result = SubtitleSummaryParser.parse(envelope(
                "{\"topic\":\"Topic\",\"terms\":[{\"source\":\"a\",\"translation\":\"\"},"
                        + "{\"source\":\"\",\"translation\":\"b\"},{\"source\":\"ok\",\"translation\":\"gut\"}]}",
                "stop"), false);

        assertEquals(1, result.getSummary().getTerms().size());
        assertEquals("ok", result.getSummary().getTerms().get(0).getSource());
    }

    @Test
    public void anAnswerWithoutAnyUsableTextIsARefusedAnswer() {
        assertTrue(SubtitleSummaryParser.parse(envelope("{\"topic\":\"\"}", "stop"), false).isFailed());
        assertNull(SubtitleSummaryParser.parse(envelope("{\"topic\":\"\"}", "stop"), false).getSummary());
    }
}
