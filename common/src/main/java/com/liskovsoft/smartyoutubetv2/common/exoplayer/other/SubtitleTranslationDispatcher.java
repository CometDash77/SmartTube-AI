package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import java.util.List;

/**
 * Runs translation batches one at a time against a service seam.
 *
 * <p>Rules (plan 6.3):
 * <ul>
 *     <li>At most one call in flight; a new call starts only after the previous one reported back,
 *     and never less than one second after the previous start.</li>
 *     <li>Cancellation on seek/release frees the slot only when the cancelled call reports back, so
 *     a slow cancellation cannot silently turn into a second concurrent call.</li>
 *     <li>Results are accepted only for the batch that is currently in flight; a cancelled batch's
 *     late success or failure is dropped.</li>
 *     <li>A retryable failure may be planned again; once the attempt budget is used up the ids are
 *     marked done so no tick can resubmit them.</li>
 * </ul>
 */
public class SubtitleTranslationDispatcher {
    public static final long MIN_START_INTERVAL_MS = 1_000;
    /** Consecutive failed batches pause the whole session instead of hammering the service. */
    public static final long FAILURE_COOLDOWN_MS = 30_000;
    public static final int MAX_CONSECUTIVE_FAILURES = 2;

    public interface TranslationCall {
        void cancel();
    }

    public interface TranslationService {
        /** Starts a call for the batch; the dispatcher owns the returned call. */
        TranslationCall translate(SubtitleBatch batch, Callback callback);
    }

    public interface Callback {
        void onSuccess(SubtitleBatch batch, List<String> translations);

        void onFailure(SubtitleBatch batch);
    }

    public interface Clock {
        long elapsedRealtimeMs();
    }

    public interface ResultListener {
        void onBatchResult(SubtitleBatch batch, List<String> translations, boolean success);
    }

    private final SubtitleBatchPlanner mPlanner;
    private final SubtitleTranslationCache mCache;
    private final TranslationService mService;
    private final Clock mClock;
    private final ResultListener mListener;
    private SubtitleTimeline mTimeline;
    private SubtitleBatch mInFlight;
    private TranslationCall mInFlightCall;
    private boolean mCancelled;
    private long mPositionUs;
    private long mLastStartMs = Long.MIN_VALUE / 2;
    private long mCooldownUntilMs;
    private int mConsecutiveFailures;
    private boolean mFirstBatch = true;

    public SubtitleTranslationDispatcher(SubtitleBatchPlanner planner, SubtitleTranslationCache cache,
                                         TranslationService service, Clock clock, ResultListener listener) {
        mPlanner = planner;
        mCache = cache;
        mService = service;
        mClock = clock;
        mListener = listener;
    }

    public void setTimeline(SubtitleTimeline timeline) {
        mTimeline = timeline;
        mFirstBatch = true;
        mPlanner.reset(); // a new snapshot has its own ids: old done/pending bookkeeping is void
    }

    public void setPosition(long positionUs) {
        mPositionUs = positionUs;
    }

    public void setFirstBatch(boolean firstBatch) {
        mFirstBatch = firstBatch;
    }

    public boolean isBusy() {
        return mInFlight != null;
    }

    /** True while consecutive batch failures pause the session. */
    public boolean isCoolingDown() {
        return mClock.elapsedRealtimeMs() < mCooldownUntilMs;
    }

    /** Milliseconds left of the current pause, for a menu that may want to say how long. */
    public long getCooldownRemainingMs() {
        return Math.max(0, mCooldownUntilMs - mClock.elapsedRealtimeMs());
    }

    /**
     * Starts the next batch when the slot and the interval allow it.
     *
     * @return true when a call was started
     */
    public boolean tick() {
        if (mInFlight != null || mTimeline == null) {
            return false;
        }

        long nowMs = mClock.elapsedRealtimeMs();

        if (nowMs - mLastStartMs < MIN_START_INTERVAL_MS) {
            return false;
        }

        if (nowMs < mCooldownUntilMs) {
            return false; // batch-failure cooldown: the original subtitles keep playing
        }

        SubtitleBatch batch = mPlanner.nextBatch(mTimeline, mPositionUs, mFirstBatch);

        if (batch == null) {
            return false;
        }

        mPlanner.markQueued(batch);
        mInFlight = batch;
        mFirstBatch = false;
        mLastStartMs = nowMs;
        mInFlightCall = mService.translate(batch, new Callback() {
            @Override
            public void onSuccess(SubtitleBatch resultBatch, List<String> translations) {
                handleSuccess(resultBatch, translations);
            }

            @Override
            public void onFailure(SubtitleBatch resultBatch) {
                handleFailure(resultBatch);
            }
        });

        return true;
    }

    /**
     * Pauses dispatch for an externally decided delay, for example a rate-limit {@code Retry-After}
     * from {@link SubtitleRetryPolicy}. A shorter existing pause is never shortened.
     */
    public void pauseFor(long delayMs) {
        if (delayMs > 0) {
            mCooldownUntilMs = Math.max(mCooldownUntilMs, mClock.elapsedRealtimeMs() + delayMs);
        }
    }

    /** Seek/release: cancels the current call; the slot stays busy until it reports back. */
    public void cancel() {
        if (mInFlightCall != null) {
            mInFlightCall.cancel();
            mCancelled = true; // its late result belongs to an abandoned position
        }
    }

    private void handleSuccess(SubtitleBatch batch, List<String> translations) {
        if (batch != mInFlight) {
            return; // a replaced batch: never repaint, never cache as current
        }

        boolean cancelled = mCancelled;
        release();

        if (cancelled) {
            return; // the position was abandoned: drop the result but free the slot
        }

        mConsecutiveFailures = 0;
        mCooldownUntilMs = 0;

        if (translations != null) {
            List<SubtitleItem> items = batch.getItems();

            for (int i = 0; i < items.size() && i < translations.size(); i++) {
                String translation = translations.get(i);

                if (translation != null && !translation.trim().isEmpty()) {
                    mCache.put(items.get(i).getItemId(), translation);
                }
            }
        }

        mPlanner.markFinished(batch, true);

        if (mListener != null) {
            mListener.onBatchResult(batch, translations, true);
        }
    }

    private void handleFailure(SubtitleBatch batch) {
        if (batch != mInFlight) {
            return;
        }

        boolean cancelled = mCancelled;
        release();

        if (cancelled) {
            return;
        }

        mConsecutiveFailures++;

        if (mConsecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            mCooldownUntilMs = mClock.elapsedRealtimeMs() + FAILURE_COOLDOWN_MS;
            mConsecutiveFailures = 0;
        }

        for (SubtitleItem item : batch.getItems()) {
            if (!mCache.recordFailure(item.getItemId())) {
                mPlanner.markDone(item.getItemId()); // budget used up: never resubmitted by a tick
            }
        }

        mPlanner.markFinished(batch, false);

        if (mListener != null) {
            mListener.onBatchResult(batch, null, false);
        }
    }

    private void release() {
        mInFlight = null;
        mInFlightCall = null;
        mCancelled = false;
    }
}
