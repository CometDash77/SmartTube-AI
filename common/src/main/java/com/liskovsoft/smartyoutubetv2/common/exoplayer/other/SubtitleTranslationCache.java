package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import java.nio.charset.Charset;

import java.util.ArrayList;
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
 */
public class SubtitleTranslationCache {
    /** API 1 charset: java.nio.charset.StandardCharsets is API 19 while this module supports 17. */
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    public static final int MAX_ENTRIES = 2_000;
    public static final long MAX_BYTES = 2L * 1024 * 1024L;
    public static final int MAX_FAILURE_ENTRIES = 4_000;
    public static final int MAX_ATTEMPTS = 2;

    private final LinkedHashMap<String, String> mEntries = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<String, Integer> mAttempts = new HashMap<>();
    private final List<String> mExhausted = new ArrayList<>();
    private long mBytes;

    /** Stores a successful translation, evicting least-recently-used entries to stay inside bounds. */
    public void put(String itemId, String translation) {
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

    public String get(String itemId) {
        return itemId != null ? mEntries.get(itemId) : null;
    }

    public boolean hasSuccess(String itemId) {
        return itemId != null && mEntries.containsKey(itemId);
    }

    public int size() {
        return mEntries.size();
    }

    public long getBytes() {
        return mBytes;
    }

    public List<String> getItemIds() {
        return new ArrayList<>(mEntries.keySet());
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
    public boolean recordFailure(String itemId) {
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

    public int attempts(String itemId) {
        Integer attempts = mAttempts.get(itemId);
        return attempts != null ? attempts : 0;
    }

    /** A finished failure must not be retried by a later tick. */
    public boolean hasExhausted(String itemId) {
        return mExhausted.contains(itemId);
    }

    /** An explicit user retry starts a new attempt cycle for the current window. */
    public void clearFailure(String itemId) {
        mAttempts.remove(itemId);
        mExhausted.remove(itemId);
    }

    public void clearFailures() {
        mAttempts.clear();
        mExhausted.clear();
    }

    public void clear() {
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
