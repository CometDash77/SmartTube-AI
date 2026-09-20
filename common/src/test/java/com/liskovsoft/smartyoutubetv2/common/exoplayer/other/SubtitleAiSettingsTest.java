package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** T08/T09 slice: validated non-sensitive AI settings. */
public class SubtitleAiSettingsTest {
    @Test
    public void defaultsMatchThePlan() {
        SubtitleAiSettings defaults = SubtitleAiSettings.defaults();

        assertFalse("AI is off for a new video", defaults.isEnabled());
        assertEquals(SubtitleComposer.MODE_ORIGINAL_ONLY, defaults.getDisplayMode());
        assertEquals(SubtitleLanguageSupport.DEFAULT_TARGET_LANGUAGE, defaults.getTargetLanguage());
        assertEquals(SubtitleEndpoint.DEFAULT_BASE_URL, defaults.getConfig().getEndpointBaseUrl());
        assertEquals(SubtitleTranslationConfig.DEFAULT_MODEL, defaults.getConfig().getModel());
        assertNull("no expression preference by default", defaults.getInstruction());
    }

    @Test
    public void targetLanguageIsNormalisedOrFallsBackToEnglish() {
        assertEquals(SubtitleLanguageSupport.ZH_HANS,
                SubtitleAiSettings.create(true, 0, "zh-CN", null, null, null).getTargetLanguage());
        assertEquals(SubtitleLanguageSupport.ZH_HANT,
                SubtitleAiSettings.create(true, 0, "zh-HK", null, null, null).getTargetLanguage());
        assertEquals(SubtitleLanguageSupport.DEFAULT_TARGET_LANGUAGE,
                SubtitleAiSettings.create(true, 0, "zh", null, null, null).getTargetLanguage());
        assertEquals(SubtitleLanguageSupport.DEFAULT_TARGET_LANGUAGE,
                SubtitleAiSettings.create(true, 0, "   ", null, null, null).getTargetLanguage());
    }

    @Test
    public void invalidDisplayModeFallsBackToOriginalOnly() {
        assertEquals(SubtitleComposer.MODE_ORIGINAL_ONLY,
                SubtitleAiSettings.create(true, 99, "en", null, null, null).getDisplayMode());
        assertEquals(SubtitleComposer.MODE_TRANSLATION_ONLY,
                SubtitleAiSettings.create(true, SubtitleComposer.MODE_TRANSLATION_ONLY, "en", null, null, null).getDisplayMode());
        assertEquals(SubtitleComposer.MODE_BILINGUAL,
                SubtitleAiSettings.create(true, SubtitleComposer.MODE_BILINGUAL, "en", null, null, null).getDisplayMode());
    }

    @Test
    public void instructionIsTrimmedAndCapped() {
        StringBuilder longStyle = new StringBuilder();

        for (int i = 0; i < SubtitleProtocolInstruction.MAX_USER_STYLE_CODE_POINTS + 50; i++) {
            longStyle.append('x');
        }

        assertNull(SubtitleAiSettings.create(true, 0, "en", "   ", null, null).getInstruction());
        assertEquals("Keep it short.",
                SubtitleAiSettings.create(true, 0, "en", "  Keep it short.  ", null, null).getInstruction());
        assertEquals(SubtitleProtocolInstruction.MAX_USER_STYLE_CODE_POINTS,
                SubtitleAiSettings.create(true, 0, "en", longStyle.toString(), null, null).getInstruction().length());
    }

    @Test
    public void enablingAndModeChangesKeepTheRestOfTheSettings() {
        SubtitleAiSettings settings = SubtitleAiSettings.create(false, SubtitleComposer.MODE_BILINGUAL, "zh-TW",
                "Be concise.", "https://example.com/v1", "custom-model");

        SubtitleAiSettings enabled = SubtitleAiSettings.withEnabled(settings, true);

        assertTrue(enabled.isEnabled());
        assertEquals(SubtitleComposer.MODE_BILINGUAL, enabled.getDisplayMode());
        assertEquals(SubtitleLanguageSupport.ZH_HANT, enabled.getTargetLanguage());
        assertEquals("Be concise.", enabled.getInstruction());
        assertEquals("https://example.com/v1", enabled.getConfig().getEndpointBaseUrl());
        assertEquals("custom-model", enabled.getConfig().getModel());

        SubtitleAiSettings otherMode = SubtitleAiSettings.withDisplayMode(enabled, SubtitleComposer.MODE_ORIGINAL_ONLY);
        assertEquals(SubtitleComposer.MODE_ORIGINAL_ONLY, otherMode.getDisplayMode());
        assertEquals("Be concise.", otherMode.getInstruction());
        assertTrue(otherMode.isEnabled());
    }

    @Test
    public void printableFormCarriesNoKeyMaterial() {
        String printed = SubtitleAiSettings.create(true, SubtitleComposer.MODE_BILINGUAL, "zh-CN", "s", null, null).toString();

        assertFalse(printed.contains("sk-"));
        assertTrue(printed.contains("namespace="));
    }

    @Test
    public void kissFeatureDefaultsMatchThePlan() {
        SubtitleAiSettings defaults = SubtitleAiSettings.defaults();

        assertEquals("basic neighbour context is the default tier",
                SubtitleAiSettings.CONTEXT_BASIC, defaults.getContextTier());
        assertFalse("rule segmentation is off by default", defaults.usesRuleSegmentation());
        assertTrue("load notifications are on by default", defaults.showsLoadNotifications());
        assertEquals("raw items are the default translation units", 0,
                defaults.getConfig().getSegmentationRuleVersion());
    }

    @Test
    public void anUnknownContextTierFallsBackToBasic() {
        assertEquals(SubtitleAiSettings.CONTEXT_BASIC,
                SubtitleAiSettings.create(true, 0, "en", null, null, null, 7, false, true).getContextTier());
        assertEquals(SubtitleAiSettings.CONTEXT_VIDEO_ENHANCED,
                SubtitleAiSettings.create(true, 0, "en", null, null, null,
                        SubtitleAiSettings.CONTEXT_VIDEO_ENHANCED, false, true).getContextTier());
    }

    @Test
    public void ruleSegmentationRecordsTheRuleVersionAndChangesTheIdentity() {
        SubtitleAiSettings plain = SubtitleAiSettings.create(true, 0, "en", null, null, null, 0, false, true);
        SubtitleAiSettings segmented = SubtitleAiSettings.withRuleSegmentation(plain, true);

        assertFalse(plain.usesRuleSegmentation());
        assertTrue(segmented.usesRuleSegmentation());
        assertEquals(SubtitleAiSettings.SEGMENTATION_RULE_VERSION, segmented.getConfig().getSegmentationRuleVersion());
        assertEquals(0, plain.getConfig().getSegmentationRuleVersion());
        assertNotEquals("the translated units changed, so the identity must change",
                plain.getConfig().namespace(), segmented.getConfig().namespace());
    }

    @Test
    public void withMethodsKeepTheOtherKissFields() {
        SubtitleAiSettings base = SubtitleAiSettings.create(true, SubtitleComposer.MODE_BILINGUAL, "en", null,
                "https://example.com", "model-x", SubtitleAiSettings.CONTEXT_COHERENT, true, false);

        SubtitleAiSettings retiered = SubtitleAiSettings.withContextTier(base, SubtitleAiSettings.CONTEXT_VIDEO_ENHANCED);
        assertEquals(SubtitleAiSettings.CONTEXT_VIDEO_ENHANCED, retiered.getContextTier());
        assertTrue(retiered.usesRuleSegmentation());
        assertFalse(retiered.showsLoadNotifications());
        assertEquals(SubtitleComposer.MODE_BILINGUAL, retiered.getDisplayMode());

        SubtitleAiSettings renotified = SubtitleAiSettings.withLoadNotifications(base, true);
        assertEquals(SubtitleAiSettings.CONTEXT_COHERENT, renotified.getContextTier());
        assertTrue(renotified.usesRuleSegmentation());
        assertTrue(renotified.showsLoadNotifications());

        SubtitleAiSettings reenabled = SubtitleAiSettings.withEnabled(base, false);
        assertEquals(SubtitleAiSettings.CONTEXT_COHERENT, reenabled.getContextTier());
        assertTrue(reenabled.usesRuleSegmentation());
        assertFalse(reenabled.showsLoadNotifications());
    }
}
