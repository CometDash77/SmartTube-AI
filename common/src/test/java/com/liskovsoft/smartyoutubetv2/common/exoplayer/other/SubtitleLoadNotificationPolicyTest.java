package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/** Kiss feature (plan 4.5): which subtitle load state may notify, and when it must stay quiet. */
public class SubtitleLoadNotificationPolicyTest {
    private static final String SOURCE = "gen1|vss|en|https://example.com";

    private SubtitleLoadNotificationPolicy mPolicy;

    @Before
    public void setUp() {
        mPolicy = new SubtitleLoadNotificationPolicy();
        mPolicy.setSourceKey(SOURCE);
    }

    @Test
    public void aRealDownloadNotifiesOncePerSource() {
        assertEquals(SubtitleLoadNotificationPolicy.Stage.LOADING, mPolicy.onSnapshotRequested().getStage());
        assertNull("the same source must not repeat the loading notice", mPolicy.onSnapshotRequested());

        mPolicy.setSourceKey("gen2|vss|en|https://example.com");

        assertEquals("a new identity may notify again", SubtitleLoadNotificationPolicy.Stage.LOADING,
                mPolicy.onSnapshotRequested().getStage());
    }

    @Test
    public void theFirstInstallAnnouncesOriginalReadyAndRepeatsStayQuiet() {
        mPolicy.onSnapshotRequested();

        assertEquals(SubtitleLoadNotificationPolicy.Stage.ORIGINAL_READY,
                mPolicy.onSnapshotSettled("UPLOADED", true, true).getStage());
        assertNull("a repeated install (ALREADY_READY) stays quiet",
                mPolicy.onSnapshotSettled("UPLOADED", true, true));
    }

    @Test
    public void aReusedCancelledOrStaleOutcomeNeverSpeaks() {
        assertNull("REUSED re-attribution is quiet",
                mPolicy.onSnapshotSettled(SubtitleTimelineCoordinator.STATUS_REUSED, true, true));
        assertNull("a cancelled attempt is quiet",
                mPolicy.onSnapshotSettled(SubtitleSnapshotReader.Status.CANCELLED.name(), true, false));
        assertNull("a stale attempt is quiet", mPolicy.onSnapshotSettled("IO_FAILED", false, false));
    }

    @Test
    public void aTerminalFailureIsReportedOnce() {
        assertEquals(SubtitleLoadNotificationPolicy.Stage.LOAD_FAILED,
                mPolicy.onSnapshotSettled("IO_FAILED", true, false).getStage());
        assertNull(mPolicy.onSnapshotSettled("IO_FAILED", true, false));
    }

    @Test
    public void translationStatesAreScopedToTheGeneration() {
        mPolicy.setTranslationGeneration(3);

        assertEquals(SubtitleLoadNotificationPolicy.Stage.TRANSLATION_READY,
                mPolicy.onTranslationShown().getStage());
        assertNull("the same generation never repeats it", mPolicy.onTranslationShown());

        mPolicy.setTranslationGeneration(4);

        assertEquals("a new generation (retranslation or new configuration) may announce again",
                SubtitleLoadNotificationPolicy.Stage.TRANSLATION_READY, mPolicy.onTranslationShown().getStage());
    }

    @Test
    public void aTranslationFailureIsReportedOncePerGeneration() {
        mPolicy.setTranslationGeneration(7);

        assertEquals(SubtitleLoadNotificationPolicy.Stage.TRANSLATION_FAILED,
                mPolicy.onTranslationFailed().getStage());
        assertNull(mPolicy.onTranslationFailed());
    }

    @Test
    public void resetAllowsEverythingAgain() {
        mPolicy.onSnapshotRequested();
        mPolicy.setTranslationGeneration(1);
        mPolicy.onTranslationShown();

        mPolicy.reset();

        assertEquals(SubtitleLoadNotificationPolicy.Stage.LOADING, mPolicy.onSnapshotRequested().getStage());
        assertNotNull(mPolicy.onTranslationShown());
    }
}
