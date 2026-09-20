package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** T04 acceptance for the three display modes, including partial and missing translations. */
public class SubtitleComposerTest {
    private static SubtitleComposer composer(int mode, List<String> original, List<String> translations) {
        SubtitleComposer composer = new SubtitleComposer();
        composer.setMode(mode);
        composer.setOriginalLines(original);
        composer.setTranslations(translations);

        return composer;
    }

    @Test
    public void originalOnlyShowsTheOriginalEvenWhenTranslationsExist() {
        assertEquals(Arrays.asList("Hello"),
                composer(SubtitleComposer.MODE_ORIGINAL_ONLY, Arrays.asList("Hello"), Arrays.asList("\u4f60\u597d")).compose());
    }

    @Test
    public void bilingualPutsTheOriginalAboveTheTranslation() {
        assertEquals(Arrays.asList("Hello\n\u4f60\u597d"),
                composer(SubtitleComposer.MODE_BILINGUAL, Arrays.asList("Hello"), Arrays.asList("\u4f60\u597d")).compose());
    }

    @Test
    public void bilingualShowsOneOriginalWhenTheTranslationIsMissing() {
        List<String> composed = composer(SubtitleComposer.MODE_BILINGUAL, Arrays.asList("Hello"), Collections.<String>emptyList()).compose();

        assertEquals(Arrays.asList("Hello"), composed);
        assertTrue(composed.get(0).indexOf('\n') < 0); // no separator, no ellipsis placeholder
    }

    @Test
    public void translationOnlyFallsBackToTheOriginalWhileWaiting() {
        assertEquals(Arrays.asList("Hello"),
                composer(SubtitleComposer.MODE_TRANSLATION_ONLY, Arrays.asList("Hello"), null).compose());
        assertEquals(Arrays.asList("\u4f60\u597d"),
                composer(SubtitleComposer.MODE_TRANSLATION_ONLY, Arrays.asList("Hello"), Arrays.asList("\u4f60\u597d")).compose());
    }

    @Test
    public void partialTranslationsOnlyAffectTheirOwnSlots() {
        SubtitleComposer composer = composer(SubtitleComposer.MODE_BILINGUAL,
                Arrays.asList("First", "Second"), Arrays.asList("\u4e00", null));

        assertEquals(Arrays.asList("First\n\u4e00", "Second"), composer.compose());
        assertTrue(composer.hasTranslation(0));
        assertFalse(composer.hasTranslation(1));
    }

    @Test
    public void blankOrWhitespaceTranslationsAreTreatedAsMissing() {
        SubtitleComposer composer = composer(SubtitleComposer.MODE_BILINGUAL,
                Arrays.asList("Hello"), Arrays.asList("   "));

        assertEquals(Arrays.asList("Hello"), composer.compose());
        assertFalse(composer.hasTranslation(0));
    }

    @Test
    public void moreTranslationsThanOriginalsAreIgnored() {
        SubtitleComposer composer = composer(SubtitleComposer.MODE_BILINGUAL,
                Arrays.asList("Hello"), Arrays.asList("\u4f60\u597d", "extra"));

        assertEquals(Arrays.asList("Hello\n\u4f60\u597d"), composer.compose());
    }

    @Test
    public void multilineOriginalTextIsKeptAsOneSlot() {
        SubtitleComposer composer = composer(SubtitleComposer.MODE_BILINGUAL,
                Arrays.asList("Line one\nLine two"), Arrays.asList("\u4e00\n\u4e8c"));

        assertEquals(Arrays.asList("Line one\nLine two\n\u4e00\n\u4e8c"), composer.compose());
    }

    @Test
    public void emojiAndRtlTextPassThroughUnchanged() {
        SubtitleComposer composer = composer(SubtitleComposer.MODE_BILINGUAL,
                Arrays.asList("Emoji \ud83d\ude00"), Arrays.asList("\u0645\u0631\u062d\u0628\u0627"));

        assertEquals(Arrays.asList("Emoji \ud83d\ude00\n\u0645\u0631\u062d\u0628\u0627"), composer.compose());
    }

    @Test
    public void sameWritingSystemShowsOneOriginalLineInEveryMode() {
        for (int mode : new int[]{SubtitleComposer.MODE_ORIGINAL_ONLY, SubtitleComposer.MODE_TRANSLATION_ONLY,
                SubtitleComposer.MODE_BILINGUAL}) {
            SubtitleComposer composer = composer(mode, Arrays.asList("Hello"), Arrays.asList("Hello"));
            composer.setSourceSameAsTarget(true);

            assertEquals("mode " + mode, Arrays.asList("Hello"), composer.compose());
        }
    }

    @Test
    public void emptyOriginalFrameClearsTheScreenInEveryMode() {
        for (int mode : new int[]{SubtitleComposer.MODE_ORIGINAL_ONLY, SubtitleComposer.MODE_TRANSLATION_ONLY,
                SubtitleComposer.MODE_BILINGUAL}) {
            SubtitleComposer composer = composer(mode, Collections.<String>emptyList(), Arrays.asList("\u4f60\u597d"));

            assertEquals(Collections.emptyList(), composer.compose());
        }
    }

    @Test
    public void modeSwitchOnlyRecomposes() {
        SubtitleComposer composer = composer(SubtitleComposer.MODE_ORIGINAL_ONLY, Arrays.asList("Hello"), Arrays.asList("\u4f60\u597d"));

        composer.setMode(SubtitleComposer.MODE_BILINGUAL);
        assertEquals(Arrays.asList("Hello\n\u4f60\u597d"), composer.compose());

        composer.setMode(SubtitleComposer.MODE_ORIGINAL_ONLY);
        assertEquals(Arrays.asList("Hello"), composer.compose());

        assertEquals(Arrays.asList("Hello"), composer.getOriginalLines());
    }

    @Test
    public void clearingTranslationsKeepsTheOriginal() {
        SubtitleComposer composer = composer(SubtitleComposer.MODE_BILINGUAL, Arrays.asList("Hello"), Arrays.asList("\u4f60\u597d"));

        composer.clearTranslations();

        assertEquals(Arrays.asList("Hello"), composer.compose());
        assertFalse(composer.hasTranslation(0));
    }

    @Test
    public void unknownModeIsIgnored() {
        SubtitleComposer composer = composer(SubtitleComposer.MODE_BILINGUAL, Arrays.asList("Hello"), Arrays.asList("\u4f60\u597d"));

        composer.setMode(99);

        assertEquals(SubtitleComposer.MODE_BILINGUAL, composer.getMode());
    }

    @Test
    public void originalLinesAreNotSharedWithTheCaller() {
        List<String> source = new java.util.ArrayList<>(Arrays.asList("Hello"));
        SubtitleComposer composer = new SubtitleComposer();
        composer.setOriginalLines(source);

        source.add("Injected");

        assertEquals(Arrays.asList("Hello"), composer.compose());
    }
}
