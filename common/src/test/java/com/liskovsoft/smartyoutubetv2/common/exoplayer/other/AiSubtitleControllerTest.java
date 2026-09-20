package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * T05 acceptance: stale success and stale failure callbacks can never reach the display, and the
 * identity is invalidated before in-flight work is cancelled.
 */
public class AiSubtitleControllerTest {
    private static final String ORIGIN_URL = "https://example.com/api/timedtext?v=abc&lang=en";
    private static final String TRANSLATED_URL = ORIGIN_URL + "&tlang=zh-Hans";

    private RecordingDisplay mDisplay;
    private AiSubtitleController mController;

    private static class RecordingDisplay implements SubtitleDisplay {
        private final List<String> actions = new ArrayList<>();
        private List<String> lastTranslations;
        private int mode = SubtitleComposer.MODE_ORIGINAL_ONLY;

        @Override
        public void setTranslations(List<String> translations) {
            lastTranslations = new ArrayList<>(translations);
            actions.add("set:" + translations);
        }

        @Override
        public void clearTranslations() {
            lastTranslations = Collections.emptyList();
            actions.add("clear");
        }

        @Override
        public void setAiDisplayMode(int mode) {
            this.mode = mode;
            actions.add("mode:" + mode);
        }

        @Override
        public void resetOriginalCueState() {
            actions.add("reset");
        }

        String lastAction() {
            return actions.isEmpty() ? null : actions.get(actions.size() - 1);
        }
    }

    private static class RecordingCancellable implements AiSubtitleController.Cancellable {
        private final AiSubtitleController controller;
        private final List<Boolean> staleAtCancel = new ArrayList<>();
        private AiSubtitleController.Token tokenAtCancel;
        private boolean cancelled;

        RecordingCancellable(AiSubtitleController controller) {
            this.controller = controller;
        }

        @Override
        public void cancel() {
            cancelled = true;
            staleAtCancel.add(!controller.isCurrent(tokenAtCancel));
        }
    }

    private static SelectedSubtitleSource source(String baseUrl) {
        return new SelectedSubtitleSource(1, baseUrl, "en", "zh-Hans", "Chinese (Simplified)*",
                "application/x-mp4-vtt", null, null, true);
    }

    @Before
    public void setUp() {
        mDisplay = new RecordingDisplay();
        mController = new AiSubtitleController(mDisplay);
        mController.openVideo();
        mController.setAiEnabled(true);
    }

    @Test
    public void resultFromTheLiveSessionIsApplied() {
        mController.onSubtitleSourceSelected(source(ORIGIN_URL));
        AiSubtitleController.Token token = mController.currentToken();

        assertTrue(mController.applyTranslations(token, Arrays.asList("\u4f60\u597d")));
        assertEquals(Arrays.asList("\u4f60\u597d"), mDisplay.lastTranslations);
    }

    @Test
    public void resultAfterANewVideoIsIgnored() {
        mController.onSubtitleSourceSelected(source(ORIGIN_URL));
        AiSubtitleController.Token stale = mController.currentToken();

        mController.openVideo();
        mController.onSubtitleSourceSelected(source(ORIGIN_URL));

        assertFalse(mController.applyTranslations(stale, Arrays.asList("\u4f60\u597d")));
        assertEquals(Collections.emptyList(), mDisplay.lastTranslations);
    }

    @Test
    public void resultAfterAReplacedMediaSourceIsIgnored() {
        mController.onSubtitleSourceSelected(source(ORIGIN_URL));
        AiSubtitleController.Token stale = mController.currentToken();

        mController.onMediaSourceReplaced();

        assertFalse(mController.applyTranslations(stale, Arrays.asList("\u4f60\u597d")));
    }

    @Test
    public void resultAfterASubtitleTrackChangeIsIgnored() {
        mController.onSubtitleSourceSelected(source(ORIGIN_URL));
        AiSubtitleController.Token stale = mController.currentToken();

        mController.onSubtitleSourceSelected(source(TRANSLATED_URL));

        assertFalse(mController.applyTranslations(stale, Arrays.asList("\u4f60\u597d")));
        assertFalse("stale result must not be shown", "set:[\u4f60\u597d]".equals(mDisplay.lastAction()));
    }

    @Test
    public void resultAfterSeekToTheSameTextAtAnotherTimeIsIgnored() {
        mController.onSubtitleSourceSelected(source(ORIGIN_URL));
        mController.applyTranslations(mController.currentToken(), Arrays.asList("\u4f60\u597d"));
        AiSubtitleController.Token beforeSeek = mController.currentToken();

        mController.onSeek();

        assertEquals("clear", mDisplay.lastAction());
        assertFalse(mController.applyTranslations(beforeSeek, Arrays.asList("\u4f60\u597d")));
        assertEquals("the cleared frame must stay cleared", "clear", mDisplay.lastAction());
    }

    @Test
    public void staleResultAfterAClearedFrameCannotResurrectIt() {
        mController.onSubtitleSourceSelected(source(ORIGIN_URL));
        AiSubtitleController.Token token = mController.currentToken();

        mController.onSubtitlesDisabled();

        assertFalse(mController.applyTranslations(token, Arrays.asList("\u4f60\u597d")));
        assertEquals(Collections.emptyList(), mDisplay.lastTranslations);
        assertFalse(mController.hasActiveSession());
    }

    @Test
    public void selectingTheSameSourceAgainKeepsTheSession() {
        mController.onSubtitleSourceSelected(source(ORIGIN_URL));
        AiSubtitleController.Token token = mController.currentToken();
        int actionsBefore = mDisplay.actions.size();

        mController.onSubtitleSourceSelected(source(ORIGIN_URL));

        assertTrue(mController.isCurrent(token));
        assertEquals("a brief track change must not clear or repaint", actionsBefore, mDisplay.actions.size());
        assertTrue(mController.applyTranslations(token, Arrays.asList("\u4f60\u597d")));
    }

    @Test
    public void identityIsInvalidatedBeforeInFlightWorkIsCancelled() {
        mController.onSubtitleSourceSelected(source(ORIGIN_URL));
        RecordingCancellable cancellable = new RecordingCancellable(mController);
        cancellable.tokenAtCancel = mController.currentToken();
        mController.register(cancellable);

        mController.openVideo();

        assertTrue(cancellable.cancelled);
        assertEquals(Collections.singletonList(true), cancellable.staleAtCancel);
    }

    @Test
    public void releaseCancelsAndIgnoresLaterCallbacks() {
        mController.onSubtitleSourceSelected(source(ORIGIN_URL));
        AiSubtitleController.Token token = mController.currentToken();
        RecordingCancellable cancellable = new RecordingCancellable(mController);
        cancellable.tokenAtCancel = token;
        mController.register(cancellable);

        mController.release();

        assertTrue(cancellable.cancelled);
        assertFalse(mController.applyTranslations(token, Arrays.asList("\u4f60\u597d")));
        assertFalse(mController.isAiEnabled());
        assertFalse(mController.hasActiveSession());
    }

    @Test
    public void unregisteredWorkIsNotCancelled() {
        RecordingCancellable cancellable = new RecordingCancellable(mController);
        cancellable.tokenAtCancel = mController.currentToken();
        mController.register(cancellable);
        mController.unregister(cancellable);

        mController.openVideo();

        assertFalse(cancellable.cancelled);
    }

    @Test
    public void reopeningTheSameVideoStartsANewSession() {
        mController.onSubtitleSourceSelected(source(ORIGIN_URL));
        AiSubtitleController.Token first = mController.currentToken();

        mController.openVideo();
        mController.onSubtitleSourceSelected(source(ORIGIN_URL));

        assertFalse(mController.isCurrent(first));
        assertTrue(mController.applyTranslations(mController.currentToken(), Arrays.asList("\u4f60\u597d")));
    }

    @Test
    public void modeChangesNeverInvalidateTheSession() {
        mController.onSubtitleSourceSelected(source(ORIGIN_URL));
        AiSubtitleController.Token token = mController.currentToken();

        mController.setDisplayMode(SubtitleComposer.MODE_BILINGUAL);

        assertTrue(mController.isCurrent(token));
        assertEquals(SubtitleComposer.MODE_BILINGUAL, mDisplay.mode);
        assertEquals(SubtitleComposer.MODE_BILINGUAL, mController.getDisplayMode());
    }

    @Test
    public void disablingAiInvalidatesAndEnablingItAgainWorks() {
        mController.onSubtitleSourceSelected(source(ORIGIN_URL));
        AiSubtitleController.Token token = mController.currentToken();

        mController.setAiEnabled(false);

        assertFalse(mController.isAiEnabled());
        assertFalse(mController.applyTranslations(token, Arrays.asList("\u4f60\u597d")));
        assertEquals(Collections.emptyList(), mDisplay.lastTranslations);
        assertEquals(SubtitleComposer.MODE_ORIGINAL_ONLY, mDisplay.mode);

        mController.setAiEnabled(true);
        assertTrue(mController.isAiEnabled());
    }

    @Test
    public void modeChangesWhileAiIsOffAreRememberedForTheNextSession() {
        mController.setAiEnabled(false);
        mController.setDisplayMode(SubtitleComposer.MODE_TRANSLATION_ONLY);

        mController.setAiEnabled(true);

        assertEquals(SubtitleComposer.MODE_TRANSLATION_ONLY, mController.getDisplayMode());
    }

    @Test
    public void emptyTranslationResultClearsTheFrame() {
        mController.onSubtitleSourceSelected(source(ORIGIN_URL));
        mController.applyTranslations(mController.currentToken(), Arrays.asList("\u4f60\u597d"));

        assertTrue(mController.applyTranslations(mController.currentToken(), Collections.<String>emptyList()));

        assertEquals(Collections.emptyList(), mDisplay.lastTranslations);
    }

    @Test
    public void statusFollowsTheSwitchesAndTheSelectedSource() {
        mController.setAiEnabled(false);
        assertEquals(AiSubtitleController.Status.AI_OFF, mController.getStatus());

        mController.setAiEnabled(true);
        assertEquals("AI on, nothing selected yet", AiSubtitleController.Status.NO_SOURCE, mController.getStatus());

        mController.onSubtitleSourceSelected(source(ORIGIN_URL));
        assertEquals(AiSubtitleController.Status.READY, mController.getStatus());

        mController.onSubtitlesDisabled();
        assertEquals(AiSubtitleController.Status.NO_SOURCE, mController.getStatus());

        mController.release();
        assertEquals(AiSubtitleController.Status.AI_OFF, mController.getStatus());
    }

    @Test
    public void configurationChangeVoidsTheOldSessionButKeepsTheSelectedSource() {
        mController.onSubtitleSourceSelected(source(ORIGIN_URL));
        mController.applyTranslations(mController.currentToken(), Arrays.asList("\u4f60\u597d"));
        AiSubtitleController.Token beforeChange = mController.currentToken();

        mController.onConfigurationChanged();

        assertFalse("a result of the old configuration is refused",
                mController.applyTranslations(beforeChange, Arrays.asList("\u4f60\u597d")));
        assertEquals(Collections.emptyList(), mDisplay.lastTranslations);
        assertTrue("the source stays selected", mController.hasActiveSession());
        assertEquals(AiSubtitleController.Status.READY, mController.getStatus());
        assertTrue("a fresh session accepts new results",
                mController.applyTranslations(mController.currentToken(), Arrays.asList("\u4e16\u754c")));
    }

    @Test
    public void noSubtitleSourceMeansNoSession() {
        assertFalse(mController.hasActiveSession());
        assertFalse(mController.applyTranslations(mController.currentToken(), Arrays.asList("\u4f60\u597d")));
        assertEquals(Collections.emptyList(), mDisplay.lastTranslations);
        assertNotNull(mController.currentToken());
    }
}
