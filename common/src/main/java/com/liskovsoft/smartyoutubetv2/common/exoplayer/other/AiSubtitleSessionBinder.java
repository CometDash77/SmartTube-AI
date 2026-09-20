package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Maps player events onto one {@link AiSubtitleController} session.
 *
 * <p>The player stack owns events, the UI owns the {@link SubtitleDisplay} and the engine owns the
 * selected source; this class is the single seam that joins them, so every caller uses the same
 * ordering and no caller can forget to reset the original-text state on seek.
 *
 * <p>Event order per plan 4.2: a video/source change invalidates the old identity first, then the
 * new source is resolved; a seek clears the current translation and drops the incremental
 * original-text state so a line buffered for the old position cannot strip text at the new one.
 */
public class AiSubtitleSessionBinder implements SubtitlePrefetchLoop.Pipeline {
    /** Supplies the currently bound subtitle source, or null when subtitles are off. */
    public interface SubtitleSourceProvider {
        SelectedSubtitleSource getSelectedSubtitleSource();
    }

    /** Periodic driver of the prefetch loop, fed in microseconds. */
    public interface TickTarget {
        boolean tick(long positionUs);
    }

    private final AiSubtitleController mController;
    private final SubtitleDisplay mDisplay;
    private final SubtitleSourceProvider mSourceProvider;
    private TickTarget mTickTarget;
    private SubtitleTranslationDispatcher mDispatcher;
    private SubtitleTranslationCache mTranslationCache;
    private SubtitleTimeline mTimeline;
    private List<SubtitleItem> mFrameItems = Collections.emptyList();

    public AiSubtitleSessionBinder(SubtitleDisplay display, SubtitleSourceProvider sourceProvider) {
        mDisplay = display;
        mSourceProvider = sourceProvider;
        mController = new AiSubtitleController(display);
    }

    public AiSubtitleController getController() {
        return mController;
    }

    public SubtitleDisplay getDisplay() {
        return mDisplay;
    }

    /** A new video opened (including re-opening the same one). */
    public void onVideoLoaded() {
        mController.openVideo();
        refreshSource();
    }

    /** The player replaced the media source (new manifest, live refresh, merged source). */
    public void onSourceChanged() {
        mController.onMediaSourceReplaced();
        refreshSource();
    }

    /** A track selection changed: re-resolve which subtitle source is now playing. */
    public void onTrackChanged() {
        refreshSource();
    }

    /** The seek finished; the display was already cleared by the controller. */
    public void onSeekEnd() {
        mController.onSeek();
        cancelDispatcher(); // an in-flight batch belongs to the abandoned position
        mDisplay.resetOriginalCueState();
    }

    /**
     * Short CC press / overlays. Hidden subtitles end the session without losing the AI intent;
     * showing them again re-resolves the source and repaints whatever is already cached, so a
     * re-enabled subtitle track does not have to wait for a new request.
     */
    public void onSubtitlesShown(boolean shown) {
        if (!shown) {
            mController.onSubtitlesDisabled();

            return;
        }

        refreshSource();
        applyCurrentFrame();
    }

    public void onAiEnabled(boolean enabled) {
        mController.setAiEnabled(enabled);
    }

    public void onConfigurationChanged() {
        mController.onConfigurationChanged();
    }

    /** Language code of the subtitle source currently selected, or null when none is bound. */
    public String getActiveSourceLanguage() {
        SelectedSubtitleSource source = mSourceProvider != null ? mSourceProvider.getSelectedSubtitleSource() : null;

        return source != null ? source.getLanguageCode() : null;
    }

    /** Coarse status for the subtitle menu. */
    public AiSubtitleController.Status getStatus() {
        return mController.getStatus();
    }

    public void onDisplayMode(int mode) {
        mController.setDisplayMode(mode);
    }

    /** Engine release / player destroy. */
    public void onEngineReleased() {
        mController.release();
        cancelDispatcher();
    }

    /** Installs the prefetch driver; it is only called while AI subtitles are on. */
    public void setTickTarget(TickTarget tickTarget) {
        mTickTarget = tickTarget;
    }

    /**
     * Installs the prefetch driver. The dispatcher keeps its own single-call gate, so the binder only
     * forwards the player position and never decides when a request may start.
     */
    public void installDispatcher(SubtitleTranslationDispatcher dispatcher) {
        mDispatcher = dispatcher;

        if (dispatcher != null) {
            setTickTarget(positionUs -> {
                dispatcher.setPosition(positionUs);

                return dispatcher.tick();
            });
        }
    }

    /** Pauses prefetch after a failed attempt, for example a rate limit's Retry-After. */
    public void onRetryDelay(long delayMs) {
        if (mDispatcher != null) {
            mDispatcher.pauseFor(delayMs);
        }
    }

    /**
     * Builds the periodic loop that drives this binder on the given scheduler, so the player side
     * only has to start and stop it.
     */
    public SubtitlePrefetchLoop createLoop(SubtitlePrefetchTicker.Scheduler scheduler,
                                           SubtitlePrefetchLoop.PositionSource positionSource) {
        return new SubtitlePrefetchLoop(scheduler, this, positionSource);
    }

    /** Installs the bounded session cache used for display hand-off. */
    public void setTranslationCache(SubtitleTranslationCache translationCache) {
        mTranslationCache = translationCache;
    }

    /**
     * Installs the subtitle timeline of the current source. A new timeline (new source or reload)
     * replaces the previous one; the caller must also tell the controller about the source change.
     */
    public void setTimeline(SubtitleTimeline timeline) {
        mTimeline = timeline;
        // The tracked frame belonged to the previous timeline; until the next tick recomputes it, a
        // stray repaint must not write cache entries of an unrelated frame.
        onFrameItems(null);

        if (mDispatcher != null) {
            mDispatcher.setTimeline(timeline);
        }
    }

    /**
     * The timeline installed for the current source, or null while no snapshot was obtained.
     *
     * <p>An export takes this reference at click time: a later source change replaces the field with
     * a different immutable timeline, so the already started export keeps its own snapshot.
     */
    public SubtitleTimeline getTimeline() {
        return mTimeline;
    }

    /** Remembers the items of the frame the player is showing right now. */
    public void onFrameItems(List<SubtitleItem> items) {
        mFrameItems = items == null ? Collections.<SubtitleItem>emptyList() : new ArrayList<>(items);
    }

    /** The items of the frame currently on screen (for diagnostics and tests). */
    public List<SubtitleItem> getCurrentFrameItems() {
        return new ArrayList<>(mFrameItems);
    }

    /**
     * Writes the stored translations of the current frame, if AI is on and the frame still belongs
     * to the live session.
     *
     * @return true when the display was updated
     */
    @Override
    public boolean applyCurrentFrame() {
        if (!mController.isAiEnabled() || mTranslationCache == null || mFrameItems.isEmpty()) {
            return false;
        }

        return applyFrameTranslations(mFrameItems, mTranslationCache.asLookup());
    }

    /**
     * Feeds the player position to the prefetch driver.
     *
     * <p>The player reports milliseconds; the subtitle timeline is in microseconds, so the single
     * conversion happens here and nowhere else.
     *
     * @return true when the driver started work
     */
    @Override
    public boolean onTick(long positionMs) {
        if (mTickTarget == null || !mController.isAiEnabled()) {
            return false;
        }

        // Track which items the frame on screen shows, so a cached translation can be written to the
        // right slot; the timeline is in microseconds, like the dispatcher.
        if (mTimeline != null) {
            SubtitleFrame frame = mTimeline.frameAt(positionMs * 1_000L);
            onFrameItems(frame != null ? frame.getItems() : Collections.<SubtitleItem>emptyList());
        }

        return mTickTarget.tick(positionMs * 1_000L);
    }

    /**
     * Pushes the stored translations of the currently displayed frame, if that frame still belongs
     * to the live session.
     *
     * @param items  items of the frame the player is showing right now
     * @param lookup stored translations by stable item id
     * @return true when the display was updated
     */
    public boolean applyFrameTranslations(java.util.List<SubtitleItem> items, SubtitleFrameTranslations.TranslationLookup lookup) {
        return mController.applyTranslations(mController.currentToken(),
                SubtitleFrameTranslations.align(items, lookup));
    }

    /** Abandons the in-flight translation attempt, if the dispatcher is installed. */
    private void cancelDispatcher() {
        if (mDispatcher != null) {
            mDispatcher.cancel();
        }
    }

    private void refreshSource() {
        String previousKey = mController.getActiveSourceKey();
        mController.onSubtitleSourceSelected(mSourceProvider != null ? mSourceProvider.getSelectedSubtitleSource() : null);
        String currentKey = mController.getActiveSourceKey();

        if (previousKey == null ? currentKey != null : !previousKey.equals(currentKey)) {
            cancelDispatcher(); // the selected source changed: a batch of the old one belongs to it
            // The timeline of the previous source must never be exported (or prefetched) as the new
            // source's text; the next fetch installs the timeline that belongs to this source.
            setTimeline(null);
        }
    }
}
