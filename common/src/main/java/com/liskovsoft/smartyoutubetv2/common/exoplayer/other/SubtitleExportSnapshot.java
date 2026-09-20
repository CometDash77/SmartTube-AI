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
     * Source identity that is safe to print: the source type, its declared MIME and its language.
     * The source base URL is memory-only and is never copied here.
     */
    public static final class Source {
        private final boolean mBound;
        private final String mType;
        private final String mMimeType;
        private final String mLanguageCode;
        private final String mVssId;
        private final boolean mTranslatable;

        public Source(boolean bound, String type, String mimeType, String languageCode, String vssId,
                      boolean translatable) {
            mBound = bound;
            mType = type;
            mMimeType = mimeType;
            mLanguageCode = languageCode;
            mVssId = vssId;
            mTranslatable = translatable;
        }

        /** No subtitle source is bound (subtitles off, unsupported source or nothing selected). */
        public static Source none() {
            return new Source(false, null, null, null, null, false);
        }

        public boolean isBound() {
            return mBound;
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

        public Session(boolean aiEnabled, boolean keyConfigured, int displayMode, String targetLanguage,
                       String snapshotStatus) {
            mAiEnabled = aiEnabled;
            mKeyConfigured = keyConfigured;
            mDisplayMode = displayMode;
            mTargetLanguage = targetLanguage;
            mSnapshotStatus = snapshotStatus;
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
    }

    /** Counters the diagnostic report is allowed to print; they never contain subtitle text. */
    public static final class Counters {
        private final int mRequests;
        private final int mDeliveredItems;
        private final int mFailedBatches;
        private final int mCancelledBatches;
        private final int mCacheEntries;
        private final long mCacheBytes;

        public Counters(int requests, int deliveredItems, int failedBatches, int cancelledBatches,
                        int cacheEntries, long cacheBytes) {
            mRequests = requests;
            mDeliveredItems = deliveredItems;
            mFailedBatches = failedBatches;
            mCancelledBatches = cancelledBatches;
            mCacheEntries = cacheEntries;
            mCacheBytes = cacheBytes;
        }

        public static Counters empty() {
            return new Counters(0, 0, 0, 0, 0, 0);
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
