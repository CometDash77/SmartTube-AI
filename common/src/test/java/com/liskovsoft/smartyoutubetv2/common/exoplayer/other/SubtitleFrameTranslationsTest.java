package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** T06 acceptance for id-aligned display write-back and its token binding. */
public class SubtitleFrameTranslationsTest {
    private static final String ORIGIN_URL = "https://example.com/api/timedtext?v=abc&lang=en";

    private Map<String, String> mStored;
    private RecordingDisplay mDisplay;
    private AiSubtitleSessionBinder mBinder;

    private static class RecordingDisplay implements SubtitleDisplay {
        private List<String> translations = Collections.emptyList();
        private int writes;

        @Override
        public void setTranslations(List<String> values) {
            translations = new ArrayList<>(values);
            writes++;
        }

        @Override
        public void clearTranslations() {
            translations = Collections.emptyList();
            writes++;
        }

        @Override
        public void setAiDisplayMode(int mode) {
        }

        @Override
        public void resetOriginalCueState() {
        }
    }

    private static SubtitleItem item(String id, String text) {
        return new SubtitleItem(id, text);
    }

    @Before
    public void setUp() {
        mStored = new HashMap<>();
        mDisplay = new RecordingDisplay();
        mBinder = new AiSubtitleSessionBinder(mDisplay, () -> new SelectedSubtitleSource(1, ORIGIN_URL,
                "en", "zh-Hans", "Chinese (Simplified)*", "application/x-mp4-vtt", null, null, true));
        mBinder.onVideoLoaded();
    }

    @Test
    public void alignmentKeepsSlotsAndLeavesMissingOnesNull() {
        List<SubtitleItem> items = Arrays.asList(item("a", "One"), item("b", "Two"), item("c", "Three"));
        mStored.put("a", "\u4e00");
        mStored.put("c", "\u4e09");

        List<String> aligned = SubtitleFrameTranslations.align(items, mStored::get);

        assertEquals(Arrays.asList("\u4e00", null, "\u4e09"), aligned);
    }

    @Test
    public void alignmentIsIndependentOfTranslationArrivalOrder() {
        List<SubtitleItem> items = Arrays.asList(item("first", "One"), item("second", "Two"));
        mStored.put("second", "\u4e8c");
        mStored.put("first", "\u4e00");

        assertEquals(Arrays.asList("\u4e00", "\u4e8c"), SubtitleFrameTranslations.align(items, mStored::get));
    }

    @Test
    public void displayReceivesTheAlignedTranslationsForTheLiveSession() {
        mStored.put("b", "\u4e8c");

        boolean applied = mBinder.applyFrameTranslations(
                Arrays.asList(item("a", "One"), item("b", "Two")), mStored::get);

        assertTrue(applied);
        assertEquals(Arrays.asList(null, "\u4e8c"), mDisplay.translations);
    }

    @Test
    public void frameTranslationsAreRefusedAfterTheSessionWasInvalidated() {
        mStored.put("a", "\u4e00");
        mBinder.onSeekEnd(); // invalidates the previous identity, keeps the source session
        mStored.put("a", "\u4e00");

        boolean applied = mBinder.applyFrameTranslations(Collections.singletonList(item("a", "One")), mStored::get);

        assertTrue("the same source session stays valid across a seek", applied);
        assertEquals(Arrays.asList("\u4e00"), mDisplay.translations);

        mBinder.onSubtitlesShown(false); // no source anymore

        assertFalse(mBinder.applyFrameTranslations(Collections.singletonList(item("a", "One")), mStored::get));
    }

    @Test
    public void emptyFrameWritesNothingToTranslate() {
        assertTrue(mBinder.applyFrameTranslations(Collections.<SubtitleItem>emptyList(), mStored::get));
        assertEquals(Collections.emptyList(), mDisplay.translations);
    }
}
