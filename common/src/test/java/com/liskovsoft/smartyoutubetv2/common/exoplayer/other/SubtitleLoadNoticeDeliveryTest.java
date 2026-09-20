package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Plan 4.5 regression: a notice is accepted when the event happens but must be re-checked when it is
 * really delivered, because the user may have closed the switch, the generation may have moved on and
 * the surface may have been released in the meantime.
 */
public class SubtitleLoadNoticeDeliveryTest {
    private FakePoster mPoster;
    private FakeState mState;
    private RecordingUi mUi;
    private SubtitleLoadNoticeDelivery mDelivery;

    private static class FakePoster implements SubtitleLoadNoticeDelivery.Poster {
        private final List<Runnable> mTasks = new ArrayList<>();

        @Override
        public void post(Runnable task) {
            mTasks.add(task);
        }

        void runAll() {
            List<Runnable> tasks = new ArrayList<>(mTasks);
            mTasks.clear();

            for (Runnable task : tasks) {
                task.run();
            }
        }
    }

    private static class FakeState implements SubtitleLoadNoticeDelivery.SessionState {
        private String sourceKey = "s1";
        private int generation = 1;
        private boolean aiEnabled = true;
        private boolean notifications = true;

        @Override
        public String getSourceKey() {
            return sourceKey;
        }

        @Override
        public int getTranslationGeneration() {
            return generation;
        }

        @Override
        public boolean isAiEnabled() {
            return aiEnabled;
        }

        @Override
        public boolean showsNotifications() {
            return notifications;
        }
    }

    private static class RecordingUi implements SubtitleLoadNoticeDelivery.Ui {
        private final List<String> notices = new ArrayList<>();
        private int cleared;

        @Override
        public void onNotice(SubtitleLoadNotificationPolicy.Stage stage) {
            notices.add(stage.name());
        }

        @Override
        public void onCleared() {
            cleared++;
        }
    }

    @Before
    public void setUp() {
        mPoster = new FakePoster();
        mState = new FakeState();
        mUi = new RecordingUi();
        mDelivery = new SubtitleLoadNoticeDelivery(mPoster, mState);
        mDelivery.setUi(mUi);
    }

    private SubtitleLoadNotificationPolicy.Notice loadingNotice() {
        SubtitleLoadNotificationPolicy policy = new SubtitleLoadNotificationPolicy();
        policy.setSourceKey(mState.sourceKey);

        return policy.onSnapshotRequested();
    }

    private SubtitleLoadNotificationPolicy.Notice translationNotice() {
        SubtitleLoadNotificationPolicy policy = new SubtitleLoadNotificationPolicy();
        policy.setSourceKey(mState.sourceKey);
        policy.setTranslationGeneration(mState.generation);

        return policy.onTranslationShown();
    }

    @Test
    public void anAcceptedNoticeAppearsWhenTheStateIsStillLive() {
        assertTrue(mDelivery.show(loadingNotice()));

        mPoster.runAll();

        assertEquals(Collections.singletonList("LOADING"), mUi.notices);
    }

    @Test
    public void closingTheNotificationSwitchBeforeDeliveryDropsTheQueuedNotice() {
        assertTrue(mDelivery.show(loadingNotice()));

        mState.notifications = false;
        mPoster.runAll();

        assertEquals("the switch is read when the notice would appear", Collections.<String>emptyList(),
                mUi.notices);
    }

    @Test
    public void aNoticeOfAnOldContentGenerationIsNotDelivered() {
        assertTrue(mDelivery.show(translationNotice()));

        mState.generation = 2; // configuration change or forced retranslation
        mPoster.runAll();

        assertEquals(Collections.<String>emptyList(), mUi.notices);
    }

    @Test
    public void aNoticeOfAnotherSourceIsNotDelivered() {
        assertTrue(mDelivery.show(loadingNotice()));

        mState.sourceKey = "s2";
        mPoster.runAll();

        assertEquals(Collections.<String>emptyList(), mUi.notices);
    }

    @Test
    public void aiOffSuppressesTranslationStagesButNotTheOriginalOnes() {
        assertTrue(mDelivery.show(translationNotice()));
        assertTrue(mDelivery.show(loadingNotice()));

        mState.aiEnabled = false;
        mPoster.runAll();

        assertEquals("a translation notice must not survive AI being switched off",
                Collections.singletonList("LOADING"), mUi.notices);
    }

    @Test
    public void revocationDropsPendingNoticesAndClearsTheVisibleOne() {
        assertTrue(mDelivery.show(loadingNotice()));

        mDelivery.revoke();
        mPoster.runAll();

        assertEquals(Collections.<String>emptyList(), mUi.notices);
        assertEquals("the toast already on screen is hidden too", 1, mUi.cleared);

        // A notice queued after the revocation may appear again (the switch may be reopened).
        mState.notifications = true;
        assertTrue(mDelivery.show(loadingNotice()));
        mPoster.runAll();

        assertEquals(Collections.singletonList("LOADING"), mUi.notices);
    }

    @Test
    public void aDetachedDisplaySideShowsNothing() {
        mDelivery.setUi(null);

        assertTrue(mDelivery.show(loadingNotice()));
        mPoster.runAll();

        assertFalse(mDelivery.show(null));

        assertEquals(Collections.<String>emptyList(), mUi.notices);
        assertEquals(0, mUi.cleared);
    }

    @Test
    public void aTranslationNoticeOfTheLiveGenerationIsDelivered() {
        assertTrue(mDelivery.show(translationNotice()));

        // The dedupe lives in the policy; this class only rejects a notice whose session identity
        // moved on (source or generation), so the live generation still appears.
        mPoster.runAll();

        assertEquals(Arrays.asList("TRANSLATION_READY"), mUi.notices);
    }
}
