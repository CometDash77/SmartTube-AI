package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable copy of every value the local export may use, taken on the UI thread at the moment the
 * user presses an export button (plan section 14, T13: the click binds the current video and source
 * snapshot).
 *
 * <p>Because the snapshot is a copy, a later video/track/configuration change cannot mix new session
 * content into an already started export: the timeline is an immutable object that a new source
 * replaces rather than mutates, the translations are copied entry by entry and the source identity is
 * reduced to plain values.
 *
 * <p>Privacy: the snapshot deliberately holds no video title, no subtitle URL and no credential. The
 * subtitle archive only ever contains subtitle text the user already obtained; the diagnostic report
 * only prints the field whitelist of {@link SubtitleDiagnosticReport}.
 */
public final class SubtitleExportSnapshot {
    /**
     * Whether the player surface can be observed at all. It is what keeps "no playback host" from
     * being reported as "the user did not select a subtitle track" (task R1).
     */
    public enum PlayerReadiness {
        /** The playback surface exists, exposes a subtitle display and has a session binder. */
        READY,
        /** No playback surface (`AiSubtitleHost`) is attached; there is nothing to observe. */
        NO_HOST,
        /** The surface exists but its subtitle view is not created yet. */
        NO_DISPLAY,
        /** The surface is usable but the AI subtitle session was not joined to it yet. */
        NO_BINDER
    }

    /**
     * Source observation that is safe to print: the readiness of the player, whether a subtitle track
     * is actually selected, the resolved {@link SubtitleSourceBinder.Status} and the source identity.
     * The source base URL is memory-only and is never copied here.
     */
    public static final class Source {
        private final PlayerReadiness mReadiness;
        private final boolean mSelected;
        private final SubtitleSourceBinder.Status mStatus;
        private final String mType;
        private final String mMimeType;
        private final String mLanguageCode;
        private final String mVssId;
        private final boolean mTranslatable;

        public Source(PlayerReadiness readiness, boolean selected, SubtitleSourceBinder.Status status,
                      String type, String mimeType, String languageCode, String vssId, boolean translatable) {
            mReadiness = readiness != null ? readiness : PlayerReadiness.NO_HOST;
            mSelected = selected;
            mStatus = status != null ? status : SubtitleSourceBinder.Status.UNBOUND;
            mType = type;
            mMimeType = mimeType;
            mLanguageCode = languageCode;
            mVssId = vssId;
            mTranslatable = translatable;
        }

        /** Nothing could be observed: no host, no selected track and no binding. */
        public static Source none() {
            return new Source(PlayerReadiness.NO_HOST, false, SubtitleSourceBinder.Status.UNBOUND,
                    null, null, null, null, false);
        }

        /** Readiness of the player surface; never {@link PlayerReadiness#READY} when it is unknown. */
        public PlayerReadiness getReadiness() {
            return mReadiness;
        }

        /** True when a subtitle track is really selected right now (not merely a format object). */
        public boolean isSelected() {
            return mSelected;
        }

        /** Result of resolving the selected track through {@link SubtitleSourceBinder}. */
        public SubtitleSourceBinder.Status getStatus() {
            return mStatus;
        }

        public boolean isBound() {
            return mStatus == SubtitleSourceBinder.Status.BOUND;
        }

        public String getType() {
            return mType;
        }

        public String getMimeType() {
            return mMimeType;
        }

        public String getLanguageCode() {
            return mLanguageCode;
        }

        public String getVssId() {
            return mVssId;
        }

        public boolean isTranslatable() {
            return mTranslatable;
        }
    }

    /** Session state: the per-video switch, key availability, display mode and the last snapshot result. */
    public static final class Session {
        private final boolean mAiEnabled;
        private final boolean mKeyConfigured;
        private final int mDisplayMode;
        private final String mTargetLanguage;
        private final String mSnapshotStatus;
        private final String mLastTimelineRequestResult;
        private final boolean mTimelineRequestInFlight;

        public Session(boolean aiEnabled, boolean keyConfigured, int displayMode, String targetLanguage,
                       String snapshotStatus) {
            this(aiEnabled, keyConfigured, displayMode, targetLanguage, snapshotStatus, "NOT_REQUESTED", false);
        }

        public Session(boolean aiEnabled, boolean keyConfigured, int displayMode, String targetLanguage,
                       String snapshotStatus, String lastTimelineRequestResult,
                       boolean timelineRequestInFlight) {
            mAiEnabled = aiEnabled;
            mKeyConfigured = keyConfigured;
            mDisplayMode = displayMode;
            mTargetLanguage = targetLanguage;
            mSnapshotStatus = snapshotStatus;
            mLastTimelineRequestResult = lastTimelineRequestResult != null
                    ? lastTimelineRequestResult
                    : "NOT_REQUESTED";
            mTimelineRequestInFlight = timelineRequestInFlight;
        }

        /** AI off, no key, no timeline: diagnostics still have to be exportable. */
        public static Session idle() {
            return new Session(false, false, SubtitleComposer.MODE_ORIGINAL_ONLY, null, "NOT_REQUESTED");
        }

        public boolean isAiEnabled() {
            return mAiEnabled;
        }

        public boolean isKeyConfigured() {
            return mKeyConfigured;
        }

        public int getDisplayMode() {
            return mDisplayMode;
        }

        public String getTargetLanguage() {
            return mTargetLanguage;
        }

        /** Name of the last {@link SubtitleSnapshotReader.Status}, or an explicit placeholder. */
        public String getSnapshotStatus() {
            return mSnapshotStatus;
        }

        /**
         * Name of the last timeline request outcome: either a
         * {@link SubtitleTimelineCoordinator.RequestResult} or the {@code PlayerReadiness} that
         * refused the request. Reset to {@code NOT_REQUESTED} whenever the media source changes.
         */
        public String getLastTimelineRequestResult() {
            return mLastTimelineRequestResult;
        }

        /** True while the one timeline read of the selected source is still running. */
        public boolean isTimelineRequestInFlight() {
            return mTimelineRequestInFlight;
        }
    }

    /** Counters the diagnostic report is allowed to print; they never contain subtitle text. */
    public static final class Counters {
        private final int mRequests;
        private final int mDeliveredItems;
        private final int mFailedBatches;
        private final int mCancelledBatches;
        private final int mCacheEntries;
        private final long mCacheBytes;
        private final int mTimelineRequests;
        private final int mTimelineInstalls;
        private final int mTimelineSkips;

        public Counters(int requests, int deliveredItems, int failedBatches, int cancelledBatches,
                        int cacheEntries, long cacheBytes) {
            this(requests, deliveredItems, failedBatches, cancelledBatches, cacheEntries, cacheBytes, 0, 0, 0);
        }

        /**
         * @param timelineRequests attempts really started ({@code STARTED} request results only)
         * @param timelineInstalls installs of the current request, including a reused-timeline install
         * @param timelineSkips   refusals of a precondition; deduplication and reuse are not skips
         */
        public Counters(int requests, int deliveredItems, int failedBatches, int cancelledBatches,
                        int cacheEntries, long cacheBytes, int timelineRequests, int timelineInstalls,
                        int timelineSkips) {
            mRequests = requests;
            mDeliveredItems = deliveredItems;
            mFailedBatches = failedBatches;
            mCancelledBatches = cancelledBatches;
            mCacheEntries = cacheEntries;
            mCacheBytes = cacheBytes;
            mTimelineRequests = timelineRequests;
            mTimelineInstalls = timelineInstalls;
            mTimelineSkips = timelineSkips;
        }

        public static Counters empty() {
            return new Counters(0, 0, 0, 0, 0, 0);
        }

        public int getTimelineRequests() {
            return mTimelineRequests;
        }

        public int getTimelineInstalls() {
            return mTimelineInstalls;
        }

        public int getTimelineSkips() {
            return mTimelineSkips;
        }

        public int getRequests() {
            return mRequests;
        }

        public int getDeliveredItems() {
            return mDeliveredItems;
        }

        public int getFailedBatches() {
            return mFailedBatches;
        }

        public int getCancelledBatches() {
            return mCancelledBatches;
        }

        public int getCacheEntries() {
            return mCacheEntries;
        }

        public long getCacheBytes() {
            return mCacheBytes;
        }
    }

    private final long mCreatedAtMs;
    private final Source mSource;
    private final Session mSession;
    private final Counters mCounters;
    private final SubtitleTimeline mTimeline;
    private final Map<String, String> mTranslations;
    private final Map<String, String> mTranslationStatus;
    private final List<String> mEvents;

    public SubtitleExportSnapshot(long createdAtMs, Source source, Session session, Counters counters,
                                  SubtitleTimeline timeline, Map<String, String> translations,
                                  List<String> events) {
        this(createdAtMs, source, session, counters, timeline, translations, null, events);
    }

    public SubtitleExportSnapshot(long createdAtMs, Source source, Session session, Counters counters,
                                  SubtitleTimeline timeline, Map<String, String> translations,
                                  Map<String, String> translationStatus, List<String> events) {
        mCreatedAtMs = createdAtMs;
        mSource = source != null ? source : Source.none();
        mSession = session != null ? session : Session.idle();
        mCounters = counters != null ? counters : Counters.empty();
        mTimeline = timeline;
        mTranslations = translations == null
                ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(translations));
        mTranslationStatus = translationStatus == null
                ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(translationStatus));
        mEvents = events == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(events));
    }

    /** The moment the user pressed the button; also the file-name stamp of the export. */
    public long getCreatedAtMs() {
        return mCreatedAtMs;
    }

    public Source getSource() {
        return mSource;
    }

    public Session getSession() {
        return mSession;
    }

    public Counters getCounters() {
        return mCounters;
    }

    /** The timeline of the source bound at click time, or null when none was obtained yet. */
    public SubtitleTimeline getTimeline() {
        return mTimeline;
    }

    /** Immutable copy of the successful translations known at click time. */
    public Map<String, String> getTranslations() {
        return mTranslations;
    }

    /**
     * Immutable copy of the per-item translation state known at click time
     * ({@link SubtitleTranslationCache#STATUS_TRANSLATED} / {@code STATUS_FAILED}); an item that is
     * absent was never attempted, which is how an interrupted run stays visible in the export.
     */
    public Map<String, String> getTranslationStatus() {
        return mTranslationStatus;
    }

    /** Bounded recent event codes (already sanitised to the code alphabet). */
    public List<String> getEvents() {
        return mEvents;
    }

    @Override
    public String toString() {
        return "SubtitleExportSnapshot{at=" + mCreatedAtMs + ", frames="
                + (mTimeline != null ? mTimeline.size() : 0) + ", translations=" + mTranslations.size()
                + ", ai=" + mSession.isAiEnabled() + ", status=" + mSession.getSnapshotStatus() + "}";
    }
}
