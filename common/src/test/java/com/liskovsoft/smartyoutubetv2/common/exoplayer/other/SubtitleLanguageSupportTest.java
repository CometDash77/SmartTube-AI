package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** T08/T09 slice: region to writing-system normalisation of plan section 5. */
public class SubtitleLanguageSupportTest {
    @Test
    public void simplifiedRegionsNormaliseToHans() {
        assertEquals(SubtitleLanguageSupport.ZH_HANS, SubtitleLanguageSupport.normalizeWritingSystem("zh-CN"));
        assertEquals(SubtitleLanguageSupport.ZH_HANS, SubtitleLanguageSupport.normalizeWritingSystem("zh-SG"));
        assertEquals(SubtitleLanguageSupport.ZH_HANS, SubtitleLanguageSupport.normalizeWritingSystem("ZH-cn"));
    }

    @Test
    public void traditionalRegionsNormaliseToHant() {
        assertEquals(SubtitleLanguageSupport.ZH_HANT, SubtitleLanguageSupport.normalizeWritingSystem("zh-TW"));
        assertEquals(SubtitleLanguageSupport.ZH_HANT, SubtitleLanguageSupport.normalizeWritingSystem("zh-HK"));
        assertEquals(SubtitleLanguageSupport.ZH_HANT, SubtitleLanguageSupport.normalizeWritingSystem("zh-MO"));
    }

    @Test
    public void bareChineseAndUnknownVariantsStayUnknown() {
        assertNull(SubtitleLanguageSupport.normalizeWritingSystem("zh"));
        assertNull(SubtitleLanguageSupport.normalizeWritingSystem("zh-Hans-CN-x"));
        assertNull(SubtitleLanguageSupport.normalizeWritingSystem(null));
        assertNull(SubtitleLanguageSupport.normalizeWritingSystem("   "));
    }

    @Test
    public void otherLanguagesKeepTheirCode() {
        assertEquals("en", SubtitleLanguageSupport.normalizeWritingSystem("EN"));
        assertEquals("ru", SubtitleLanguageSupport.normalizeWritingSystem("ru"));
    }

    @Test
    public void equalWritingSystemsAreDetectedOnlyWhenBothAreKnown() {
        assertTrue(SubtitleLanguageSupport.isSameWritingSystem("zh-CN", "zh-SG"));
        assertTrue(SubtitleLanguageSupport.isSameWritingSystem("en-US", "en-GB"));
        assertFalse(SubtitleLanguageSupport.isSameWritingSystem("zh-CN", "zh-TW"));
        assertFalse("a bare zh is unknown, not equal", SubtitleLanguageSupport.isSameWritingSystem("zh", "zh"));
    }

    @Test
    public void defaultTargetLanguageFallsBackToEnglishWhenUnknown() {
        assertEquals(SubtitleLanguageSupport.ZH_HANS, SubtitleLanguageSupport.defaultTargetLanguage("zh-CN"));
        assertEquals(SubtitleLanguageSupport.ZH_HANT, SubtitleLanguageSupport.defaultTargetLanguage("zh-HK"));
        assertEquals(SubtitleLanguageSupport.DEFAULT_TARGET_LANGUAGE, SubtitleLanguageSupport.defaultTargetLanguage("zh"));
        assertEquals(SubtitleLanguageSupport.DEFAULT_TARGET_LANGUAGE, SubtitleLanguageSupport.defaultTargetLanguage(null));
        assertEquals("ru", SubtitleLanguageSupport.defaultTargetLanguage("ru"));
    }
}
