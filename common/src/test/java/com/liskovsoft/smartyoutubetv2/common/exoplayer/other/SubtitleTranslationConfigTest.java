package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/** T07/T08 slice: what invalidates a translation session and what does not. */
public class SubtitleTranslationConfigTest {
    private static SubtitleTranslationConfig config(String endpoint, String model, String target, String instruction) {
        return new SubtitleTranslationConfig(endpoint, model, target, instruction);
    }

    @Test
    public void defaultsMatchThePlan() {
        SubtitleTranslationConfig config = config(null, null, null, null);

        assertEquals(SubtitleEndpoint.DEFAULT_BASE_URL, config.getEndpointBaseUrl());
        assertEquals(SubtitleTranslationConfig.DEFAULT_MODEL, config.getModel());
        assertTrue(config.isEndpointUsable());
    }

    @Test
    public void sameSettingsShareANamespaceRegardlessOfWhitespace() {
        SubtitleTranslationConfig first = config("https://api.deepseek.com", "deepseek-flash", "zh-Hans", "keep it short");
        SubtitleTranslationConfig second = config(" https://api.deepseek.com/ ", " deepseek-flash ", "zh-Hans", " keep it short ");

        assertTrue(first.hasSameNamespace(second));
        assertEquals(first.namespace(), second.namespace());
    }

    @Test
    public void endpointModelTargetAndInstructionEachChangeTheNamespace() {
        SubtitleTranslationConfig base = config("https://api.deepseek.com", "deepseek-flash", "zh-Hans", "style");

        assertNotEquals(base.namespace(), config("https://other.example.com", "deepseek-flash", "zh-Hans", "style").namespace());
        assertNotEquals(base.namespace(), config("https://api.deepseek.com", "deepseek-pro", "zh-Hans", "style").namespace());
        assertNotEquals(base.namespace(), config("https://api.deepseek.com", "deepseek-flash", "en", "style").namespace());
        assertNotEquals(base.namespace(), config("https://api.deepseek.com", "deepseek-flash", "zh-Hans", "other style").namespace());
    }

    @Test
    public void equivalentEndpointFormsShareTheNamespace() {
        assertTrue(config("https://api.deepseek.com/v1", "deepseek-flash", "zh-Hans", "s")
                .hasSameNamespace(config("https://api.deepseek.com/v1/", "deepseek-flash", "zh-Hans", "s")));
    }

    @Test
    public void anUnusableEndpointIsReportedInsteadOfBeingPersisted() {
        assertFalse(config("http://api.deepseek.com", "deepseek-flash", "zh-Hans", "s").isEndpointUsable());
        assertFalse(config("ftp://api.deepseek.com", "deepseek-flash", "zh-Hans", "s").isEndpointUsable());
    }

    @Test
    public void namespaceIsPrintableAndCarriesNoKey() {
        SubtitleTranslationConfig config = config("https://api.deepseek.com", "deepseek-flash", "zh-Hans", "s");
        String namespace = config.namespace();

        assertFalse(namespace.contains("sk-"));
        assertTrue(namespace.matches("[0-9a-f]+"));
        assertTrue(config.toString().contains(namespace));
    }

    @Test
    public void contextTierAndSegmentationVersionChangeTheNamespace() {
        SubtitleTranslationConfig base = new SubtitleTranslationConfig(
                "https://api.deepseek.com", "deepseek-flash", "zh-Hans", "style", 0, 0);

        assertNotEquals(base.namespace(), new SubtitleTranslationConfig(
                "https://api.deepseek.com", "deepseek-flash", "zh-Hans", "style", 1, 0).namespace());
        assertNotEquals(base.namespace(), new SubtitleTranslationConfig(
                "https://api.deepseek.com", "deepseek-flash", "zh-Hans", "style", 0, 1).namespace());
        assertEquals(base.namespace(), new SubtitleTranslationConfig(
                "https://api.deepseek.com", "deepseek-flash", "zh-Hans", "style", 0, 0).namespace());
        assertEquals("basic tier and no rule version stay the legacy identity", base.namespace(),
                config("https://api.deepseek.com", "deepseek-flash", "zh-Hans", "style").namespace());
    }
}
