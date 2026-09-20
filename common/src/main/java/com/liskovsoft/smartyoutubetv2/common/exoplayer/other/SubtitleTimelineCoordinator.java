package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;

import java.util.concurrent.Executor;

/**
 * Runs the one extra subtitle read of the selected source and owns its request identity (plan 4.1
 * and task N1).
 *
 * <p>Guarantees:
 * <ul>
 *     <li>One read per source identity: repeated events of the same source (video loaded, track
 *     changed, seek) neither restart nor duplicate the attempt, and a payload already decoded for the
 *     same source locator is re-attributed instead of being downloaded and decoded again.</li>
 *     <li>A real change (new video, replaced media source, different track, subtitles off, release)
 *     abandons the previous attempt through its own cancellation flag, so the late answer can neither
 *     install a timeline nor overwrite the diagnostic status of the live attempt, and it cannot free
 *     the newer attempt's slot.</li>
 *     <li>A failed attempt releases its slot and the last status is kept as evidence; only an explicit
 *     later event starts another attempt, so no unbounded retry loop exists.</li>
 *     <li>The translation switch is not consulted: the original timeline is prepared for the local
 *     export as well, and nothing here is a paid request.</li>
 * </ul>
 *
 * <p>The class performs no Android call itself: the caller supplies the worker, the main-thread
 * poster and the player surface, which keeps the ordering deterministic in tests.
 */
public class SubtitleTimelineCoordinator {
    /** Player surface the coordinator reads the current subtitle source from. */
    public interface Host {
        /** The source the user is actually watching, or null when subtitles are off. */
        SelectedSubtitleSource getSelectedSource();

        Format getSelectedFormat();

        SubtitleSnapshotFetcher.PayloadFactory createPayloadFactory();

        /** Identity of the source the installed timeline belongs to, or null while nothing is bound. */
        String getCurrentSourceKey();

        /** Installed timeline, but only while it belongs to the currently selected source. */
        SubtitleTimeline getTimelineOfCurrentSource();

        /** Any installed timeline, including one of another generation of the same content. */
        SubtitleTimeline getInstalledTimeline();

        /** Installs (or re-attributes) a timeline for the current source. */
        void installTimeline(SubtitleTimeline timeline);
    }

    /** Blocking fetch executed on the worker; production uses {@link #systemFetcher()}. */
    public interface Fetcher {
        SubtitleSnapshotFetcher.Result fetch(SelectedSubtitleSource source, Format format,
                                            SubtitleSnapshotFetcher.PayloadFactory payloadFactory,
                                            SubtitleSnapshotReader.Cancellation cancellation);
    }

    /** One settled attempt, reported on the main thread. */
    public interface Listener {
        /**
         * @param status    snapshot status of the settled attempt
         * @param accepted  true when the attempt still belonged to the selected source; only then may
         *                  the caller record it as the current diagnostic state
         * @param installed true when the attempt's timeline was installed
         */
        void onAttemptSettled(String status, boolean accepted, boolean installed);
    }

    /** Status reported when an already decoded payload was reused for another source generation. */
    public static final String STATUS_REUSED = "REUSED";

    /**
     * Fixed outcome of one {@link #request()} call. A plain boolean could not tell a started read
     * from a deduplicated one, a refused precondition or a missing identity (task R1).
     */
    public enum RequestResult {
        /** A new read of the selected source was started. */
        STARTED,
        /** The selected source already has its decoded timeline; nothing had to be fetched. */
        ALREADY_READY,
        /** An already decoded timeline of the same payload was re-attributed to a new generation. */
        REUSED,
        /** A read of the same source identity is already running: it is reused, not restarted. */
        IN_FLIGHT,
        /** No subtitle source is selected (subtitles off, or no track bound). */
        NO_SOURCE,
        /** A source is selected but the player does not expose its selected format yet. */
        NO_FORMAT,
        /** A source and its format exist but the player cannot open the payload yet. */
        NO_FACTORY,
        /** The selected source has no usable identity (missing source key or content locator). */
        NO_IDENTITY
    }

    /** The production fetch: the real reader over the payload of the selected source. */
    public static Fetcher systemFetcher() {
        return (source, format, payloadFactory, cancellation) -> new SubtitleSnapshotFetcher(
                new SubtitleSnapshotReader(), payloadFactory).fetch(source, format, C.TIME_UNSET, cancellation);
    }

    private final Host mHost;
    private final Executor mWorker;
    private final Executor mMain;
    private final Fetcher mFetcher;
    private final Listener mListener;
    private final SubtitleTimelineScheduler mRequests = new SubtitleTimelineScheduler();
    private int mMediaGeneration;
    private String mInstalledLocator; // locator of the timeline this coordinator installed

    public SubtitleTimelineCoordinator(Host host, Executor worker, Executor main, Fetcher fetcher,
                                       Listener listener) {
        mHost = host;
        mWorker = worker;
        mMain = main;
        mFetcher = fetcher;
        mListener = listener;
    }

    /**
     * A new video or a replaced media source. The media generation moves on, so every attempt of the
     * previous generation is abandoned even when the track identity string looks the same.
     */
    public void invalidateSourceContext() {
        mMediaGeneration++;
        mRequests.cancel();
    }

    /** Drops the current attempt (release, or a source that became unknown). */
    public void cancel() {
        mRequests.cancel();
    }

    public boolean isAttemptInFlight() {
        return mRequests.getInFlight() != null;
    }

    /**
     * Starts the read of the currently selected source when it is still needed.
     *
     * @return the fixed outcome; every refusal reason is distinguished before a slot is taken
     */
    public RequestResult request() {
        SelectedSubtitleSource source = mHost.getSelectedSource();

        if (source == null) {
            mRequests.cancel(); // subtitles off (or not bound yet): no attempt may outlive it

            return RequestResult.NO_SOURCE;
        }

        String locator = locatorOf(source);
        String sourceKey = mHost.getCurrentSourceKey();

        if (mHost.getTimelineOfCurrentSource() != null) {
            mInstalledLocator = locator;

            return RequestResult.ALREADY_READY; // this source identity already has its decoded timeline
        }

        Format format = mHost.getSelectedFormat();

        if (format == null) {
            return RequestResult.NO_FORMAT; // the next event may expose the selected format
        }

        SubtitleSnapshotFetcher.PayloadFactory factory = mHost.createPayloadFactory();

        if (factory == null) {
            return RequestResult.NO_FACTORY; // the surface cannot open the payload yet; retry later
        }

        if (sourceKey == null || locator == null) {
            // A request without identity could only be mis-attributed later; the values themselves are
            // never recorded. A later valid event starts a fresh attempt.
            return RequestResult.NO_IDENTITY;
        }

        // The same payload in another generation (manifest rebuilt, track re-resolved): re-attribute
        // the decoded timeline instead of downloading and decoding the same text again.
        SubtitleTimeline installed = mHost.getInstalledTimeline();

        if (installed != null && locator.equals(mInstalledLocator)) {
            mHost.installTimeline(installed);
            mListener.onAttemptSettled(STATUS_REUSED, true, true);

            return RequestResult.REUSED;
        }

        SubtitleTimelineScheduler.Request request = mRequests.begin(mMediaGeneration, sourceKey, locator);

        if (request == null) {
            return RequestResult.IN_FLIGHT; // the same identity is already in flight: reuse it
        }

        mWorker.execute(() -> run(request, source, format, factory));

        return RequestResult.STARTED;
    }

    private void run(SubtitleTimelineScheduler.Request request, SelectedSubtitleSource source,
                     Format format, SubtitleSnapshotFetcher.PayloadFactory factory) {
        SubtitleSnapshotFetcher.Result result;

        try {
            result = mFetcher.fetch(source, format, factory, request.asCancellation());
        } catch (RuntimeException e) {
            // A failing seam must still release the attempt's slot, otherwise the source could never
            // be fetched again (task N1: a failed attempt releases its own occupancy).
            result = new SubtitleSnapshotFetcher.Result(SubtitleSnapshotReader.Status.IO_FAILED, null);
        }

        boolean cancelled = request.isCancelled();
        String status = cancelled ? SubtitleSnapshotReader.Status.CANCELLED.name()
                : (result != null && result.getStatus() != null ? result.getStatus().name() : "UNKNOWN");
        SubtitleTimeline timeline = !cancelled && result != null && result.isUsable() ? result.getTimeline() : null;

        mMain.execute(() -> finish(request, status, timeline));
    }

    private void finish(SubtitleTimelineScheduler.Request request, String status, SubtitleTimeline timeline) {
        boolean current = mRequests.settle(request);
        String currentKey = mHost.getCurrentSourceKey();
        boolean accepted = current && !request.isCancelled()
                && currentKey != null && currentKey.equals(request.getSourceKey());

        if (accepted && timeline != null) {
            mHost.installTimeline(timeline);
            mInstalledLocator = request.getLocator();
        }

        mListener.onAttemptSettled(status, accepted, accepted && timeline != null);
    }

    /**
     * Memory-only content locator of a source. The manifest generation is deliberately excluded, so a
     * rebuilt manifest with the same text is recognised as the same payload.
     */
    static String locatorOf(SelectedSubtitleSource source) {
        if (source == null) {
            return null;
        }

        String vssId = source.getVssId() != null ? source.getVssId() : "";
        String language = source.getLanguageCode() != null ? source.getLanguageCode() : "";

        return vssId + "|" + language + "|" + source.getBaseUrl();
    }
}
