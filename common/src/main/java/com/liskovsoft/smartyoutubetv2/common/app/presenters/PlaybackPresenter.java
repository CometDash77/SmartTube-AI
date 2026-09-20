package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.AiSubtitleHost;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.AiSubtitleSessionBinder;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.AppPrefsSubtitleAiBackend;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleAiPrefsStore;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleAiSettingsController;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleBatchPlanner;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleConnectionTest;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleTranslationClient;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleHandlerScheduler;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitlePrefetchLoop;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitlePrefetchTicker;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SelectedSubtitleSource;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleSnapshotFetcher;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleSourceBinder;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleLoadNoticeDelivery;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleRuleSegmenter;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleSessionContext;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleAiSettings;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleFrame;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleSummaryAnalyzer;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleSummarySession;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleLoadNotificationPolicy;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleTimelineCoordinator;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    /** Verified examples and the frozen summary of the running session (plan 4.2). */
    private SubtitleSessionContext mAiSessionContext;
    /** One automatic context analysis per source and analysis configuration (plan 4.2). */
    private final SubtitleSummarySession mAiSummarySession = new SubtitleSummarySession();
    private SubtitleSummaryAnalyzer mAiSummaryAnalyzer;
    private SubtitleTranslationClient.Cancellable mAiSummaryCall;
    private SubtitleTranslationDispatcher mAiDispatcher;
    private SubtitleTranslationService mAiTranslationService;
    /** True after 401/403/402: no further request is started until the credential changes (N2). */
    private boolean mAiAuthorizationStopped;
    private SubtitleTranslationClient.Cancellable mAiConnectionTestCall;
    private boolean mIsAiSubtitleChainReady;
    private SubtitlePrefetchLoop mAiLoop;
    /** Bounded 100 ms derived-sentence display check, separate from the one-second prefetch loop. */
    private SubtitlePrefetchTicker mAiDerivedTicker;
    private final SubtitleRuleSegmenter mAiSegmenter = new SubtitleRuleSegmenter();
    private ExecutorService mAiSnapshotExecutor;
    /** Owns the request identity of the one timeline read per source (task N1). */
    private SubtitleTimelineCoordinator mAiTimeline;
    private final SubtitleTranslationStats mAiStats = new SubtitleTranslationStats();
    private int mAiConnectionHttpStatus;
    private int mAiConnectionGeneration;
    private String mAiLastTranslationResult = "NOT_REQUESTED";

    public int getAiConnectionHttpStatus() {
        return mAiConnectionHttpStatus;
    }

    public String getAiLastTranslationResult() {
        return mAiLastTranslationResult;
    }
    /** Bounded recent session events for the diagnostic export: enumerated codes only, never content. */
    private final SubtitleExportEventLog mAiEvents = new SubtitleExportEventLog(android.os.SystemClock::elapsedRealtime);
    /** Decides which subtitle load state may be shown as a short non-modal notice (plan 4.5). */
    private final SubtitleLoadNotificationPolicy mAiLoadNotifications = new SubtitleLoadNotificationPolicy();
    /** Outcome of the last snapshot attempt; the diagnostic report prints it instead of guessing. */
    private volatile String mAiSnapshotStatus = "NOT_REQUESTED";
    /** Fixed outcome of the last {@code requestAiSubtitleTimeline()} call; never a free-form value. */
    private volatile String mAiTimelineRequestResult = "NOT_REQUESTED";
    /** Timeline attempts really started (process lifetime, so it is not a per-video count). */
    private int mAiTimelineRequests;
    /** Timeline installs of the current request, including an install of a reused timeline. */
    private int mAiTimelineInstalls;
    /** Refused preconditions only; deduplication and reuse are deliberately not counted here. */
    private int mAiTimelineSkips;
    /** Last skip event code, so repeated refusals do not fill the bounded event ring (task R1). */
    private String mAiTimelineLastSkipCode;
    /** Last observed subtitle visibility; null means "unknown after a source change". */
    private Boolean mAiSubtitlesVisible;
    private SubtitleExportController mAiExport;
    private ExecutorService mAiExportExecutor;
    private final android.os.Handler mMainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    /**
     * Queues the accepted load notices and re-checks the live session at delivery time (plan 4.5): a
     * notice queued before the switch closed, the content generation moved on or the surface was
     * released must not appear afterwards.
     */
    private final SubtitleLoadNoticeDelivery mAiNoticeDelivery = new SubtitleLoadNoticeDelivery(
            task -> mMainHandler.post(task), new SubtitleLoadNoticeDelivery.SessionState() {
                @Override
                public String getSourceKey() {
                    return mAiSubtitleBinder != null ? mAiSubtitleBinder.getController().getActiveSourceKey() : null;
                }

                @Override
                public int getTranslationGeneration() {
                    return mAiSubtitleBinder != null ? mAiSubtitleBinder.getController().getTranslationGeneration() : 0;
                }

                @Override
                public boolean isAiEnabled() {
                    return mAiSettings != null && mAiSettings.getSettings().isEnabled();
                }

                @Override
                public boolean showsNotifications() {
                    return mAiSettings != null && mAiSettings.getSettings().showsLoadNotifications();
                }
            });

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

        invalidateAiSubtitleTimeline(); // a new media source invalidates the fetched timeline too
        mAiSnapshotStatus = "NOT_REQUESTED";
        mAiTimelineRequestResult = "NOT_REQUESTED";
        mAiLastTranslationResult = "NOT_REQUESTED";
        mAiTimelineLastSkipCode = null;
        mAiSubtitlesVisible = null; // visibility is unknown again until the tracks settle
        mAiEvents.add("SOURCE_CHANGED");

        cancelAiSubtitleSummary(); // the analysis sampled the previous source
        mAiSummarySession.reset();

        if (subtitles != null) {
            subtitles.onSourceChanged(); // invalidate the previous identity before anyone reacts
        }

        requestAiSubtitleTimeline(); // the export needs the original timeline of the new source

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
                this::onAiContentConfigurationChanged);
        mAiTranslationCache = new SubtitleTranslationCache();
        // Dispatcher/cache/display are player state: deliver network callbacks on the main thread.
        com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleTranslationClient client =
                (request, instruction, handler) -> new SubtitleOkHttpTranslationClient().send(request, instruction,
                        new com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleTranslationClient.ResponseHandler() {
                            @Override
                            public void onResponse(int status, long retryAfterMs, boolean truncated, String body) {
                                mMainHandler.post(() -> handler.onResponse(status, retryAfterMs, truncated, body));
                            }

                            @Override
                            public void onTransportFailure() {
                                mMainHandler.post(handler::onTransportFailure);
                            }
                        });
        mAiTranslationService = new SubtitleTranslationService(client,
                mAiSettings.asConfigProvider(), mAiSettings.asKeyProvider(),
                mAiSubtitleBinder::getActiveSourceLanguage, null);
        mAiTranslationService.setObserver(code -> {
            if ("REQUEST_STARTED".equals(code)) {
                mAiStats.onRequestStarted();
                mAiLastTranslationResult = code;
            }
            mAiEvents.add("TRANSLATION_" + code);
        });
        mAiDispatcher = new SubtitleTranslationDispatcher(new SubtitleBatchPlanner(), mAiTranslationCache,
                mAiTranslationService,
                () -> android.os.SystemClock.elapsedRealtime(), (batch, translations, success) -> {
                    mAiLastTranslationResult = mAiTranslationService.getLastResult();
                    // A finished batch repaints the frame the player is showing; a failure leaves the
                    // original subtitles untouched (the dispatcher has already recorded the attempt).
                    if (success) {
                        int delivered = 0;
                        if (translations != null) {
                            for (String translation : translations) {
                                if (translation != null && !translation.trim().isEmpty()) {
                                    delivered++;
                                }
                            }
                        }
                        mAiStats.onBatchDelivered(delivered);

                        if (mAiSubtitleBinder != null) {
                            // A batch of only future items must not claim a visible success: the
                            // display listener reports the notice only when a translation really
                            // reached the current frame, including a cached frame reached later.
                            mAiSubtitleBinder.applyCurrentFrame();
                        }
                    } else {
                        mAiStats.onBatchFailed();

                        syncAiLoadNotificationIdentity();
                        mAiNoticeDelivery.show(mAiLoadNotifications.onTranslationFailed());

                        // A refused credential or an empty balance must not be retried blindly: stop
                        // the loop and let the menu ask for a new key (plan 6.3, task N2).
                        if (mAiTranslationService.isAuthorizationStopped()) {
                            mAiAuthorizationStopped = true;
                            mAiEvents.add("AUTH_STOP");

                            if (mAiLoop != null) {
                                mAiLoop.stop();
                            }
                        }
                    }
                });
        mAiSettings.setCredentialChangeListener(this::onAiCredentialChanged);

        mAiSessionContext = new SubtitleSessionContext();
        mAiSubtitleBinder.setTranslationCache(mAiTranslationCache);
        mAiSubtitleBinder.setSessionContext(mAiSessionContext);
        mAiSubtitleBinder.setTranslationAvailability(
                () -> mAiSettings != null && mAiSettings.isKeyConfigured());
        mAiSubtitleBinder.installDispatcher(mAiDispatcher);
        mAiSubtitleBinder.setTranslationDisplayListener(() -> {
            syncAiLoadNotificationIdentity();
            mAiNoticeDelivery.show(mAiLoadNotifications.onTranslationShown());
        });
        // The clock is built here but started only by the AI switch (applyAiEnabled), so ordinary
        // playback never checks or requests anything on its own.
        mAiLoop = mAiSubtitleBinder.createLoop(new SubtitleHandlerScheduler(), () -> {
            PlaybackView player = mPlayer.get();

            return player != null ? player.getPositionMs() : 0;
        });
    }

    /**
     * One content transaction for endpoint, model, target language, instruction, context tier and
     * segmentation rules (plan 4.1/6.3). The session identity moves on first and the in-flight batch
     * is abandoned together with the translations of the old configuration, so neither a late answer
     * nor a cached result of the previous setup can be displayed or reused; the presenter
     * bookkeeping follows.
     */
    private void onAiContentConfigurationChanged() {
        if (mAiSubtitleBinder != null) {
            mAiSubtitleBinder.onConfigurationChanged(); // identity + cancel + cache of the old setup
        } else if (mAiTranslationCache != null) {
            mAiTranslationCache.clear();
        }

        mAiStats.reset();
        mAiLastTranslationResult = "NOT_REQUESTED";
        mAiAuthorizationStopped = false;
        cancelAiSubtitleConnectionTest();
        mAiEvents.add("CONFIGURATION_CHANGED");

        // The original timeline is independent of endpoint, model and language, so it must not be
        // fetched again; re-installing it restarts prefetch for the new configuration instead of
        // waiting for an unrelated track event (plan 6.3).
        reinstateAiSubtitleTimeline();

        // The previous analysis belongs to the old configuration; the enhanced tier analyses once more.
        cancelAiSubtitleSummary();
        mAiSummarySession.reset();
        maybeAnalyzeAiSubtitleContext();
        syncRuleSegmentation(); // the rule switch or its version is part of the new configuration

        if (mAiLoop != null && mAiSettings != null && mAiSettings.getSettings().isEnabled()
                && mAiSettings.isKeyConfigured()) {
            mAiLoop.start();
        }
    }

    /**
     * Starts the one automatic context analysis of the {@code VIDEO_ENHANCED} tier (plan 4.2), reusing
     * the configured endpoint, model, key and transport. The attempt holds the single AI slot, so the
     * first translation batch waits for it (at most 5 s) instead of the analysis queueing behind
     * translations; a failure or too small a sample simply leaves the session on the coherent context.
     */
    private void maybeAnalyzeAiSubtitleContext() {
        if (mAiSettings == null || mAiSessionContext == null || mAiSubtitleBinder == null) {
            return;
        }

        SubtitleAiSettings settings = mAiSettings.getSettings();

        if (!settings.isEnabled() || settings.getContextTier() != SubtitleAiSettings.CONTEXT_VIDEO_ENHANCED
                || mAiSessionContext.hasSummary()) {
            return;
        }

        SubtitleTimeline timeline = mAiSubtitleBinder.getTimelineOfCurrentSource();
        String sourceKey = mAiSubtitleBinder.getController().getActiveSourceKey();

        if (timeline == null || timeline.isEmpty() || sourceKey == null) {
            return; // the sample comes from the selected source's own timeline
        }

        if (!mAiSummarySession.beginAttempt(sourceKey + "|" + settings.getConfig().namespace())) {
            return; // one automatic attempt per source and analysis configuration (plan 4.2)
        }

        if (mAiDispatcher == null || !mAiDispatcher.tryAcquireExternalSlot()) {
            mAiSummarySession.releaseAttempt(); // busy: never spend the one attempt on a refusal
            return;
        }

        final SubtitleTranslationDispatcher dispatcher = mAiDispatcher;
        mAiEvents.add("CONTEXT_ANALYSIS_STARTED");

        mAiSummaryCall = aiSummaryAnalyzer().analyze(settings.getConfig(),
                mAiSettings.asKeyProvider().getApiKey(), mAiSubtitleBinder.getActiveSourceLanguage(),
                videoTitle(), videoDescription(), sampleOriginalLines(timeline), (status, summary) ->
                        mMainHandler.post(() -> {
                            if (dispatcher != null) {
                                dispatcher.releaseExternalSlot(); // the first batch may start now
                            }

                            mAiSummarySession.finishAttempt();
                            mAiEvents.add("CONTEXT_" + status.name());

                            if (status == SubtitleSummaryAnalyzer.Status.SUCCESS && summary != null
                                    && mAiSessionContext != null) {
                                mAiSessionContext.freezeSummary(summary);
                            } else if (status != SubtitleSummaryAnalyzer.Status.CANCELLED) {
                                // A failure falls back to the coherent context and is never retried
                                // automatically (plan 4.2).
                                mAiEvents.add("CONTEXT_ANALYSIS_FALLBACK");
                            } else {
                                // The abandoned attempt released the shared slot only now, so a
                                // configuration change that cancelled it can still analyse the new
                                // one; the attempt key keeps this from looping (plan 4.2).
                                maybeAnalyzeAiSubtitleContext();
                            }
                        }));
    }

    /**
     * Rule segmentation (Kiss feature, plan 4.3): recomputes the derived sentences of the current
     * source and hands them to the session. The switch only takes effect together with a key, AI on
     * and a translated display mode; otherwise the binder keeps the native cues on screen.
     */
    private void syncRuleSegmentation() {
        AiSubtitleSessionBinder binder = mAiSubtitleBinder;

        if (binder == null || mAiSettings == null) {
            return;
        }

        boolean enabled = mAiSettings.getSettings().usesRuleSegmentation();
        binder.setRuleSegmentation(enabled);

        SubtitleTimeline raw = binder.getTimeline();

        if (!enabled || raw == null || raw.isEmpty()) {
            binder.setDerivedTimeline(null);
            refreshAiDerivedDisplay();
            return;
        }

        SubtitleRuleSegmenter.Result result = mAiSegmenter.segment(raw, binder.getActiveSourceLanguage());

        if (result.isDerived()) {
            mAiEvents.add("SEGMENTED_OK");
        } else {
            mAiEvents.add("SEGMENTED_" + result.getFallback());
        }

        binder.setDerivedTimeline(result.isDerived() ? result.getTimeline() : null);
        refreshAiDerivedDisplay();
    }

    /**
     * Pushes the derived sentence of the current position to the display and keeps the bounded
     * display check running only while derived sentences are really on screen (plan 4.3.7).
     */
    private void refreshAiDerivedDisplay() {
        AiSubtitleSessionBinder binder = mAiSubtitleBinder;

        if (binder == null) {
            return;
        }

        binder.refreshDerivedDisplay(currentPositionMs());

        if (mAiDerivedTicker == null) {
            mAiDerivedTicker = new SubtitlePrefetchTicker(new SubtitleHandlerScheduler(),
                    this::refreshAiDerivedDisplay, SubtitlePrefetchTicker.DERIVED_DISPLAY_INTERVAL_MS);
        }

        if (binder.isDerivedDisplayActive()) {
            mAiDerivedTicker.start();
        } else {
            mAiDerivedTicker.stop();
        }
    }

    private long currentPositionMs() {
        PlaybackView player = mPlayer.get();

        return player != null ? player.getPositionMs() : 0;
    }

    /** Abandons a prepared context analysis (video, source, configuration change, release). */
    private void cancelAiSubtitleSummary() {
        if (mAiSummaryCall != null) {
            mAiSummaryCall.cancel();
            mAiSummaryCall = null;
        }
    }

    /** The analysis transport: the same clean client and the plan's independent 5 s budget. */
    private SubtitleSummaryAnalyzer aiSummaryAnalyzer() {
        if (mAiSummaryAnalyzer == null) {
            mAiSummaryAnalyzer = new SubtitleSummaryAnalyzer(new SubtitleOkHttpTranslationClient(),
                    (delayMs, task) -> {
                        SubtitleHandlerScheduler scheduler = new SubtitleHandlerScheduler();
                        scheduler.postDelayed(task, delayMs);

                        return () -> scheduler.removeCallbacks(task);
                    });
        }

        return mAiSummaryAnalyzer;
    }

    /** Title of the video being played; missing metadata never blocks the analysis (plan 4.2). */
    private String videoTitle() {
        Video video = mVideo != null ? mVideo.get() : null;

        return video != null ? video.title : null;
    }

    private String videoDescription() {
        Video video = mVideo != null ? mVideo.get() : null;

        return video != null ? video.description : null;
    }

    /** Ordered distinct originals from the beginning of the source; the analyzer bounds them. */
    private static List<String> sampleOriginalLines(SubtitleTimeline timeline) {
        List<String> samples = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        if (timeline == null) {
            return samples;
        }

        for (SubtitleFrame frame : timeline.getFrames()) {
            for (SubtitleItem item : frame.getItems()) {
                String text = item.getText();

                if (text == null || text.trim().isEmpty() || !seen.add(item.getItemId())) {
                    continue;
                }

                samples.add(text.trim());

                if (samples.size() >= 400) {
                    return samples; // the analyzer applies the code-point bound
                }
            }
        }

        return samples;
    }

    /**
     * Prepares the timeline of the bound subtitle source, at most once per source (plan 4.1, task N1).
     *
     * <p>This is deliberately independent of the AI translation switch: the local export needs the
     * original timeline even for a user who never configures a key, so the request is also started by
     * ordinary subtitle events. Repeated events of the same source reuse the running or finished
     * read instead of restarting it, and a real change abandons the previous attempt with its own
     * cancellation flag, so a late answer can neither install a timeline nor overwrite the current
     * diagnostic status.
     */
    public void requestAiSubtitleTimeline() {
        SubtitleExportSnapshot.PlayerReadiness readiness = playerReadiness();

        if (readiness != SubtitleExportSnapshot.PlayerReadiness.READY) {
            // A missing host or subtitle view must never be reported as "no subtitle track selected".
            recordAiTimelineSkip(readiness.name());

            return;
        }

        SubtitleTimelineCoordinator.RequestResult result = aiTimeline().request();

        mAiTimelineRequestResult = result.name();
        mAiTimelineLastSkipCode = null;

        switch (result) {
            case STARTED:
                // A refused snapshot is diagnostic evidence, so its status survives even though nothing
                // is displayed (plan section 14: the report must work without a timeline).
                mAiTimelineRequests++;
                mAiEvents.add("TIMELINE_REQUESTED");
                syncAiLoadNotificationIdentity();
                mAiNoticeDelivery.show(mAiLoadNotifications.onSnapshotRequested());
                return;
            case REUSED:
            case ALREADY_READY:
            case IN_FLIGHT:
                return; // deduplication and reuse are neither errors nor skipped preconditions
            default:
                recordAiTimelineSkip(result.name());
        }
    }

    /** Counts a refusal and records it once per distinct code, so repeated events cannot flood. */
    private void recordAiTimelineSkip(String result) {
        mAiTimelineSkips++;
        mAiTimelineRequestResult = result;

        String code = "TIMELINE_SKIP_" + result;

        if (!code.equals(mAiTimelineLastSkipCode)) {
            mAiEvents.add(code);
            mAiTimelineLastSkipCode = code;
        }
    }

    /** Drops the current attempt (release, configuration reset) without starting a new one. */
    public void cancelAiSubtitleTimeline() {
        if (mAiTimeline != null) {
            mAiTimeline.cancel();
        }
    }

    /**
     * A new video or a replaced media source: the media generation moves on, so even a track that
     * looks identical starts a fresh identity and every older attempt is abandoned.
     */
    private void invalidateAiSubtitleTimeline() {
        if (mAiTimeline != null) {
            mAiTimeline.invalidateSourceContext();
        }
    }

    /**
     * Re-installs the already decoded timeline of the current source. Used when endpoint, model,
     * target language or the instruction changed: the text is the same, so nothing is fetched again,
     * but prefetch has to restart for the new configuration (plan 6.3).
     */
    public void reinstateAiSubtitleTimeline() {
        if (mAiSubtitleBinder == null) {
            return;
        }

        SubtitleTimeline timeline = mAiSubtitleBinder.getTimelineOfCurrentSource();

        if (timeline != null) {
            mAiSubtitleBinder.setTimeline(timeline);
        }
    }

    private SubtitleTimelineCoordinator aiTimeline() {
        if (mAiTimeline == null) {
            if (mAiSnapshotExecutor == null || mAiSnapshotExecutor.isShutdown()) {
                mAiSnapshotExecutor = Executors.newSingleThreadExecutor();
            }

            mAiTimeline = new SubtitleTimelineCoordinator(new AiTimelineHost(), mAiSnapshotExecutor,
                    mMainHandler::post, SubtitleTimelineCoordinator.systemFetcher(), this::onAiTimelineSettled);
        }

        return mAiTimeline;
    }

    /**
     * A stored credential changed through the active settings controller: allow requests again. The
     * cached translations stay valid, because the key is not part of the configuration namespace.
     */
    private void onAiCredentialChanged() {
        cancelAiSubtitleConnectionTest();
        mAiAuthorizationStopped = false;
        mAiEvents.add("CREDENTIAL_CHANGED");

        if (mAiLoop != null && mAiSettings != null && mAiSettings.getSettings().isEnabled()
                && mAiSettings.isKeyConfigured()) {
            mAiLoop.start(); // resume immediately instead of waiting for the switch to be toggled
        }
    }

    /** True when the last attempts were refused because of the credentials or the billing state. */
    public boolean isAiSubtitleAuthorizationStopped() {
        return mAiAuthorizationStopped;
    }

    /** Result of the manual connection test of the AI settings menu. */
    public interface OnAiSubtitleConnectionTestFinished {
        void onAiSubtitleConnectionTestFinished(SubtitleConnectionTest.Outcome outcome);
    }

    /** The player UI side of the non-modal subtitle load notifications (plan 4.5). */
    public interface OnAiSubtitleLoadNotice {
        void onAiSubtitleLoadNotice(SubtitleLoadNotificationPolicy.Stage stage);

        /** The switch was closed or the surface was released: hide a notice already on screen. */
        void onAiSubtitleLoadNoticeCleared();
    }

    /** Registers the UI that shows the load notifications; pass null to detach. */
    public void setAiLoadNoticeListener(final OnAiSubtitleLoadNotice listener) {
        mAiNoticeDelivery.setUi(listener == null ? null : new SubtitleLoadNoticeDelivery.Ui() {
            @Override
            public void onNotice(SubtitleLoadNotificationPolicy.Stage stage) {
                listener.onAiSubtitleLoadNotice(stage);
            }

            @Override
            public void onCleared() {
                listener.onAiSubtitleLoadNoticeCleared();
            }
        });
    }

    /**
     * The menu's notification switch. It is persisted by the settings controller and never starts or
     * cancels work, but closing it must also drop a pending notice and hide the current one instead
     * of leaving a stale toast on screen (plan 4.5).
     */
    public void setAiLoadNotifications(boolean enabled) {
        buildAiSubtitleChain();

        if (mAiSettings != null) {
            mAiSettings.setLoadNotifications(enabled);
        }

        if (!enabled) {
            cancelPendingAiLoadNotices();
        }
    }

    /** Syncs the notification dedupe identity with the live session (source, content generation). */
    private void syncAiLoadNotificationIdentity() {
        if (mAiSubtitleBinder != null) {
            mAiLoadNotifications.setSourceKey(mAiSubtitleBinder.getController().getActiveSourceKey());
            mAiLoadNotifications.setTranslationGeneration(mAiSubtitleBinder.getController().getTranslationGeneration());
        } else {
            mAiLoadNotifications.reset();
        }
    }

    /**
     * Drops every queued load notice and hides the one on screen. Called when the user closes the
     * notification switch and on engine release (plan 4.5): a stale toast must not survive either.
     */
    private void cancelPendingAiLoadNotices() {
        mAiNoticeDelivery.revoke();
    }

    /**
     * Sends one minimal synthetic batch with the current configuration, so the user can verify a key
     * and an endpoint without playing a video (plan 18.3). It never sends the subtitles being watched
     * and reports only a classified outcome, never a response body or a key.
     *
     * @return true when an attempt was started
     */
    public boolean testAiSubtitleConnection(OnAiSubtitleConnectionTestFinished listener) {
        buildAiSubtitleChain();

        if (mAiSettings == null) {
            return false;
        }

        final SubtitleTranslationDispatcher dispatcher = mAiDispatcher;

        cancelAiSubtitleConnectionTest(); // a superseded attempt must not report for this one

        // The synthetic request uses the same paid service as the session (plan 4.1): the test and a
        // translation share one slot. A translation in flight refuses the test with explicit feedback,
        // and the reserved slot refuses the next prefetch tick, so neither start order can put two AI
        // requests in the air.
        if (dispatcher != null && !dispatcher.tryAcquireExternalSlot()) {
            mAiEvents.add("CONNECTION_TEST_BUSY");

            if (listener != null) {
                mMainHandler.post(() -> listener.onAiSubtitleConnectionTestFinished(
                        SubtitleConnectionTest.Outcome.BUSY));
            }

            return false;
        }

        final int generation = mAiConnectionGeneration;
        mAiConnectionHttpStatus = 0;
        mAiEvents.add("CONNECTION_TEST_STARTED");
        SubtitleConnectionTest.Listener forwarder = new SubtitleConnectionTest.Listener() {
            @Override
            public void onFinished(SubtitleConnectionTest.Outcome outcome) {
                onHttpFinished(outcome, 0);
            }

            @Override
            public void onHttpFinished(SubtitleConnectionTest.Outcome outcome, int status) {
                mMainHandler.post(() -> {
                    if (dispatcher != null) {
                        // The terminal callback of this attempt arrived: the shared slot is free even
                        // when the attempt was superseded in the meantime (plan 4.1).
                        dispatcher.releaseExternalSlot();
                    }

                    if (generation != mAiConnectionGeneration) {
                        return; // a superseded attempt must not report or overwrite the current status
                    }
                    mAiConnectionHttpStatus = status >= 100 && status <= 599 ? status : 0;
                    mAiEvents.add("CONNECTION_HTTP_" + mAiConnectionHttpStatus);
                    mAiEvents.add("CONNECTION_" + outcome.name());
                    if (listener != null) {
                        listener.onAiSubtitleConnectionTestFinished(outcome);
                    }
                });
            }
        };

        SubtitleTranslationClient.Cancellable call = new SubtitleConnectionTest(new SubtitleOkHttpTranslationClient())
                .test(mAiSettings.getSettings().getConfig(), mAiSettings.asKeyProvider().getApiKey(), null, forwarder);

        mAiConnectionTestCall = call;

        return true;
    }

    /**
     * Invalidates a superseded attempt (its late terminal callback is dropped on the main thread).
     *
     * <p>The shared AI slot is deliberately not released here: it is released by the terminal
     * callback of the attempt that reserved it, so a cancelled transport can never free the slot for
     * a second call while its own call is still terminating (plan 4.1).
     */
    private void cancelAiSubtitleConnectionTest() {
        mAiConnectionGeneration++;
        if (mAiConnectionTestCall != null) {
            mAiConnectionTestCall.cancel();
            mAiConnectionTestCall = null;
        }
    }

    /** Only accepted attempts may become the current diagnostic state (task N1). */
    private void onAiTimelineSettled(String status, boolean accepted, boolean installed) {
        if (accepted) {
            mAiSnapshotStatus = status;
        }

        if (accepted && installed) {
            // Includes the reused install: the timeline is in place for the current source afterwards.
            mAiTimelineInstalls++;
            maybeAnalyzeAiSubtitleContext(); // the sample needs the timeline of this source
            syncRuleSegmentation(); // derived sentences need the snapshot of this source
        }

        mAiEvents.add((accepted ? "SNAPSHOT_" : "SNAPSHOT_STALE_") + status);
        syncAiLoadNotificationIdentity();
        mAiNoticeDelivery.show(mAiLoadNotifications.onSnapshotSettled(status, accepted, installed));
    }

    /**
     * Whether the player surface can be observed at all. This is what keeps a missing host or
     * subtitle view from being reported as "the user did not select a subtitle track" (task R1).
     */
    private SubtitleExportSnapshot.PlayerReadiness playerReadiness() {
        PlaybackView player = mPlayer.get();

        if (!(player instanceof AiSubtitleHost)) {
            return SubtitleExportSnapshot.PlayerReadiness.NO_HOST;
        }

        if (mAiSubtitleBinder == null) {
            return ((AiSubtitleHost) player).getSubtitleDisplay() == null
                    ? SubtitleExportSnapshot.PlayerReadiness.NO_DISPLAY
                    : SubtitleExportSnapshot.PlayerReadiness.NO_BINDER;
        }

        return SubtitleExportSnapshot.PlayerReadiness.READY;
    }

    /**
     * True when the selected subtitle track resolves to a bound source. The menu must ask this instead
     * of treating a non-null format object as a bound track (task R1).
     */
    public boolean isAiSubtitleSourceBound() {
        PlaybackView player = mPlayer.get();

        return player instanceof AiSubtitleHost
                && ((AiSubtitleHost) player).getSelectedSubtitleSource() != null;
    }

    /** True when host, subtitle view and session binder are all available. */
    public boolean isAiSubtitlePlayerReady() {
        return playerReadiness() == SubtitleExportSnapshot.PlayerReadiness.READY;
    }

    /**
     * Records SUBTITLES_ON/OFF only when the real visibility state changed. A selected track and
     * visible subtitles are different concepts, so this event is never proof that a source is bound,
     * and neither a tick nor a repeated menu redraw may emit it (task R1).
     */
    private void trackAiSubtitleVisibility() {
        PlaybackView player = mPlayer.get();
        boolean visible = player instanceof AiSubtitleHost
                && ((AiSubtitleHost) player).getSelectedSubtitleFormat() != null;

        if (mAiSubtitlesVisible != null && mAiSubtitlesVisible == visible) {
            return;
        }

        mAiSubtitlesVisible = visible;
        mAiEvents.add(visible ? "SUBTITLES_ON" : "SUBTITLES_OFF");
    }

    /** The player surface as the timeline coordinator needs it. */
    private final class AiTimelineHost implements SubtitleTimelineCoordinator.Host {
        @Override
        public SelectedSubtitleSource getSelectedSource() {
            PlaybackView player = mPlayer.get();

            return player instanceof AiSubtitleHost ? ((AiSubtitleHost) player).getSelectedSubtitleSource() : null;
        }

        @Override
        public com.google.android.exoplayer2.Format getSelectedFormat() {
            PlaybackView player = mPlayer.get();

            return player instanceof AiSubtitleHost ? ((AiSubtitleHost) player).getSelectedSubtitleFormat() : null;
        }

        @Override
        public SubtitleSnapshotFetcher.PayloadFactory createPayloadFactory() {
            PlaybackView player = mPlayer.get();

            return player instanceof AiSubtitleHost ? ((AiSubtitleHost) player).createSubtitlePayloadFactory() : null;
        }

        @Override
        public String getCurrentSourceKey() {
            return mAiSubtitleBinder != null ? mAiSubtitleBinder.getController().getActiveSourceKey() : null;
        }

        @Override
        public SubtitleTimeline getTimelineOfCurrentSource() {
            return mAiSubtitleBinder != null ? mAiSubtitleBinder.getTimelineOfCurrentSource() : null;
        }

        @Override
        public SubtitleTimeline getInstalledTimeline() {
            return mAiSubtitleBinder != null ? mAiSubtitleBinder.getTimeline() : null;
        }

        @Override
        public void installTimeline(SubtitleTimeline timeline) {
            if (mAiSubtitleBinder != null) {
                mAiSubtitleBinder.setTimeline(timeline);
            }
        }
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
            maybeAnalyzeAiSubtitleContext(); // the enhanced tier analyses once, before the first batch
            syncRuleSegmentation();
        } else {
            // Turning translation off must not discard the original timeline: the local export works
            // without the AI switch and an in-flight snapshot is a plain subtitle read, not a paid call.
            mAiLoop.stop();
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
            syncRuleSegmentation(); // "original only" uses the native cues again
        }
    }

    /** Outcome of the forced-retranslation action, so the menu can explain a refusal (plan 4.4). */
    public enum AiRetranslateOutcome {
        STARTED,
        /** The per-video AI switch is off. */
        AI_OFF,
        /** No key is configured. */
        NO_KEY,
        /** The last attempt was refused by the credentials or the billing state. */
        AUTH_STOPPED,
        /** No subtitle source is bound right now. */
        NO_SOURCE,
        /** The player surface or the timeline is not ready yet. */
        NOT_READY
    }

    /**
     * Forces retranslation of the current source from the current position (plan 4.4). The stored
     * translations, the failure budget and the coherent examples of this source are dropped while the
     * original timeline and the frozen summary survive; the current window is then requested again
     * under a new generation, so a previous success really produces a new request. Original subtitles
     * stay visible and the action never claims that the whole video was retranslated.
     */
    public AiRetranslateOutcome retranslateAiSubtitles() {
        buildAiSubtitleChain();

        if (mAiSettings == null || !mAiSettings.getSettings().isEnabled()) {
            return AiRetranslateOutcome.AI_OFF;
        }

        if (!mAiSettings.isKeyConfigured()) {
            return AiRetranslateOutcome.NO_KEY;
        }

        if (mAiAuthorizationStopped) {
            return AiRetranslateOutcome.AUTH_STOPPED; // a refused credential needs the settings, not a retry
        }

        if (mAiSubtitleBinder == null || !isAiSubtitlePlayerReady()
                || !mAiSubtitleBinder.getController().hasActiveSession()) {
            return AiRetranslateOutcome.NOT_READY;
        }

        if (!mAiSubtitleBinder.retranslateCurrentSource()) {
            return AiRetranslateOutcome.NOT_READY; // no timeline yet: there is nothing to translate again
        }

        mAiStats.reset();
        mAiEvents.add("RETRANSLATE");

        if (mAiLoop != null) {
            mAiLoop.start(); // idempotent; the reset window is planned from the current position
        }

        return AiRetranslateOutcome.STARTED;
    }

    /** Session counters of the AI subtitle work, for the menu and the integration report. */
    public SubtitleTranslationStats getAiSubtitleStats() {
        return mAiStats;
    }

    /** Last completed snapshot outcome (status name), shown by the menu when an export has no timeline. */
    public String getAiSubtitleSnapshotStatus() {
        return mAiSnapshotStatus;
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
        SubtitleExportSnapshot.PlayerReadiness readiness = playerReadiness();
        boolean selected = false;
        SubtitleSourceBinder.Status sourceStatus = SubtitleSourceBinder.Status.UNBOUND;
        SubtitleExportSnapshot.Source source = SubtitleExportSnapshot.Source.none();

        if (readiness == SubtitleExportSnapshot.PlayerReadiness.READY) {
            AiSubtitleHost host = (AiSubtitleHost) player;
            // Resolve first, then read the status: getStatus() alone only repeats an earlier result.
            SelectedSubtitleSource selectedSource = host.getSelectedSubtitleSource();
            SubtitleSourceBinder.Status resolved = host.getSubtitleSourceStatus();

            selected = host.getSelectedSubtitleFormat() != null;
            sourceStatus = resolved != null ? resolved : SubtitleSourceBinder.Status.UNBOUND;

            // The base URL stays where it belongs (memory only): the snapshot keeps the safe parts.
            source = new SubtitleExportSnapshot.Source(readiness, selected, sourceStatus,
                    selectedSource != null ? selectedSource.getType() : null,
                    selectedSource != null ? selectedSource.getMimeType() : null,
                    selectedSource != null ? selectedSource.getLanguageCode() : null,
                    selectedSource != null ? selectedSource.getVssId() : null,
                    selectedSource != null && selectedSource.isTranslatable());
        }

        boolean aiEnabled = binder != null && binder.getController().isAiEnabled();
        int displayMode = binder != null ? binder.getController().getDisplayMode() : SubtitleComposer.MODE_ORIGINAL_ONLY;
        boolean keyConfigured = mAiSettings != null && mAiSettings.isKeyConfigured();
        String targetLanguage = mAiSettings != null ? mAiSettings.getSettings().getTargetLanguage() : null;
        SubtitleTranslationCache cache = mAiTranslationCache;
        Map<String, String> translations = cache != null ? cache.snapshot() : Collections.<String, String>emptyMap();
        Map<String, String> translationStatus = cache != null ? cache.statusSnapshot() : Collections.<String, String>emptyMap();
        SubtitleTimeline timeline = binder != null ? binder.getTimelineOfCurrentSource() : null;
        // The derived sentences are exported next to the raw ones, never instead of them (plan 4.3.8).
        SubtitleTimeline segmentedTimeline = binder != null ? binder.getDerivedTimeline() : null;
        SubtitleExportSnapshot.Counters counters = new SubtitleExportSnapshot.Counters(
                mAiStats.getRequests(), mAiStats.getDeliveredItems(), mAiStats.getFailedBatches(),
                mAiStats.getCancelledBatches(), translations.size(), cache != null ? cache.getBytes() : 0,
                mAiTimelineRequests, mAiTimelineInstalls, mAiTimelineSkips);
        boolean requestInFlight = mAiTimeline != null && mAiTimeline.isAttemptInFlight();

        return new SubtitleExportSnapshot(System.currentTimeMillis(), source,
                new SubtitleExportSnapshot.Session(aiEnabled, keyConfigured, displayMode, targetLanguage,
                        mAiSnapshotStatus, mAiTimelineRequestResult, requestInFlight),
                counters, timeline, segmentedTimeline, translations, translationStatus, mAiEvents.snapshot());
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
        cancelAiSubtitleSummary(); // no analysis of a released surface may settle later
        mAiSummarySession.reset();
        cancelPendingAiLoadNotices(); // a released surface must not deliver or keep a notice
        mAiLoadNotifications.reset();
        cancelAiSubtitleTimeline();
        cancelAiSubtitleConnectionTest();
        mAiTimeline = null; // the coordinator and its identity belong to the released surface

        if (mAiSnapshotExecutor != null) {
            mAiSnapshotExecutor.shutdownNow(); // the worker thread must not outlive the engine
            mAiSnapshotExecutor = null;
        }

        if (mAiLoop != null) {
            mAiLoop.stop(); // no residual timer survives an engine release
            mAiLoop = null;
        }

        if (mAiDerivedTicker != null) {
            mAiDerivedTicker.stop(); // the derived display check is a timer too
            mAiDerivedTicker = null;
        }

        mIsAiSubtitleChainReady = false;

        mAiSubtitlesVisible = null; // a rebuilt engine reports its visibility again

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
        if (mAiSubtitleBinder != null && mAiSubtitleBinder.isDerivedDisplayActive() && mAiDerivedTicker != null) {
            mAiDerivedTicker.start(); // the clock paused with playback; resume the display check
        }

        process(PlayerEventListener::onPlay);
    }

    @Override
    public void onPause() {
        if (mAiDerivedTicker != null) {
            mAiDerivedTicker.stop(); // a paused video has no boundary to catch up with
        }

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

        if (subtitles != null) {
            subtitles.onSeekEnd(); // clear stale translations and drop the carried original text
            refreshAiDerivedDisplay(); // the sentence of the new position appears immediately
        }

        // The timeline covers the whole source and survives a seek, so an attempt that is already
        // running for the same source is deliberately neither cancelled nor restarted (task N1); only
        // when the source has no timeline yet does this start a read.
        requestAiSubtitleTimeline();

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

        cancelAiSubtitleSummary(); // the analysis belonged to the previous video
        mAiSummarySession.reset();

        // The AI switch is per video (plan section 5): a newly loaded video starts with it off, so the
        // clock stops and the prepared timeline of the previous video is abandoned here.
        if (mAiSettings != null && mAiSettings.getSettings().isEnabled()) {
            applyAiEnabled(false);
        }

        if (subtitles != null) {
            subtitles.onVideoLoaded();
        }

        invalidateAiSubtitleTimeline(); // the new video's attempts start from a fresh identity
        requestAiSubtitleTimeline(); // prepare the original timeline without needing the AI switch

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

        if (subtitles != null) {
            subtitles.onTrackChanged(); // same source keeps the session; a new source invalidates it
        }

        trackAiSubtitleVisibility(); // a real visibility change, never a tick or a menu redraw

        // The new track needs its own timeline; without this, switching subtitles while AI is on
        // would silently stop translating until the switch was toggled again. Selecting the same
        // source again reuses the running or finished read instead of restarting it (task N1).
        requestAiSubtitleTimeline(); // the export needs the original timeline even with AI off

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
