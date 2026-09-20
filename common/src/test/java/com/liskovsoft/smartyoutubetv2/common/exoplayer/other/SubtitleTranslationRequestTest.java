package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** T07 slice: a prepared request exists only for a usable endpoint plus a key. */
@RunWith(RobolectricTestRunner.class)
public class SubtitleTranslationRequestTest {
    private static SubtitleTranslationConfig config() {
        return new SubtitleTranslationConfig("https://api.deepseek.com", "deepseek-flash", "zh-Hans", "keep it short");
    }

    private static SubtitleBatch batch() {
        return new SubtitleBatch(Collections.singletonList(new SubtitleItem("a", "Hello")), null, null);
    }

    @Test
    public void requestCarriesEndpointHeaderAndFixedBody() throws Exception {
        SubtitleTranslationRequest request = SubtitleTranslationRequest.create(config(), "sk-test", "en", batch());

        assertNotNull(request);
        assertEquals("https://api.deepseek.com/chat/completions", request.getUrl());
        assertEquals("Bearer sk-test", request.getAuthorization());

        JSONObject body = new JSONObject(request.getBody());
        assertEquals("en", body.getString("sourceLanguage"));
        assertEquals("zh-Hans", body.getString("targetLanguage"));
        assertEquals("a", body.getJSONArray("items").getJSONObject(0).getString("id"));
    }

    @Test
    public void requestIsRefusedWithoutAKeyOrWithAnUnusableEndpoint() throws Exception {
        assertNull(SubtitleTranslationRequest.create(config(), "   ", "en", batch()));
        assertNull(SubtitleTranslationRequest.create(config(), null, "en", batch()));
        assertNull(SubtitleTranslationRequest.create(
                new SubtitleTranslationConfig("http://api.deepseek.com", "deepseek-flash", "zh-Hans", null), "sk-test", "en", batch()));
        assertNull(SubtitleTranslationRequest.create(null, "sk-test", "en", batch()));
    }

    @Test
    public void authorizationIsNeverPartOfThePrintedForm() throws Exception {
        SubtitleTranslationRequest request = SubtitleTranslationRequest.create(config(), "sk-secret-value", "en", batch());

        assertNotNull(request);
        assertFalse(request.toString().contains("sk-secret-value"));
    }

    @Test
    public void instructionBelongsToTheSameConfiguration() throws Exception {
        SubtitleTranslationRequest request = SubtitleTranslationRequest.create(config(), "sk-test", "en", batch());

        String instruction = request.getSystemInstruction(config(), "Keep it short.");

        assertTrue(instruction.startsWith(SubtitleProtocolInstruction.FIXED_PROTOCOL));
        assertTrue(instruction.contains("Target language: zh-Hans."));
        assertTrue(instruction.endsWith("Keep it short."));
    }
}
