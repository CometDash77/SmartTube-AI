package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns one AI subtitle session and blocks every stale write-back.
 *
 * <p>Identity rules (plan 4.1/4.2):
 * <ul>
 *     <li>A new video, a replaced media source, a different subtitle source and a seek each
 *     invalidate the previous identity <em>before</em> in-flight work is cancelled, so a late
 *     callback can never pass the freshness check.</li>
 *     <li>Selecting the same subtitle source again (for example a brief audio/video track change)
 *     keeps the existing session: the same text must not be translated twice.</li>
 *     <li>Subtitles off, AI off and release invalidate the session and clear the display.</li>
 *     <li>A seek clears the current translation immediately and keeps the source session; cached
 *     success for the new position is the scheduler's business (T06).</li>
 * </ul>
 *
 * <p>The class is deliberately free of Android and player dependencies: the caller feeds it events
 * and it decides what may be displayed.
 */
public class AiSubtitleController {
    /** Handle of one in-flight unit of work (network call, snapshot read, timer). */
    public interface Cancellable {
        void cancel();
    }

    /** Identity of one translation session; every result must carry the token it was produced for. */
    public static final class Token {
        private final int mPlayerGeneration;
        private final int mSourceGeneration;
        private final int mTrackGeneration;
        private final int mSeekGeneration;

        private Token(int playerGeneration, int sourceGeneration, int trackGeneration, int seekGeneration) {
            mPlayerGeneration = playerGeneration;
            mSourceGeneration = sourceGeneration;
            mTrackGeneration = trackGeneration;
            mSeekGeneration = seekGeneration;
        }

        @Override
        public String toString() {
            return "Token{p" + mPlayerGeneration + "s" + mSourceGeneration + "t" + mTrackGeneration + "k" + mSeekGeneration + "}";
        }
    }

    /** Coarse state the subtitle menu can show without knowing any transport details. */
    public enum Status {
        /** The AI switch is off for this video. */
        AI_OFF,
        /** AI is on but no subtitle source is selected (no track, or subtitles off). */
        NO_SOURCE,
        /** AI is on and a source session exists: translations may be prepared. */
        READY
    }

    private final SubtitleDisplay mDisplay;
    private final List<Cancellable> mInFlight = new ArrayList<>();
    private int mPlayerGeneration;
    private int mSourceGeneration;
    private int mTrackGeneration;
    private int mSeekGeneration;
    private String mActiveSourceKey; // memory-only identity of the selected source
    private boolean mAiEnabled;
    private int mDisplayMode = SubtitleComposer.MODE_ORIGINAL_ONLY;

    public AiSubtitleController(SubtitleDisplay display) {
        mDisplay = display;
    }

    /** A new video (or a re-open of the same video) always starts a new session. */
    public void openVideo() {
        invalidateSession();
        mPlayerGeneration++;
        mSourceGeneration++;
        mTrackGeneration++;
        mSeekGeneration++;
        mActiveSourceKey = null;
        mDisplay.clearTranslations();
        mDisplay.resetOriginalCueState();
    }

    /** The media source was replaced (new manifest, live refresh, merged source). */
    public void onMediaSourceReplaced() {
        invalidateSession();
        mSourceGeneration++;
        mTrackGeneration++;
        mActiveSourceKey = null;
        mDisplay.clearTranslations();
        mDisplay.resetOriginalCueState();
    }

    /**
     * The player resolved which subtitle source is selected.
     *
     * @param source the bound source, or null when no subtitle track is selected
     */
    public void onSubtitleSourceSelected(SelectedSubtitleSource source) {
        String key = keyOf(source);

        if (key == null) {
            if (mActiveSourceKey != null) {
                invalidateSession();
                mActiveSourceKey = null;
            }

            return;
        }

        if (key.equals(mActiveSourceKey)) {
            return; // same text session: a brief video/audio track change must not rebuild it
        }

        invalidateSession();
        mActiveSourceKey = key;
        mTrackGeneration++;
        mDisplay.clearTranslations();
        mDisplay.resetOriginalCueState();
    }

    /** A seek invalidates pending results and clears the current translation immediately. */
    public void onSeek() {
        mSeekGeneration++;
        cancelInFlight();
        mDisplay.clearTranslations();
    }

    /**
     * Endpoint, model, target language or expression instruction changed: results produced for the
     * old configuration must never be displayed. The selected source stays selected, so the next
     * track or tick event starts a fresh session without the user touching the menu again.
     */
    public void onConfigurationChanged() {
        invalidateSession();
        mDisplay.clearTranslations();
    }

    /** The player reported actual subtitles-off (short CC press). */
    public void onSubtitlesDisabled() {
        onSubtitleSourceSelected(null);
        mDisplay.clearTranslations();
    }

    public void setAiEnabled(boolean enabled) {
        if (mAiEnabled == enabled) {
            return;
        }

        mAiEnabled = enabled;

        if (enabled) {
            mDisplay.setAiDisplayMode(mDisplayMode);
        } else {
            invalidateSession();
            mDisplay.clearTranslations();
            mDisplay.setAiDisplayMode(SubtitleComposer.MODE_ORIGINAL_ONLY);
        }
    }

    public boolean isAiEnabled() {
        return mAiEnabled;
    }

    /** Mode changes only repaint; they never invalidate the session or request anything. */
    public void setDisplayMode(int mode) {
        mDisplayMode = mode;
        mDisplay.setAiDisplayMode(mode);
    }

    public int getDisplayMode() {
        return mDisplayMode;
    }

    /** Engine release / fragment destroy: nothing may be displayed or delivered afterwards. */
    public void release() {
        invalidateSession();
        mPlayerGeneration++;
        mSourceGeneration++;
        mTrackGeneration++;
        mSeekGeneration++;
        mActiveSourceKey = null;
        mAiEnabled = false;
        mDisplay.clearTranslations();
    }

    /** Registers work that must be cancelled when the session becomes stale. */
    public void register(Cancellable cancellable) {
        if (cancellable != null) {
            mInFlight.add(cancellable);
        }
    }

    public void unregister(Cancellable cancellable) {
        mInFlight.remove(cancellable);
    }

    /** Token a request must carry; results of another token are discarded. */
    public Token currentToken() {
        return new Token(mPlayerGeneration, mSourceGeneration, mTrackGeneration, mSeekGeneration);
    }

    public boolean isCurrent(Token token) {
        return token != null
                && token.mPlayerGeneration == mPlayerGeneration
                && token.mSourceGeneration == mSourceGeneration
                && token.mTrackGeneration == mTrackGeneration
                && token.mSeekGeneration == mSeekGeneration;
    }

    /**
     * Applies a translation result only when it still belongs to the current session.
     *
     * @return true when the display was updated
     */
    public boolean applyTranslations(Token token, List<String> translations) {
        if (!isCurrent(token) || !hasActiveSession()) {
            // Stale success or failure, or a result that arrived without a selected source: never
            // repaint and never resurrect a cleared frame.
            return false;
        }

        mDisplay.setTranslations(translations);

        return true;
    }

    /** True while a session identity exists (a source is selected and AI is on). */
    public boolean hasActiveSession() {
        return mActiveSourceKey != null;
    }

    public Status getStatus() {
        if (!mAiEnabled) {
            return Status.AI_OFF;
        }

        return mActiveSourceKey != null ? Status.READY : Status.NO_SOURCE;
    }

    public String getActiveSourceKey() {
        return mActiveSourceKey;
    }

    /** Invalidates the identity first, then cancels: a callback racing with the cancel is stale. */
    private void invalidateSession() {
        mSourceGeneration++;
        mTrackGeneration++;
        mSeekGeneration++;
        cancelInFlight();
    }

    private void cancelInFlight() {
        List<Cancellable> pending = new ArrayList<>(mInFlight);
        mInFlight.clear();

        for (Cancellable cancellable : pending) {
            cancellable.cancel();
        }
    }

    private static String keyOf(SelectedSubtitleSource source) {
        if (source == null) {
            return null;
        }

        // Memory-only identity: the locator is never logged or persisted.
        return source.getGeneration() + "|" + source.getVssId() + "|" + source.getLanguageCode() + "|" + source.getBaseUrl();
    }
}
