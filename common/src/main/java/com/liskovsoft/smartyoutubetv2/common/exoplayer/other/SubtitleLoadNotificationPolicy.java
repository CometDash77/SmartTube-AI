package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import java.util.HashSet;
import java.util.Set;

/**
 * Decides which subtitle load state may interrupt the user with a short non-modal toast (plan 4.5).
 *
 * <p>The policy is a pure decision class: it never touches Android UI, never starts work and never
 * reads a clock. The caller feeds it only accepted events (a stale callback must have been dropped
 * before it reaches here) and shows a toast only for a non-null result.
 *
 * <p>Rules from the plan:
 * <ul>
 *     <li>One state is shown at most once per source identity and translation generation
 *     ("generation + stage + result"), so repeated batches or repeated events of the same source
 *     never repeat a toast.</li>
 *     <li>ALREADY_READY, REUSED and CANCELLED are quiet: only a real new download says "loading"
 *     and only the first real install says "original ready".</li>
 *     <li>Translation states are scoped to the current translation generation, so a configuration
 *     change or a forced retranslation may announce them again.</li>
 * </ul>
 */
public class SubtitleLoadNotificationPolicy {
    /** Stage of one allowed notification; the UI maps it to text. */
    public enum Stage {
        /** A real download of the selected source has started. */
        LOADING,
        /** The original timeline of the selected source was installed. */
        ORIGINAL_READY,
        /** The original download ended in a terminal failure. */
        LOAD_FAILED,
        /** A translation became visible for the first time in this generation. */
        TRANSLATION_READY,
        /** A translation batch ended in a terminal failure in this generation. */
        TRANSLATION_FAILED
    }

    /** One allowed notification; the UI maps the stage to text. */
    public static final class Notice {
        private final Stage mStage;

        private Notice(Stage stage) {
            mStage = stage;
        }

        public Stage getStage() {
            return mStage;
        }

        @Override
        public String toString() {
            return "Notice{" + mStage + "}";
        }
    }

    private final Set<String> mShown = new HashSet<>();
    private String mSourceKey;
    private int mTranslationGeneration;

    /**
     * Binds the policy to one source identity; a new identity starts its own dedupe window.
     * The controller's active source key already contains the video/track generations, so a new
     * video is a new identity even when the track looks identical.
     */
    public void setSourceKey(String sourceKey) {
        if (sourceKey == null ? mSourceKey == null : sourceKey.equals(mSourceKey)) {
            return;
        }

        mSourceKey = sourceKey;
        mShown.clear();
    }

    /** The translation generation the translation states belong to (plan 4.5). */
    public void setTranslationGeneration(int generation) {
        mTranslationGeneration = generation;
    }

    /** Drops all state (release): the next event may notify again. */
    public void reset() {
        mSourceKey = null;
        mTranslationGeneration = 0;
        mShown.clear();
    }

    /** A real snapshot download of the selected source has started. */
    public Notice onSnapshotRequested() {
        return once(Stage.LOADING, false);
    }

    /**
     * The snapshot attempt settled. A reused install never speaks (the source already announced
     * itself), a cancelled attempt stays quiet, and only the first install or the first terminal
     * failure of the identity may notify.
     */
    public Notice onSnapshotSettled(String status, boolean accepted, boolean installed) {
        if (!accepted) {
            return null; // a stale attempt must not speak about the current source
        }

        if (SubtitleTimelineCoordinator.STATUS_REUSED.equals(status)) {
            return null; // the payload was already installed and announced earlier
        }

        if (installed) {
            return once(Stage.ORIGINAL_READY, false);
        }

        if (SubtitleSnapshotReader.Status.CANCELLED.name().equals(status)) {
            return null;
        }

        return once(Stage.LOAD_FAILED, false);
    }

    /** A translation became visible for the current generation. */
    public Notice onTranslationShown() {
        return once(Stage.TRANSLATION_READY, true);
    }

    /** A translation batch failed terminally in this generation. */
    public Notice onTranslationFailed() {
        return once(Stage.TRANSLATION_FAILED, true);
    }

    private Notice once(Stage stage, boolean generationScoped) {
        String key = (mSourceKey == null ? "-" : mSourceKey)
                + (generationScoped ? "|g" + mTranslationGeneration : "")
                + "|" + stage;

        if (!mShown.add(key)) {
            return null;
        }

        return new Notice(stage);
    }
}
