package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Plan 4.2 acceptance through the production request factory: the selected context tier decides what
 * the wire body may carry, and a context-less batch never grows an empty reference field.
 */
@RunWith(RobolectricTestRunner.class)
public class SubtitleContextPayloadTest {
    private static final String KEY = "sk-test-placeholder";

    private static SubtitleBatch batchWithContext(SubtitleContext context) {
        List<SubtitleItem> items = Collections.singletonList(new SubtitleItem("id-a", "Hello"));
        List<Long> starts = Collections.singletonList(0L);

        return SubtitleBatch.withContext(items, starts, context);
    }

    private static SubtitleContext coherentContext() {
        return new SubtitleContext(Arrays.asList("Before"), Arrays.asList("After"),
                Collections.singletonList(new SubtitleTextPair("Hello", "Hallo")),
                SubtitleSummary.of("A talk about greetings",
                        Collections.singletonList(new SubtitleTextPair("greeting", "Begruessung"))));
    }

    private static SubtitleTranslationConfig config(int contextTier) {
        return new SubtitleTranslationConfig(SubtitleEndpoint.DEFAULT_BASE_URL,
                SubtitleTranslationConfig.DEFAULT_MODEL, "de", null, contextTier, 0);
    }

    private static JSONObject payloadOf(SubtitleTranslationConfig config, SubtitleBatch batch)
            throws Exception {
        SubtitleTranslationRequest request = SubtitleTranslationRequest.create(config, KEY, "en", batch);

        assertNotNull("a usable configuration must still produce a request", request);
        JSONObject body = new JSONObject(request.getBody());
        String userMessage = body.getJSONArray("messages").getJSONObject(1).getString("content");

        return new JSONObject(userMessage);
    }

    @Test
    public void theBasicTierSendsOnlyNeighboursAndItems() throws Exception {
        JSONObject payload = payloadOf(config(SubtitleAiSettings.CONTEXT_BASIC), batchWithContext(coherentContext()));

        assertEquals("Hello", payload.getJSONArray("items").getJSONObject(0).getString("text"));
        assertEquals(1, payload.getJSONArray("contextBefore").length());
        assertFalse("the basic tier must not send examples", payload.has(SubtitleRequestBuilder.KEY_TRANSLATION_EXAMPLES));
        assertFalse("the basic tier must not send a summary", payload.has(SubtitleRequestBuilder.KEY_VIDEO_CONTEXT));
    }

    @Test
    public void theCoherentTierSendsTheVerifiedExamplesOnly() throws Exception {
        JSONObject payload = payloadOf(config(SubtitleAiSettings.CONTEXT_COHERENT), batchWithContext(coherentContext()));

        assertTrue(payload.has(SubtitleRequestBuilder.KEY_TRANSLATION_EXAMPLES));
        JSONObject example = payload.getJSONArray(SubtitleRequestBuilder.KEY_TRANSLATION_EXAMPLES).getJSONObject(0);
        assertEquals("Hello", example.getString(SubtitleRequestBuilder.KEY_SOURCE));
        assertEquals("Hallo", example.getString(SubtitleRequestBuilder.KEY_TRANSLATION));

        assertFalse("only the enhanced tier may send the video context",
                payload.has(SubtitleRequestBuilder.KEY_VIDEO_CONTEXT));
        assertEquals("the requested ids are unchanged by any context",
                1, payload.getJSONArray("items").length());
    }

    @Test
    public void theEnhancedTierSendsTheFrozenSummaryAsData() throws Exception {
        JSONObject payload = payloadOf(config(SubtitleAiSettings.CONTEXT_VIDEO_ENHANCED),
                batchWithContext(coherentContext()));

        assertTrue(payload.has(SubtitleRequestBuilder.KEY_VIDEO_CONTEXT));
        JSONObject videoContext = payload.getJSONObject(SubtitleRequestBuilder.KEY_VIDEO_CONTEXT);
        assertEquals("A talk about greetings", videoContext.getString(SubtitleRequestBuilder.KEY_TOPIC));
        assertEquals("greeting", videoContext.getJSONArray(SubtitleRequestBuilder.KEY_TERMS)
                .getJSONObject(0).getString(SubtitleRequestBuilder.KEY_SOURCE));

        // The system instruction still owns the output rules, so the answer stays one-to-one.
        String system = new JSONObject(SubtitleTranslationRequest.create(
                config(SubtitleAiSettings.CONTEXT_VIDEO_ENHANCED), KEY, "en", batchWithContext(coherentContext()))
                .getBody()).getJSONArray("messages").getJSONObject(0).getString("content");
        assertTrue(system.contains("only ids that were requested"));
    }

    @Test
    public void aContextWithoutReferenceDataNeverGrowsAnEmptyField() throws Exception {
        JSONObject payload = payloadOf(config(SubtitleAiSettings.CONTEXT_VIDEO_ENHANCED),
                batchWithContext(new SubtitleContext(Collections.singletonList("Before"), null, null, null)));

        assertFalse(payload.has(SubtitleRequestBuilder.KEY_TRANSLATION_EXAMPLES));
        assertFalse(payload.has(SubtitleRequestBuilder.KEY_VIDEO_CONTEXT));
        assertTrue(payload.has("items"));
    }
}
