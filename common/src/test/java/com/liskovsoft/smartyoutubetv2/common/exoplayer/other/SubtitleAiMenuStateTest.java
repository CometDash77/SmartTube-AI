package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** T09 slice: the menu state line, without any transport knowledge. */
public class SubtitleAiMenuStateTest {
    @Test
    public void theSwitchComesFirst() {
        assertEquals(SubtitleAiMenuState.Status.AI_OFF,
                SubtitleAiMenuState.of(false, true, true, 5_000));
    }

    @Test
    public void aMissingKeyIsReportedBeforeAMissingSource() {
        assertEquals(SubtitleAiMenuState.Status.NO_KEY,
                SubtitleAiMenuState.of(true, false, false, 0));
        assertEquals(SubtitleAiMenuState.Status.NO_KEY,
                SubtitleAiMenuState.of(true, true, false, 0));
    }

    @Test
    public void withoutASelectedSourceThereIsNothingToTranslate() {
        assertEquals(SubtitleAiMenuState.Status.NO_SOURCE,
                SubtitleAiMenuState.of(true, false, true, 0));
    }

    @Test
    public void aPauseIsVisibleWhileTranslating() {
        assertEquals(SubtitleAiMenuState.Status.PAUSED,
                SubtitleAiMenuState.of(true, true, true, 12_000));
        assertEquals(SubtitleAiMenuState.Status.TRANSLATING,
                SubtitleAiMenuState.of(true, true, true, 0));
    }

    @Test
    public void onlyTheMissingKeyStateOffersTheConfigurationEntry() {
        assertTrue(SubtitleAiMenuState.offersConfiguration(SubtitleAiMenuState.Status.NO_KEY));
        assertFalse(SubtitleAiMenuState.offersConfiguration(SubtitleAiMenuState.Status.AI_OFF));
        assertFalse(SubtitleAiMenuState.offersConfiguration(SubtitleAiMenuState.Status.PAUSED));
    }

    @Test
    public void everyStateExceptTranslatingStillShowsTheOriginal() {
        assertFalse(SubtitleAiMenuState.showsOriginalOnly(SubtitleAiMenuState.Status.TRANSLATING));
        assertTrue(SubtitleAiMenuState.showsOriginalOnly(SubtitleAiMenuState.Status.AI_OFF));
        assertTrue(SubtitleAiMenuState.showsOriginalOnly(SubtitleAiMenuState.Status.NO_KEY));
        assertTrue(SubtitleAiMenuState.showsOriginalOnly(SubtitleAiMenuState.Status.NO_SOURCE));
        assertTrue(SubtitleAiMenuState.showsOriginalOnly(SubtitleAiMenuState.Status.PAUSED));
    }
}
