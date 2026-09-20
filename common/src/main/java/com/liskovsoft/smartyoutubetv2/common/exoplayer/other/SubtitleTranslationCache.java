package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import java.nio.charset.Charset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bounded session cache of successful translations, plus the per-item failure bookkeeping that a
 * least-recently-used eviction must not reset (plan 6.3).
 *
 * <p>Two independent limits are enforced: at most 2,000 entries and at most 2 MiB of UTF-8 payload.
 * Failed entries are never stored as successes; their attempt budget lives in the failure tracker.
 *
 * <p>The map is guarded because a translation callback may write it while the UI thread reads a
 * snapshot for the local export (plan section 14, T13). The lock is the cache instance, so the
 * exported snapshot is a consistent copy rather than a partially written one.
 */
public class SubtitleTranslationCache {
    /** API 1 charset: java.nio.charset.StandardCharsets is API 19 while this module supports 17. */
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    public static final int MAX_ENTRIES = 2_000;
    public static final long MAX_BYTES = 2L * 1024 * 1024L;
    public static final int MAX_FAILURE_ENTRIES = 4_000;
    public static final int MAX_ATTEMPTS = 2;
    /** Export-visible states of one item; a missing entry means "never attempted". */
    public static final String STATUS_TRANSLATED = "TRANSLATED";
    public static final String STATUS_FAILED = "FAILED";

    private final LinkedHashMap<String, String> mEntries = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<String, Integer> mAttempts = new HashMap<>();
    private final List<String> mExhausted = new ArrayList<>();
    private long mBytes;

    /** Stores a successful translation, evicting least-recently-used entries to stay inside bounds. */
    public synchronized void put(String itemId, String translation) {
        if (itemId == null || translation == null) {
            return;
        }

        String previous = mEntries.put(itemId, translation);

        if (previous != null) {
            mBytes -= bytes(previous);
        }

        mBytes += bytes(translation);
        evict();
    }

    public synchronized String get(String itemId) {
        return itemId != null ? mEntries.get(itemId) : null;
    }

    public synchronized boolean hasSuccess(String itemId) {
        return itemId != null && mEntries.containsKey(itemId);
    }

    public synchronized int size() {
        return mEntries.size();
    }

    public synchronized long getBytes() {
        return mBytes;
    }

    public synchronized List<String> getItemIds() {
        return new ArrayList<>(mEntries.keySet());
    }

    /**
     * Consistent copy of the successful entries for one export snapshot; the caller owns it and the
     * live cache can keep changing without touching the copy.
     */
    public synchronized Map<String, String> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(mEntries));
    }

    /**
     * Per-item state for the local export: `TRANSLATED` for a stored non-blank result and
     * `FAILED` for an item that was attempted at least once without a result. Items absent from the
     * returned map were never attempted by this session (for example the run was interrupted).
     */
    public synchronized Map<String, String> statusSnapshot() {
        Map<String, String> status = new LinkedHashMap<>();

        for (String itemId : mEntries.keySet()) {
            String translation = mEntries.get(itemId);

            if (translation != null && !translation.trim().isEmpty()) {
                status.put(itemId, STATUS_TRANSLATED);
            }
        }

        for (String itemId : mAttempts.keySet()) {
            if (!status.containsKey(itemId)) {
                status.put(itemId, STATUS_FAILED);
            }
        }

        for (String itemId : mExhausted) {
            if (!status.containsKey(itemId)) {
                status.put(itemId, STATUS_FAILED);
            }
        }

        return Collections.unmodifiableMap(status);
    }

    /** Bridges the cache to the frame-to-display alignment without exposing the map. */
    public SubtitleFrameTranslations.TranslationLookup asLookup() {
        return this::get;
    }

    /**
     * Records a failed attempt.
     *
     * @return true while the item may be retried; false once its attempt budget is used up
     */
    public synchronized boolean recordFailure(String itemId) {
        if (itemId == null) {
            return false;
        }

        int attempts = attempts(itemId) + 1;
        mAttempts.put(itemId, attempts);

        if (attempts >= MAX_ATTEMPTS) {
            mExhausted.add(itemId);
            trimFailureState();
            return false;
        }

        trimFailureState();

        return true;
    }

    public synchronized int attempts(String itemId) {
        Integer attempts = mAttempts.get(itemId);
        return attempts != null ? attempts : 0;
    }

    /** A finished failure must not be retried by a later tick. */
    public synchronized boolean hasExhausted(String itemId) {
        return mExhausted.contains(itemId);
    }

    /** An explicit user retry starts a new attempt cycle for the current window. */
    public synchronized void clearFailure(String itemId) {
        mAttempts.remove(itemId);
        mExhausted.remove(itemId);
    }

    public synchronized void clearFailures() {
        mAttempts.clear();
        mExhausted.clear();
    }

    public synchronized void clear() {
        mEntries.clear();
        mBytes = 0;
        clearFailures();
    }

    private void evict() {
        while (!mEntries.isEmpty() && (mEntries.size() > MAX_ENTRIES || mBytes > MAX_BYTES)) {
            String eldest = mEntries.keySet().iterator().next();
            String value = mEntries.remove(eldest);
            mBytes -= bytes(value);
        }
    }

    /** Failure bookkeeping is small but bounded too, and LRU eviction never touches it. */
    private void trimFailureState() {
        while (mAttempts.size() > MAX_FAILURE_ENTRIES && !mExhausted.isEmpty()) {
            String id = mExhausted.remove(0);
            mAttempts.remove(id);
        }
    }

    private static long bytes(String text) {
        return text.getBytes(UTF_8).length;
    }
}
