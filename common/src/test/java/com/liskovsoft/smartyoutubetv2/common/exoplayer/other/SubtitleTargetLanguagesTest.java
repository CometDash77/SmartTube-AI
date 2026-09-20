package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** T09 slice: the programme-owned target-language list. */
public class SubtitleTargetLanguagesTest {
    @Test
    public void bothChineseWritingSystemsAreExplicitAndFirst() {
        List<String> codes = SubtitleTargetLanguages.getCodes();

        assertEquals(SubtitleLanguageSupport.ZH_HANS, codes.get(0));
        assertEquals(SubtitleLanguageSupport.ZH_HANT, codes.get(1));
        assertTrue(codes.contains("en"));
    }

    @Test
    public void theListIsStableAndHasNoDuplicates() {
        List<String> codes = SubtitleTargetLanguages.getCodes();

        assertEquals(codes.size(), new HashSet<>(codes).size());
        assertEquals(codes, SubtitleTargetLanguages.getCodes());
    }

    @Test
    public void namesComeFromTheListAndUnknownCodesAreReturnedAsIs() {
        assertEquals("Chinese (Simplified)", SubtitleTargetLanguages.getDisplayName("zh-Hans"));
        assertEquals("Chinese (Traditional)", SubtitleTargetLanguages.getDisplayName("zh-Hant"));
        assertEquals("English", SubtitleTargetLanguages.getDisplayName("en"));
        assertEquals("xx", SubtitleTargetLanguages.getDisplayName("xx"));
        org.junit.Assert.assertNull(SubtitleTargetLanguages.getDisplayName(null));
    }

    @Test
    public void regionCodesNormaliseBeforeLookup() {
        assertEquals("Chinese (Simplified)", SubtitleTargetLanguages.getDisplayName("zh-CN"));
        assertEquals("Chinese (Traditional)", SubtitleTargetLanguages.getDisplayName("zh-TW"));
        assertTrue(SubtitleTargetLanguages.isOffered("zh-HK"));
    }

    @Test
    public void offeredCodesCoverTheExplicitSetOnly() {
        assertTrue(SubtitleTargetLanguages.isOffered("en"));
        assertFalse(SubtitleTargetLanguages.isOffered("zh"));
        assertFalse(SubtitleTargetLanguages.isOffered("xx"));
        assertFalse(SubtitleTargetLanguages.isOffered(null));
    }

    @Test
    public void theDefaultIsEnglishWhenTheUiLocaleIsUnknown() {
        assertEquals(SubtitleLanguageSupport.DEFAULT_TARGET_LANGUAGE, SubtitleTargetLanguages.defaultCode());
        assertTrue(SubtitleTargetLanguages.isOffered(SubtitleTargetLanguages.defaultCode()));
    }
}
