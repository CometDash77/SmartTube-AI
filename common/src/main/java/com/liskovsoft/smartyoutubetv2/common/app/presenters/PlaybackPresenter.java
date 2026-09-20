package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.AiSubtitleHost;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.AiSubtitleSessionBinder;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.AppPrefsSubtitleAiBackend;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleAiPrefsStore;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleAiSettingsController;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleBatchPlanner;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleHandlerScheduler;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitlePrefetchLoop;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SelectedSubtitleSource;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleSnapshotFetcher;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleSnapshotReader;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleOkHttpTranslationClient;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleKeyStores;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleTranslationCache;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleTranslationDispatcher;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleTranslationStats;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleTranslationService;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleDisplay;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleDiagnosticEnvironment;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleExportController;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleExportEventLog;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleExportFileStore;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleExportSnapshot;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleExportWriteOutcome;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleComposer;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleTimeline;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;

import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.BasePlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.AutoFrameRateController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.ChatController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.CommentsController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.ErrorFixerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.SponsorBlockController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.HQDialogController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.PlayerUIController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.RemoteController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.SuggestionsController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.VideoLoaderController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.VideoStateController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.ViewEventListener;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils.ChainProcessor;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils.Processor;
import com.liskovsoft.googlecommon.common.helpers.ServiceHelper;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class PlaybackPresenter extends BasePresenter<PlaybackView> implements PlayerEventListener {
    private static final String TAG = PlaybackPresenter.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static PlaybackPresenter sInstance;
    private final List<PlayerEventListener> mEventListeners = new CopyOnWriteArrayList<PlayerEventListener>() {
        @Override
        public boolean add(PlayerEventListener listener) {
            ((BasePlayerController) listener).setMainController(PlaybackPresenter.this);

            return super.add(listener);
        }
    };
    private WeakReference<Video> mVideo;
    // Fix for using destroyed view
    private WeakReference<PlaybackView> mPlayer = new WeakReference<>(null);
    private boolean mIsEmbedPlayerStarted;
    private AiSubtitleSessionBinder mAiSubtitleBinder;
    private SubtitleAiSettingsController mAiSettings;
    private SubtitleTranslationCache mAiTranslationCache;
    private SubtitleTranslationDispatcher mAiDispatcher;
    private boolean mIsAiSubtitleChainReady;
    private SubtitlePrefetchLoop mAiLoop;
    private ExecutorService mAiSnapshotExecutor;
    private final AtomicBoolean mAiSnapshotCancelled = new AtomicBoolean();
    private boolean mAiSnapshotRequested;
    private final SubtitleTranslationStats mAiStats = new SubtitleTranslationStats();
    /** Bounded recent session events for the diagnostic export: enumerated codes only, never content. */
    private final SubtitleExportEventLog mAiEvents = new SubtitleExportEventLog(android.os.SystemClock::elapsedRealtime);
    /** Outcome of the last snapshot attempt; the diagnostic report prints it instead of guessing. */
    private volatile String mAiSnapshotStatus = "NOT_REQUESTED";
    private SubtitleExportController mAiExport;
    private ExecutorService mAiExportExecutor;
    private final android.os.Handler mMainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    private PlaybackPresenter(Context context) {
        super(context);

        // NOTE: position matters!!!
        mEventListeners.add(new VideoStateController());
        mEventListeners.add(new SuggestionsController());
        mEventListeners.add(new VideoLoaderController());
        mEventListeners.add(new ErrorFixerController());
        mEventListeners.add(new PlayerUIController());
        mEventListeners.add(new RemoteController(context));
        mEventListeners.add(new SponsorBlockController());
        mEventListeners.add(new AutoFrameRateController());
        mEventListeners.add(new HQDialogController());
        mEventListeners.add(new ChatController());
        mEventListeners.add(new CommentsController());
    }

    public static PlaybackPresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new PlaybackPresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    @Override
    public void onViewInitialized() {
        super.onViewInitialized();
        
        initControllers();
    }

    private void initControllers() {
        // Re-init after app exit
        process(PlayerEventListener::onInit);
    }

    public void openVideo(String videoId) {
        openVideo(videoId, false, -1, false);
    }

    /**
     * Opens video item from splash view
     */
    public void openVideo(String videoId, boolean finishOnEnded, long timeMs, boolean incognito) {
        if (videoId == null) {
            return;
        }

        Video video = Video.from(videoId);
        video.finishOnEnded = finishOnEnded;
        video.pendingPosMs = timeMs;
        video.incognito = incognito;
        openVideo(video);
    }

    public void openVideo(Video video) {
        if (video == null) {
            return;
        }

        if (getView() != null && getView().isEmbed()) { // switching from the embed player to the fullscreen one
            // The embed player doesn't disposed properly
            // NOTE: don't release after init check because this depends on timings
            getView().finishReally();
            setView(null);
            //getController(VideoStateController.class).saveState();
        }

        onNewVideo(video);

        getViewManager().startView(PlaybackView.class);
        mIsEmbedPlayerStarted = false;
    }

    public Video getVideo() {
        return mVideo != null ? mVideo.get() : null;
    }

    public boolean isRunningInBackground() {
        return getView() != null &&
                getView().isEngineBlocked() &&
                //getView().getBackgroundMode() != PlayerEngine.BACKGROUND_MODE_DEFAULT &&
                getView().isEngineInitialized() &&
                !getViewManager().isPlayerInForeground() &&
                getContext() instanceof Activity && Utils.checkActivity((Activity) getContext()); // Check that activity is not in Finishing state
    }

    public boolean isInPipMode() {
        return getView() != null && getView().isInPIPMode();
    }

    public boolean isOverlayShown() {
        return getView() != null && getView().isOverlayShown();
    }

    public boolean isPlaying() {
        return getView() != null && getView().isPlaying();
    }

    public boolean isEngineBlocked() {
        return getView() != null && getView().isEngineBlocked();
    }

    public boolean isEngineInitialized() {
        return getView() != null && getView().isEngineInitialized();
    }

    //public int getBackgroundMode() {
    //    return getView() != null ? getView().getBackgroundMode() : -1;
    //}

    public void forceFinish() {
        if (getView() != null) {
            getView().finishReally();
        }
    }

    public void setPosition(String timeCode) {
        setPosition(ServiceHelper.timeTextToMillis(timeCode));
    }

    public void setPosition(long positionMs) {
        // Check that the user isn't open context menu on suggestion item
        // if (Utils.isPlayerInForeground(getContext()) && getView() != null && !getView().getController().isSuggestionsShown()) {
        if (getViewManager().isPlayerInForeground() && getView() != null) {
            getView().setPositionMs(positionMs);
            getView().setPlayWhenReady(true);
            getView().showOverlay(false);
        } else {
            Video video = VideoMenuPresenter.sVideoHolder.get();
            if (video != null) {
                video.pendingPosMs = positionMs;
                openVideo(video);
            }
        }
    }

    // Controller methods

    @Override
    public void setView(PlaybackView view) {
        super.setView(view);
        mPlayer = new WeakReference<>(view);

        // Fix playing the previous video when switching between embed and fullscreen players.
        // E.g. when the user pressed back on the Channel content screen
        if (view != null && view.getVideo() != null && mIsEmbedPlayerStarted) {
            mVideo = new WeakReference<>(view.getVideo());
            Playlist.instance().add(view.getVideo()); // don't show queue
        }
    }

    public PlaybackView getPlayer() {
        return mPlayer.get(); // return view even if the one is destroyed
    }

    public Activity getActivity() {
        return getContext() instanceof Activity ? (Activity) getContext() : null;
    }

    @SuppressWarnings("unchecked")
    public <T extends PlayerEventListener> T getController(Class<T> clazz) {
        for (PlayerEventListener listener : mEventListeners) {
            if (clazz.isInstance(listener)) {
                return (T) listener;
            }
        }

        return null;
    }

    // Core events

    @Override
    public void onNewVideo(Video video) {
        process(listener -> listener.onNewVideo(video));
        mVideo = new WeakReference<>(video);
        mIsEmbedPlayerStarted = true;
    }

    @Override
    public void onFinish() {
        process(PlayerEventListener::onFinish);
    }

    @Override
    public void onInit() {
        // NOP. Internal event.
    }

    @Override
    public void onMetadata(MediaItemMetadata metadata) {
        process(listener -> listener.onMetadata(metadata));
    }

    // End core events

    // Helpers

    private boolean chainProcess(ChainProcessor<PlayerEventListener> processor) {
        return Utils.chainProcess(mEventListeners, processor);
    }

    private void process(Processor<PlayerEventListener> processor) {
        Utils.process(mEventListeners, processor);
    }

    // End Helpers

    // Common events

    @Override
    public void onViewCreated() {
        process(ViewEventListener::onViewCreated);
    }

    @Override
    public void onViewDestroyed() {
        process(ViewEventListener::onViewDestroyed);
    }

    @Override
    public void onViewPaused() {
        super.onViewPaused();

        process(ViewEventListener::onViewPaused);
    }

    @Override
    public void onViewResumed() {
        super.onViewResumed();

        process(ViewEventListener::onViewResumed);
    }

    // End common events

    // Start engine events

    @Override
    public void onSourceChanged(Video item) {
        AiSubtitleSessionBinder subtitles = aiSubtitleBinder();

        cancelAiSubtitleTimeline(); // a new media source invalidates the fetched timeline too
        mAiSnapshotStatus = "NOT_REQUESTED";
        mAiEvents.add("SOURCE_CHANGED");

        if (subtitles != null) {
            subtitles.onSourceChanged(); // invalidate the previous identity before anyone reacts
        }

        process(listener -> listener.onSourceChanged(item));
    }

    /**
     * Builds the translation chain once per player: settings (with the encrypted key store), the
     * bounded cache, the single-call dispatcher and its service. The periodic clock is created but
     * not started here; starting it belongs to the AI switch in the subtitle menu, so playback can
     * never begin translating on its own.
     */
    private void buildAiSubtitleChain() {
        if (mIsAiSubtitleChainReady || mAiSubtitleBinder == null || getContext() == null) {
            return;
        }

        mIsAiSubtitleChainReady = true;
        mAiSettings = new SubtitleAiSettingsController(
                new SubtitleAiPrefsStore(new AppPrefsSubtitleAiBackend(getContext())),
                SubtitleKeyStores.create(getContext()),
                () -> {
                    // Endpoint, model, target language or instruction changed: the old session and the
                    // translations cached for it must not be reused (plan 6.3).
                    if (mAiSubtitleBinder != null) {
                        mAiSubtitleBinder.getController().onConfigurationChanged();
                    }

                    if (mAiTranslationCache != null) {
                        mAiTranslationCache.clear();
                    }

                    mAiStats.reset();
                    cancelAiSubtitleTimeline();

                    // Restart for the new configuration instead of waiting for an unrelated event.
                    if (mAiSettings.getSettings().isEnabled()) {
                        requestAiSubtitleTimeline();
                    }
                });
        mAiTranslationCache = new SubtitleTranslationCache();
        mAiDispatcher = new SubtitleTranslationDispatcher(new SubtitleBatchPlanner(), mAiTranslationCache,
                new SubtitleTranslationService(new SubtitleOkHttpTranslationClient(),
                        mAiSettings.asConfigProvider(), mAiSettings.asKeyProvider(),
                        mAiSubtitleBinder::getActiveSourceLanguage, null),
                () -> android.os.SystemClock.elapsedRealtime(), (batch, translations, success) -> {
                    // A finished batch repaints the frame the player is showing; a failure leaves the
                    // original subtitles untouched (the dispatcher has already recorded the attempt).
                    if (success) {
                        mAiStats.onBatchDelivered(batch != null ? batch.getItems().size() : 0);

                        if (mAiSubtitleBinder != null) {
                            mAiSubtitleBinder.applyCurrentFrame();
                        }
                    } else {
                        mAiStats.onBatchFailed();
                    }
                });

        mAiSubtitleBinder.setTranslationCache(mAiTranslationCache);
        mAiSubtitleBinder.installDispatcher(mAiDispatcher);
        // The clock is built here but started only by the AI switch (applyAiEnabled), so ordinary
        // playback never checks or requests anything on its own.
        mAiLoop = mAiSubtitleBinder.createLoop(new SubtitleHandlerScheduler(), () -> {
            PlaybackView player = mPlayer.get();

            return player != null ? player.getPositionMs() : 0;
        });
    }

    /**
     * Fetches the timeline of the bound subtitle source once per source snapshot (plan 4.1).
     *
     * <p>The fetch runs on a single worker; the timeline is installed on the main thread and only
     * when the attempt was not cancelled meanwhile. An unusable snapshot leaves the original
     * subtitles in place, and no retry loop is started here - a new attempt needs a new event.
     */
    public void requestAiSubtitleTimeline() {
        PlaybackView player = mPlayer.get();

        if (!(player instanceof AiSubtitleHost) || mAiSubtitleBinder == null || mAiSnapshotRequested) {
            return;
        }

        AiSubtitleHost host = (AiSubtitleHost) player;
        SelectedSubtitleSource source = host.getSelectedSubtitleSource();
        com.google.android.exoplayer2.Format format = host.getSelectedSubtitleFormat();
        SubtitleSnapshotFetcher.PayloadFactory factory = host.createSubtitlePayloadFactory();

        if (source == null || format == null || factory == null) {
            return;
        }

        mAiSnapshotRequested = true;
        mAiSnapshotCancelled.set(false);

        if (mAiSnapshotExecutor == null) {
            mAiSnapshotExecutor = Executors.newSingleThreadExecutor();
        }

        mAiEvents.add("TIMELINE_REQUESTED");

        mAiSnapshotExecutor.execute(() -> {
            SubtitleSnapshotFetcher.Result result = new SubtitleSnapshotFetcher(
                    new SubtitleSnapshotReader(), factory)
                    .fetch(source, format, com.google.android.exoplayer2.C.TIME_UNSET, mAiSnapshotCancelled::get);

            boolean cancelled = mAiSnapshotCancelled.get();
            // A refused snapshot is diagnostic evidence, so its status survives even though nothing
            // is displayed (plan section 14: the report must work without a timeline).
            String status = cancelled ? SubtitleSnapshotReader.Status.CANCELLED.name()
                    : (result != null && result.getStatus() != null ? result.getStatus().name() : "UNKNOWN");

            mAiSnapshotStatus = status;
            mAiEvents.add("SNAPSHOT_" + status);

            if (cancelled || !result.isUsable()) {
                return; // cancelled or unusable: the original subtitles stay
            }

            mMainHandler.post(() -> {
                if (!mAiSnapshotCancelled.get() && mAiSubtitleBinder != null) {
                    mAiSubtitleBinder.setTimeline(result.getTimeline());
                }
            });
        });
    }

    /** Drops the current attempt (seek, source change, release); a later event may fetch again. */
    public void cancelAiSubtitleTimeline() {
        mAiSnapshotCancelled.set(true);
        mAiSnapshotRequested = false;
    }

    /** Called by the subtitle menu when the per-video AI switch changes. */
    public void applyAiEnabled(boolean enabled) {
        buildAiSubtitleChain();
        mAiEvents.add(enabled ? "AI_ENABLED" : "AI_DISABLED");

        // The switch must reach the session as well: turning AI off has to restore the original on
        // screen immediately, and turning it on has to apply the stored display mode.
        if (mAiSettings != null) {
            mAiSettings.setEnabled(enabled);
        }

        if (mAiSubtitleBinder != null) {
            mAiSubtitleBinder.onAiEnabled(enabled);

            if (mAiSettings != null) {
                mAiSubtitleBinder.onDisplayMode(mAiSettings.getSettings().getDisplayMode());
            }
        }

        if (mAiLoop == null) {
            return;
        }

        if (enabled) {
            mAiLoop.start();
            requestAiSubtitleTimeline(); // prepare the timeline of the source already selected
        } else {
            mAiLoop.stop();
            cancelAiSubtitleTimeline();
        }
    }

    public boolean isAiSubtitleRunning() {
        return mAiLoop != null && mAiLoop.isRunning();
    }

    /**
     * Applies a display-mode change to the running session. A mode change only repaints (plan section
     * 5), so the prepared timeline and the cache are untouched.
     */
    public void applyAiDisplayMode(int mode) {
        if (mAiSubtitleBinder != null) {
            mAiSubtitleBinder.onDisplayMode(mode);
        }
    }

    /** Session counters of the AI subtitle work, for the menu and the integration report. */
    public SubtitleTranslationStats getAiSubtitleStats() {
        return mAiStats;
    }

    /** Milliseconds left of a translation pause (rate limit or repeated failures) for the menu. */
    public long getAiSubtitlePauseMs() {
        return mAiDispatcher != null ? mAiDispatcher.getCooldownRemainingMs() : 0;
    }

    public SubtitleAiSettingsController getAiSubtitleSettings() {
        buildAiSubtitleChain();

        return mAiSettings;
    }

    /** Notified on the UI thread when a local export finished (plan section 14, T13). */
    public interface OnAiSubtitleExportFinished {
        void onExportFinished(SubtitleExportController.ExportResult result);
    }

    /**
     * Exports the subtitles already obtained for the current source: the original timeline, the
     * cached translations and a coverage note. It never translates, never requests and never cancels
     * a pending translation to make the archive bigger.
     *
     * @return false when another export is already running
     */
    public boolean exportAiSubtitles(OnAiSubtitleExportFinished listener) {
        return exportAi(true, listener);
    }

    /**
     * Exports the desensitised diagnostic report. It needs no subtitle timeline, no configured key
     * and no enabled AI switch, because a failed snapshot is exactly what has to be reportable.
     *
     * @return false when another export is already running
     */
    public boolean exportAiSubtitleDiagnostics(OnAiSubtitleExportFinished listener) {
        return exportAi(false, listener);
    }

    private boolean exportAi(boolean subtitles, final OnAiSubtitleExportFinished listener) {
        SubtitleExportController export = aiExportController();

        if (export == null) {
            return false;
        }

        SubtitleExportController.Listener forwarder = result -> {
            mAiEvents.add((result.getKind() == SubtitleExportController.Kind.SUBTITLES
                    ? "EXPORT_SUBTITLES_" : "EXPORT_DIAGNOSTICS_")
                    + (result.isSuccess() ? "OK" : "FAILED_" + result.getFailureCode()));

            if (listener != null) {
                listener.onExportFinished(result);
            }
        };

        return subtitles ? export.exportSubtitles(forwarder) : export.exportDiagnostics(forwarder);
    }

    /**
     * Builds the single export controller of this presenter. The busy flag must survive between
     * presses, so the controller is created once; every dependency inside it resolves the current
     * context, player or settings at call time instead of capturing them.
     */
    private synchronized SubtitleExportController aiExportController() {
        if (mAiExport == null) {
            mAiExport = new SubtitleExportController(this::buildAiExportSnapshot,
                    () -> SubtitleDiagnosticEnvironment.read(getContext()),
                    (baseName, extension, bytes) -> {
                        Context context = getContext();

                        return context != null
                                ? new SubtitleExportFileStore(context).write(baseName, extension, bytes)
                                : SubtitleExportWriteOutcome.failure(SubtitleExportWriteOutcome.Status.NO_LOCATION, null, null);
                    },
                    mMainHandler::post,
                    System::currentTimeMillis,
                    acquireAiExportExecutor());
        }

        return mAiExport;
    }

    /**
     * One worker for the whole process. The presenter is a singleton, and a player teardown in the
     * middle of an export must not interrupt the file the user asked for, so this executor is not
     * stopped with the engine (unlike the snapshot worker).
     */
    private synchronized ExecutorService acquireAiExportExecutor() {
        if (mAiExportExecutor == null || mAiExportExecutor.isShutdown()) {
            mAiExportExecutor = Executors.newSingleThreadExecutor();
        }

        return mAiExportExecutor;
    }

    /**
     * Copies everything one export may use, on the calling (UI) thread. Because it is a copy, a video
     * or track change after the press cannot mix new session content into the finished file.
     */
    private SubtitleExportSnapshot buildAiExportSnapshot() {
        AiSubtitleSessionBinder binder = mAiSubtitleBinder;
        PlaybackView player = mPlayer.get();
        SubtitleExportSnapshot.Source source = SubtitleExportSnapshot.Source.none();

        if (player instanceof AiSubtitleHost) {
            SelectedSubtitleSource selected = ((AiSubtitleHost) player).getSelectedSubtitleSource();

            if (selected != null) {
                // The base URL stays where it belongs (memory only): the snapshot keeps the safe parts.
                source = new SubtitleExportSnapshot.Source(true, selected.getType(), selected.getMimeType(),
                        selected.getLanguageCode(), selected.getVssId(), selected.isTranslatable());
            }
        }

        boolean aiEnabled = binder != null && binder.getController().isAiEnabled();
        int displayMode = binder != null ? binder.getController().getDisplayMode() : SubtitleComposer.MODE_ORIGINAL_ONLY;
        boolean keyConfigured = mAiSettings != null && mAiSettings.isKeyConfigured();
        String targetLanguage = mAiSettings != null ? mAiSettings.getSettings().getTargetLanguage() : null;
        SubtitleTranslationCache cache = mAiTranslationCache;
        Map<String, String> translations = cache != null ? cache.snapshot() : Collections.<String, String>emptyMap();
        SubtitleTimeline timeline = binder != null ? binder.getTimeline() : null;
        SubtitleExportSnapshot.Counters counters = new SubtitleExportSnapshot.Counters(
                mAiStats.getRequests(), mAiStats.getDeliveredItems(), mAiStats.getFailedBatches(),
                mAiStats.getCancelledBatches(), translations.size(), cache != null ? cache.getBytes() : 0);

        return new SubtitleExportSnapshot(System.currentTimeMillis(), source,
                new SubtitleExportSnapshot.Session(aiEnabled, keyConfigured, displayMode, targetLanguage,
                        mAiSnapshotStatus),
                counters, timeline, translations, mAiEvents.snapshot());
    }

    /**
     * Lazily joins the player event stream, the UI display surface and the bound source.
     *
     * @return null while the UI has no subtitle view yet, or when this surface cannot host subtitles
     */
    private AiSubtitleSessionBinder aiSubtitleBinder() {
        PlaybackView player = mPlayer.get();

        if (!(player instanceof AiSubtitleHost)) {
            return null;
        }

        SubtitleDisplay display = ((AiSubtitleHost) player).getSubtitleDisplay();

        if (display == null) {
            return null; // view not ready; the next event retries
        }

        if (mAiSubtitleBinder == null) {
            mAiSubtitleBinder = new AiSubtitleSessionBinder(display, ((AiSubtitleHost) player)::getSelectedSubtitleSource);
        }

        buildAiSubtitleChain();

        return mAiSubtitleBinder;
    }

    @Override
    public void onEngineInitialized() {
        getTickleManager().addListener(this);

        process(PlayerEventListener::onEngineInitialized);
    }

    @Override
    public void onEngineReleased() {
        getTickleManager().removeListener(this);

        mAiEvents.add("ENGINE_RELEASED");
        cancelAiSubtitleTimeline();

        if (mAiSnapshotExecutor != null) {
            mAiSnapshotExecutor.shutdownNow(); // the worker thread must not outlive the engine
            mAiSnapshotExecutor = null;
        }

        if (mAiLoop != null) {
            mAiLoop.stop(); // no residual timer survives an engine release
            mAiLoop = null;
        }

        mIsAiSubtitleChainReady = false;

        if (mAiSubtitleBinder != null) {
            mAiSubtitleBinder.onEngineReleased();
            mAiSubtitleBinder = null; // the subtitle view is recreated with the engine
        }

        process(PlayerEventListener::onEngineReleased);
    }

    @Override
    public void onEngineError(int type, int rendererIndex, Throwable error) {
        process(listener -> listener.onEngineError(type, rendererIndex, error));
    }

    @Override
    public void onPlay() {
        process(PlayerEventListener::onPlay);
    }

    @Override
    public void onPause() {
        process(PlayerEventListener::onPause);
    }

    @Override
    public void onPlayClicked() {
        process(PlayerEventListener::onPlayClicked);
    }

    @Override
    public void onPauseClicked() {
        process(PlayerEventListener::onPauseClicked);
    }

    @Override
    public void onSeekEnd() {
        AiSubtitleSessionBinder subtitles = aiSubtitleBinder();

        cancelAiSubtitleTimeline(); // the timeline of the old position must never be installed

        if (subtitles != null) {
            subtitles.onSeekEnd(); // clear stale translations and drop the carried original text
        }

        process(PlayerEventListener::onSeekEnd);
    }

    @Override
    public void onSeekPositionChanged(long positionMs) {
        process(listener -> listener.onSeekPositionChanged(positionMs));
    }

    @Override
    public void onSpeedChanged(float speed) {
        process(listener -> listener.onSpeedChanged(speed));
    }

    @Override
    public void onPlayEnd() {
        process(PlayerEventListener::onPlayEnd);
    }

    @Override
    public void onBuffering() {
        process(PlayerEventListener::onBuffering);
    }

    @Override
    public boolean onKeyDown(int keyCode) {
        return chainProcess(listener -> listener.onKeyDown(keyCode));
    }

    @Override
    public void onVideoLoaded(Video item) {
        AiSubtitleSessionBinder subtitles = aiSubtitleBinder();

        // The AI switch is per video (plan section 5): a newly loaded video starts with it off, so the
        // clock stops and the prepared timeline of the previous video is abandoned here.
        if (mAiSettings != null && mAiSettings.getSettings().isEnabled()) {
            applyAiEnabled(false);
        }

        if (subtitles != null) {
            subtitles.onVideoLoaded();
        }

        process(listener -> listener.onVideoLoaded(item));
    }

    @Override
    public void onTickle() {
        process(PlayerEventListener::onTickle);
    }

    // End engine events

    // Start UI events

    @Override
    public void onSuggestionItemClicked(Video item) {
        process(listener -> listener.onSuggestionItemClicked(item));
    }

    @Override
    public void onSuggestionItemLongClicked(Video item) {
        process(listener -> listener.onSuggestionItemLongClicked(item));
    }

    @Override
    public void onScrollEnd(Video item) {
        process(listener -> listener.onScrollEnd(item));
    }

    @Override
    public boolean onPreviousClicked() {
        return chainProcess(PlayerEventListener::onPreviousClicked);
    }

    @Override
    public boolean onNextClicked() {
        return chainProcess(PlayerEventListener::onNextClicked);
    }

    @Override
    public void onTrackSelected(FormatItem track) {
        process(listener -> listener.onTrackSelected(track));
    }

    @Override
    public void onControlsShown(boolean shown) {
        process(listener -> listener.onControlsShown(shown));
    }

    @Override
    public void onTrackChanged(FormatItem track) {
        AiSubtitleSessionBinder subtitles = aiSubtitleBinder();

        cancelAiSubtitleTimeline(); // re-resolve the source's timeline for the new track

        if (subtitles != null) {
            subtitles.onTrackChanged(); // same source keeps the session; a new source invalidates it
        }

        // The new track needs its own timeline; without this, switching subtitles while AI is on
        // would silently stop translating until the switch was toggled again.
        if (mAiSettings != null && mAiSettings.getSettings().isEnabled()) {
            requestAiSubtitleTimeline();
        }

        process(listener -> listener.onTrackChanged(track));
    }

    @Override
    public void onButtonClicked(int buttonId, int buttonState) {
        process(listener -> listener.onButtonClicked(buttonId, buttonState));
    }

    @Override
    public void onButtonLongClicked(int buttonId, int buttonState) {
        process(listener -> listener.onButtonLongClicked(buttonId, buttonState));
    }

    // End UI events
}
