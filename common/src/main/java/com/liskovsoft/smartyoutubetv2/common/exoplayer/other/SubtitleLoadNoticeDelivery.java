package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

/**
 * Queues accepted subtitle load states for the player UI and re-checks the live session at delivery.
 *
 * <p>An event is accepted when it happens, but the toast appears a moment later on the UI thread. By
 * then the user may have closed the notification switch, the surface may have been released, or the
 * session may have moved to another source or translation generation. The identity captured when the
 * notice was queued must still match the live session, and the switches are read at delivery time
 * rather than at queue time (plan 4.5). A revocation - the notification switch was closed or the
 * engine was released - drops everything still queued and hides the notice already on screen.
 *
 * <p>The class owns no Android or player type: the poster, the live state and the display side are
 * seams, so the delivery rules are testable without a device.
 */
public class SubtitleLoadNoticeDelivery {
    /** Posts a task to the UI thread. */
    public interface Poster {
        void post(Runnable task);
    }

    /** Live session state, deliberately read at delivery time instead of being captured. */
    public interface SessionState {
        /** Identity of the selected subtitle source, or null when none is bound. */
        String getSourceKey();

        /** Content generation of the translations (plan 4.1). */
        int getTranslationGeneration();

        /** The per-video AI switch. */
        boolean isAiEnabled();

        /** The user's notification switch. */
        boolean showsNotifications();
    }

    /** The display side: at most one short non-modal notice is visible at a time. */
    public interface Ui {
        void onNotice(SubtitleLoadNotificationPolicy.Stage stage);

        /** Hide the notice that is currently on screen. */
        void onCleared();
    }

    private final Poster mPoster;
    private final SessionState mState;
    private Ui mUi;
    private int mRevocation;

    public SubtitleLoadNoticeDelivery(Poster poster, SessionState state) {
        mPoster = poster;
        mState = state;
    }

    /** Attaches or detaches the display side; a detached delivery shows nothing. */
    public void setUi(Ui ui) {
        mUi = ui;
    }

    /**
     * Queues one accepted notice; the decision to really show it is taken at delivery time.
     *
     * @return true when the notice was queued, false for a null notice
     */
    public boolean show(final SubtitleLoadNotificationPolicy.Notice notice) {
        if (notice == null || mPoster == null) {
            return false;
        }

        final int revocation = mRevocation;
        final SubtitleLoadNotificationPolicy.Stage stage = notice.getStage();
        final SessionState state = mState;
        final String sourceKey = state != null ? state.getSourceKey() : null;
        final int translationGeneration = state != null ? state.getTranslationGeneration() : 0;

        mPoster.post(() -> {
            Ui ui = mUi;

            if (ui == null || revocation != mRevocation) {
                return; // detached, or revoked while this notice was queued
            }

            if (state == null || !state.showsNotifications()) {
                return; // the switch is read when the notice would really appear
            }

            String currentKey = state.getSourceKey();

            if (sourceKey == null ? currentKey != null : !sourceKey.equals(currentKey)) {
                return; // the session moved to another source: an old notice must not appear
            }

            boolean translationStage = stage == SubtitleLoadNotificationPolicy.Stage.TRANSLATION_READY
                    || stage == SubtitleLoadNotificationPolicy.Stage.TRANSLATION_FAILED;

            if (translationStage
                    && (!state.isAiEnabled() || translationGeneration != state.getTranslationGeneration())) {
                return; // AI off, or the content generation moved on before delivery
            }

            ui.onNotice(stage);
        });

        return true;
    }

    /**
     * Revokes everything queued and hides the visible notice. Called when the user closes the
     * notification switch and on engine release (plan 4.5): a stale toast must never survive.
     */
    public void revoke() {
        mRevocation++;

        Ui ui = mUi;

        if (ui != null) {
            ui.onCleared();
        }
    }
}
