package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** T07 slice: the request payload must survive hostile subtitle text. */
@RunWith(RobolectricTestRunner.class)
public class SubtitleRequestBuilderTest {
    private static SubtitleBatch batch(String id, String text) {
        return new SubtitleBatch(Collections.singletonList(new SubtitleItem(id, text)),
                Collections.singletonList("before"), Collections.singletonList("after"));
    }

    @Test
    public void payloadCarriesLanguagesIdsTextAndContext() throws Exception {
        JSONObject root = new JSONObject(SubtitleRequestBuilder.buildPayload("en", "zh-Hans",
                batch("e42c0", "This is the sentence.")));

        assertEquals("en", root.getString("sourceLanguage"));
        assertEquals("zh-Hans", root.getString("targetLanguage"));
        assertEquals("before", root.getJSONArray("contextBefore").getJSONObject(0).getString("text"));
        assertEquals("after", root.getJSONArray("contextAfter").getJSONObject(0).getString("text"));

        JSONArray items = root.getJSONArray("items");
        assertEquals(1, items.length());
        assertEquals("e42c0", items.getJSONObject(0).getString("id"));
        assertEquals("This is the sentence.", items.getJSONObject(0).getString("text"));
    }

    @Test
    public void hostileTextIsSerializedSafely() throws Exception {
        String hostile = "He said \"stop\"\nand: \\ {not json} \u0007";
        JSONObject root = new JSONObject(SubtitleRequestBuilder.buildPayload("en", "zh-Hans",
                batch("id-1", hostile)));

        assertEquals(hostile, root.getJSONArray("items").getJSONObject(0).getString("text"));
    }

    @Test
    public void chatBodyCarriesModelMessagesAndProtocolFlags() throws Exception {
        SubtitleTranslationConfig config = new SubtitleTranslationConfig("https://api.deepseek.com", null, "zh-Hans", null);
        String payload = SubtitleRequestBuilder.buildPayload("en", "zh-Hans", batch("id-1", "Hello"));

        JSONObject root = new JSONObject(SubtitleRequestBuilder.buildChatCompletionsBody(
                config, SubtitleProtocolInstruction.systemInstruction("zh-Hans", null), payload, null));

        assertEquals(SubtitleTranslationConfig.DEFAULT_MODEL, root.getString("model"));
        assertFalse(root.getBoolean("stream"));
        assertEquals(SubtitleRequestBuilder.MAX_OUTPUT_TOKENS, root.getInt("max_tokens"));
        assertEquals("disabled", root.getJSONObject("thinking").getString("type"));
        assertEquals("json_object", root.getJSONObject("response_format").getString("type"));

        JSONArray messages = root.getJSONArray("messages");
        assertEquals(2, messages.length());
        assertEquals("system", messages.getJSONObject(0).getString("role"));
        assertTrue(messages.getJSONObject(0).getString("content").startsWith(SubtitleProtocolInstruction.FIXED_PROTOCOL));
        assertEquals("user", messages.getJSONObject(1).getString("role"));
        assertEquals(payload, messages.getJSONObject(1).getString("content"));
    }

    @Test
    public void chatBodyAddsTheUserPreferenceAsDataAfterThePayload() throws Exception {
        SubtitleTranslationConfig config = new SubtitleTranslationConfig("https://api.deepseek.com", "custom-model", "en", null);

        JSONObject root = new JSONObject(SubtitleRequestBuilder.buildChatCompletionsBody(
                config, SubtitleProtocolInstruction.systemInstruction("en", null), "{}", "  Keep it short.  "));

        assertEquals("custom-model", root.getString("model"));
        JSONArray messages = root.getJSONArray("messages");
        assertEquals(3, messages.length());
        assertEquals("Keep it short.", messages.getJSONObject(2).getString("content"));
    }

    @Test
    public void emptyBatchProducesAnEmptyItemsArray() throws Exception {
        JSONObject root = new JSONObject(SubtitleRequestBuilder.buildPayload("en", "zh-Hans", null));

        assertEquals(0, root.getJSONArray("items").length());
        assertEquals(0, root.getJSONArray("contextBefore").length());
        assertFalse(root.has("timeline"));
    }
}
