package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** T07 slice: strict response validation rules of section 6.2. */
@RunWith(RobolectricTestRunner.class)
public class SubtitleResponseParserTest {
    private static final java.util.List<String> IDS = Arrays.asList("a", "b", "c");

    @Test
    public void validResponseKeepsRequestedIdsOnly() {
        SubtitleResponseParser.Result result = SubtitleResponseParser.parse(
                "{\"items\":[{\"id\":\"a\",\"translation\":\"\u4e00\"},{\"id\":\"unknown\",\"translation\":\"\u4e8c\"},{\"id\":\"b\",\"translation\":\"\u4e09\"}]}",
                false, IDS);

        assertFalse(result.isBatchFailed());
        assertNull(result.getFailure());
        assertEquals(2, result.getTranslations().size());
        assertEquals("\u4e00", result.getTranslations().get("a"));
        assertEquals("\u4e09", result.getTranslations().get("b"));
    }

    @Test
    public void outOfOrderIdsAreRestoredById() {
        SubtitleResponseParser.Result result = SubtitleResponseParser.parse(
                "{\"items\":[{\"id\":\"c\",\"translation\":\"3\"},{\"id\":\"a\",\"translation\":\"1\"}]}",
                false, IDS);

        assertEquals("1", result.getTranslations().get("a"));
        assertEquals("3", result.getTranslations().get("c"));
    }

    @Test
    public void duplicatedIdVoidsOnlyThatId() {
        SubtitleResponseParser.Result result = SubtitleResponseParser.parse(
                "{\"items\":[{\"id\":\"a\",\"translation\":\"1\"},{\"id\":\"a\",\"translation\":\"1b\"},{\"id\":\"b\",\"translation\":\"2\"}]}",
                false, IDS);

        assertFalse(result.isBatchFailed());
        assertFalse(result.getTranslations().containsKey("a"));
        assertEquals("2", result.getTranslations().get("b"));
    }

    @Test
    public void nonStringBlankAndMissingTranslationsFailTheirOwnEntry() {
        SubtitleResponseParser.Result result = SubtitleResponseParser.parse(
                "{\"items\":[{\"id\":\"a\",\"translation\":12},{\"id\":\"b\",\"translation\":\"   \"},{\"id\":\"c\"}]}",
                false, IDS);

        assertTrue(result.isBatchFailed());
        assertEquals(SubtitleResponseParser.FAILURE_NO_TRANSLATIONS, result.getFailure());
        assertEquals(0, result.getTranslations().size());
    }

    @Test
    public void overlongTranslationFailsItsOwnEntry() {
        StringBuilder longText = new StringBuilder();

        for (int i = 0; i < SubtitleResponseParser.MAX_TRANSLATION_CODE_POINTS + 1; i++) {
            longText.append('x');
        }

        SubtitleResponseParser.Result result = SubtitleResponseParser.parse(
                "{\"items\":[{\"id\":\"a\",\"translation\":\"" + longText + "\"},{\"id\":\"b\",\"translation\":\"2\"}]}",
                false, IDS);

        assertFalse(result.getTranslations().containsKey("a"));
        assertEquals("2", result.getTranslations().get("b"));
    }

    @Test
    public void malformedJsonFailsTheWholeBatch() {
        SubtitleResponseParser.Result result = SubtitleResponseParser.parse("{\"items\":[", false, IDS);

        assertTrue(result.isBatchFailed());
        assertEquals(SubtitleResponseParser.FAILURE_MALFORMED, result.getFailure());
        assertEquals(0, result.getTranslations().size());
    }

    @Test
    public void truncatedResponseFailsTheWholeBatch() {
        SubtitleResponseParser.Result result = SubtitleResponseParser.parse(
                "{\"items\":[{\"id\":\"a\",\"translation\":\"1\"}]}", true, IDS);

        assertTrue(result.isBatchFailed());
        assertEquals(SubtitleResponseParser.FAILURE_TRUNCATED, result.getFailure());
        assertEquals(0, result.getTranslations().size());
    }

    @Test
    public void missingItemsArrayAndEmptyBodyFailTheBatch() {
        assertEquals(SubtitleResponseParser.FAILURE_NO_ITEMS,
                SubtitleResponseParser.parse("{\"other\":1}", false, IDS).getFailure());
        assertEquals(SubtitleResponseParser.FAILURE_EMPTY,
                SubtitleResponseParser.parse("   ", false, IDS).getFailure());
    }

    @Test
    public void requestedIdsMayBeEmpty() {
        SubtitleResponseParser.Result result = SubtitleResponseParser.parse(
                "{\"items\":[{\"id\":\"a\",\"translation\":\"1\"}]}", false, Collections.<String>emptyList());

        assertFalse(result.isBatchFailed());
        assertTrue(result.getTranslations().isEmpty());
    }

    static String completion(String content, String reason) throws Exception {
        return new org.json.JSONObject().put("choices", new org.json.JSONArray().put(
                new org.json.JSONObject().put("index", 0).put("finish_reason", reason)
                        .put("message", new org.json.JSONObject().put("role", "assistant")
                                .put("content", content)))).toString(2);
    }

    @Test
    public void officialChatCompletionEnvelopeDeliversItsMessageContent() throws Exception {
        String body = completion("{\"items\":[{\"id\":\"a\",\"translation\":\"你好\"}]}", "stop");
        SubtitleResponseParser.Result result = SubtitleResponseParser.parse(body, false, IDS);
        assertFalse(result.isBatchFailed());
        assertEquals("你好", result.getTranslations().get("a"));
    }

    @Test
    public void prettyPrintedLengthFinishCannotSlipThrough() throws Exception {
        String body = completion("{\"items\":[{\"id\":\"a\",\"translation\":\"partial\"}]}", "length");
        assertEquals(SubtitleResponseParser.FAILURE_TRUNCATED,
                SubtitleResponseParser.parse(body, false, IDS).getFailure());
    }

    @Test
    public void emptyInvalidAndNonTextCompletionsAreNotSuccess() throws Exception {
        for (String body : Arrays.asList("{\"choices\":[]}",
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":null}}]}",
                completion("not json", "stop"), completion("{\"items\":[]}", "stop"),
                completion("{\"items\":[]}", "content_filter"))) {
            assertTrue(SubtitleResponseParser.parse(body, false, IDS).isBatchFailed());
        }
    }
}
