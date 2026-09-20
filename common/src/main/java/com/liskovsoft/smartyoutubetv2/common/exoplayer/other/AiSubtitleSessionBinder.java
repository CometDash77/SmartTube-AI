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

    /**
     * Notified after a repaint really put at least one accepted translation on the current frame.
     *
     * <p>This is the only event the load-notification policy may use for "the current segment is
     * translated": a batch of only future items reports nothing here, and the frame speaks for itself
     * when the player later reaches it from the cache (plan 4.5).
     */
    public interface TranslationDisplayListener {
        void onTranslationDisplayed();
    }

    /** Supplies whether this configuration can translate at all (a key is configured). */
    public interface TranslationAvailability {
        boolean isAvailable();
    }

    private final AiSubtitleController mController;
    private final SubtitleDisplay mDisplay;
    private final SubtitleSourceProvider mSourceProvider;
    private TickTarget mTickTarget;
    private SubtitleTranslationDispatcher mDispatcher;
    private SubtitleTranslationCache mTranslationCache;
    private SubtitleSessionContext mSessionContext;
    /** The raw snapshot of the source: the export and the fallback always use this one. */
    private SubtitleTimeline mRawTimeline;
    /** Derived sentences of the same source, or null while the rule is off or unusable. */
    private SubtitleTimeline mDerivedTimeline;
    /** The timeline the display and the prefetch consume right now (raw, or derived when active). */
    private SubtitleTimeline mActiveTimeline;
    private boolean mRuleSegmentation;
    private TranslationAvailability mAvailability;
    /** True while the derived sentence frame owns the screen; used for an immediate raw fallback. */
    private boolean mDerivedApplied;
    /** Source key the installed timeline belongs to; a mismatch means "not exportable yet". */
    private String mTimelineSourceKey;
    private List<SubtitleItem> mFrameItems = Collections.emptyList();
    /** Non-blank translations the last current-frame application actually put into slots. */
    private int mLastAppliedTranslationCount;
    private TranslationDisplayListener mTranslationDisplayListener;

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

        if (mSessionContext != null) {
            mSessionContext.reset(); // examples and summary belong to the previous video (plan 4.2)
        }

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
        dropDerivedDisplay(); // the sentence of the old position must not stay on screen
        mDisplay.resetOriginalCueState();

        if (mSessionContext != null) {
            // The frozen summary survives a seek; the examples before the new position must not leak
            // into the text that follows it (plan 4.2).
            mSessionContext.resetExamples();
        }
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
        installActiveTimeline(); // AI off hands the original text back to the native cues
    }

    /**
     * Endpoint, model, target language, instruction, context tier or segmentation rules changed: one
     * content transaction. The session identity moves on first, then the in-flight batch is abandoned
     * together with the translations of the old configuration, so neither a late answer nor a cached
     * result of the previous setup can be displayed or reused (plan 4.1/6.3).
     */
    public void onConfigurationChanged() {
        mController.onConfigurationChanged();

        if (mDispatcher != null) {
            mDispatcher.invalidateContent();
        }

        if (mTranslationCache != null) {
            mTranslationCache.clear();
        }

        if (mSessionContext != null) {
            mSessionContext.reset(); // the summary was produced for the old analysis configuration
        }
    }

    /**
     * Forces retranslation of the current source (Kiss feature, plan 4.4): one transaction on the
     * current identity.
     *
     * <p>The content generation moves on first, so a late answer of the previous generation can
     * neither repaint nor enter the cache; then the stored translations, the failure budget and the
     * coherent examples of this source are dropped, while the original timeline and the frozen summary
     * survive (they are independent of the translation results). Finally the planner is reset so the
     * current window is requested again from the current position. The original subtitles stay on
     * screen, because an emptied cache composes back to the original text.
     *
     * @return true when the transaction ran; false when AI is off, no source is bound or no timeline
     *         was obtained yet
     */
    public boolean retranslateCurrentSource() {
        if (!mController.isAiEnabled() || !mController.hasActiveSession() || mActiveTimeline == null) {
            return false;
        }

        // Identity first (plan 4.1), then abandon the in-flight batch, then drop its results.
        mController.onTranslationGenerationBumped();

        if (mDispatcher != null) {
            mDispatcher.invalidateContent();
        }

        if (mTranslationCache != null) {
            mTranslationCache.clear(); // every stored success and the whole failure budget
        }

        if (mSessionContext != null) {
            mSessionContext.resetExamples(); // the frozen summary is kept, the examples are not
        }

        if (mDispatcher != null) {
            mDispatcher.setTimeline(mActiveTimeline); // planner reset: the window is planned again
        }

        applyCurrentFrame(); // with an empty cache this composes the original text again

        return true;
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
        installActiveTimeline(); // "original only" uses the native cues, the other modes the sentences
    }

    /** Engine release / player destroy. */
    public void onEngineReleased() {
        mController.release();
        cancelDispatcher();
        mDerivedApplied = false;
        mRawTimeline = null;
        mDerivedTimeline = null;
        mActiveTimeline = null;
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

        if (dispatcher != null && mSessionContext != null) {
            dispatcher.setSessionContext(mSessionContext);
        }

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
     * Installs the session context material of plan 4.2: verified examples are fed by the dispatcher,
     * the frozen summary by the context analysis.
     */
    public void setSessionContext(SubtitleSessionContext sessionContext) {
        mSessionContext = sessionContext;

        if (mDispatcher != null) {
            mDispatcher.setSessionContext(sessionContext);
        }
    }

    /** Installs the listener that is told when a repaint really showed a translation (plan 4.5). */
    public void setTranslationDisplayListener(TranslationDisplayListener listener) {
        mTranslationDisplayListener = listener;
    }

    /**
     * Installs the subtitle timeline of the current source. A new timeline (new source or reload)
     * replaces the previous one; the caller must also tell the controller about the source change.
     */
    public void setTimeline(SubtitleTimeline timeline) {
        mRawTimeline = timeline;
        mDerivedTimeline = null; // a new source has no derived sentences yet
        mTimelineSourceKey = timeline != null ? mController.getActiveSourceKey() : null;
        installActiveTimeline();
    }

    /**
     * Installs the rule-segmented sentences of the same source (plan 4.3), or null when segmentation
     * is off or its derived timeline was refused. A different timeline means different item ids, so
     * the planner starts over instead of inheriting bookkeeping of the previous unit set.
     */
    public void setDerivedTimeline(SubtitleTimeline derived) {
        mDerivedTimeline = derived;
        installActiveTimeline();
    }

    /** The user's rule-segmentation switch; it only takes effect with a key, AI on and a translated mode. */
    public void setRuleSegmentation(boolean enabled) {
        mRuleSegmentation = enabled;
        installActiveTimeline();
    }

    /** Supplies whether a key is configured; without one the native cues stay on screen. */
    public void setTranslationAvailability(TranslationAvailability availability) {
        mAvailability = availability;
        installActiveTimeline();
    }

    /** True while derived sentences are the original text on screen (plan 4.3.6). */
    public boolean isDerivedDisplayActive() {
        return mDerivedTimeline != null && mRuleSegmentation && mController.isAiEnabled()
                && mController.getDisplayMode() != SubtitleComposer.MODE_ORIGINAL_ONLY
                && mAvailability != null && mAvailability.isAvailable();
    }

    /**
     * Pushes the derived sentence of the position to the display without starting any network work.
     * The caller drives this from the segment boundaries (never from the one-second prefetch tick, so
     * a sentence cannot stay on screen after it ended).
     */
    public void refreshDerivedDisplay(long positionMs) {
        if (!isDerivedDisplayActive()) {
            dropDerivedDisplay();

            return;
        }

        SubtitleFrame frame = mActiveTimeline != null ? mActiveTimeline.frameAt(positionMs * 1_000L) : null;
        showDerivedFrame(frame);
    }

    /**
     * Writes one derived sentence frame through the single display entry: the previous translations
     * are dropped first, so no text of another sentence (or of the raw cue) can be composed onto it.
     */
    private void showDerivedFrame(SubtitleFrame frame) {
        onFrameItems(frame != null ? frame.getItems() : Collections.<SubtitleItem>emptyList());

        List<String> lines = new ArrayList<>();

        if (frame != null) {
            for (SubtitleItem item : frame.getItems()) {
                lines.add(item.getText());
            }
        }

        mDisplay.clearTranslations();
        mDisplay.setDerivedOriginalLines(lines);
        mDerivedApplied = true;
        applyCurrentFrame();
    }

    /** Switches the display between derived sentences and the native cues in one place. */
    private void installActiveTimeline() {
        SubtitleTimeline active = isDerivedDisplayActive() ? mDerivedTimeline : mRawTimeline;
        boolean timelineChanged = active != mActiveTimeline;
        mActiveTimeline = active;

        if (timelineChanged) {
            // The tracked frame belonged to the previous timeline; until the next tick recomputes it,
            // a stray repaint must not write cache entries of an unrelated frame.
            onFrameItems(null);
        }

        if (mDispatcher != null) {
            mDispatcher.setTimeline(active);
        }

        if (!isDerivedDisplayActive()) {
            dropDerivedDisplay();
        }
    }

    /** Hands the screen back to the native cue path, which is buffered in the display. */
    private void dropDerivedDisplay() {
        if (!mDerivedApplied) {
            return;
        }

        mDerivedApplied = false;
        mDisplay.clearTranslations();
        mDisplay.setDerivedOriginalLines(null); // the buffered native text appears immediately
    }

    /**
     * The timeline installed for the current source, or null while no snapshot was obtained.
     *
     * <p>An export takes this reference at click time: a later source change replaces the field with
     * a different immutable timeline, so the already started export keeps its own snapshot.
     */
    public SubtitleTimeline getTimeline() {
        return mRawTimeline;
    }

    /** The timeline the display and the prefetch consume (raw, or the derived sentences). */
    public SubtitleTimeline getActiveTimeline() {
        return mActiveTimeline;
    }

    /** The rule-segmented sentences of the current source, or null when the rule is off or refused. */
    public SubtitleTimeline getDerivedTimeline() {
        return mDerivedTimeline;
    }

    /**
     * The installed timeline only when it belongs to the subtitle source that is selected right now.
     *
     * <p>The timeline describes one source's payload, so it stays bound to that source even when the
     * player moves on: a track or manifest change makes this return null until the fetch for the new
     * source installed its own timeline. That is what keeps an export from writing the previous
     * track's text, without disturbing the display or prefetch paths (which keep using
     * {@link #getTimeline()}).
     */
    public SubtitleTimeline getTimelineOfCurrentSource() {
        String currentKey = mController.getActiveSourceKey();

        return mRawTimeline != null && currentKey != null && currentKey.equals(mTimelineSourceKey)
                ? mRawTimeline
                : null;
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
        SubtitleFrame frame = mActiveTimeline != null
                ? mActiveTimeline.frameAt(positionMs * 1_000L) : null;

        if (isDerivedDisplayActive()) {
            // The boundary ticker drives the precise updates; this keeps the safety net in step, so a
            // missed wakeup can never leave the previous sentence on screen for the whole video.
            showDerivedFrame(frame);
        } else {
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
        List<String> aligned = SubtitleFrameTranslations.align(items, lookup);
        int applied = 0;

        for (String translation : aligned) {
            if (translation != null && !translation.trim().isEmpty()) {
                applied++;
            }
        }

        mLastAppliedTranslationCount = applied;

        boolean displayed = mController.applyTranslations(mController.currentToken(), aligned);

        // The event belongs to the actual accept-and-display entry, not to the network callback: a
        // batch of only future items (applied == 0) stays silent here and speaks when the player
        // reaches that frame and this method really shows its cached translation (plan 4.5).
        if (displayed && applied > 0 && mTranslationDisplayListener != null) {
            mTranslationDisplayListener.onTranslationDisplayed();
        }

        return displayed;
    }

    /**
     * Non-blank translations the last {@link #applyCurrentFrame()} / alignment actually put into
     * the current frame's slots; a success batch of only future items reports zero (plan 4.5).
     */
    public int getLastAppliedTranslationCount() {
        return mLastAppliedTranslationCount;
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

            if (mSessionContext != null) {
                mSessionContext.reset(); // another track has other text and another summary
            }
        }
    }
}
