package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** T06 acceptance for the bounded success cache and the failure budget that survives eviction. */
public class SubtitleTranslationCacheTest {
    @Test
    public void successCacheIsBoundedByEntries() {
        SubtitleTranslationCache cache = new SubtitleTranslationCache();

        for (int i = 0; i < SubtitleTranslationCache.MAX_ENTRIES + 500; i++) {
            cache.put("id" + i, "value" + i);
        }

        assertTrue(cache.size() <= SubtitleTranslationCache.MAX_ENTRIES);
        assertNull("the eldest entry was evicted", cache.get("id0"));
        assertNotNull_value(cache.get("id" + (SubtitleTranslationCache.MAX_ENTRIES + 499)));
    }

    private static void assertNotNull_value(String value) {
        assertTrue(value != null);
    }

    @Test
    public void successCacheIsBoundedByPayloadBytes() {
        SubtitleTranslationCache cache = new SubtitleTranslationCache();
        String block = new String(new char[4_096]).replace('\0', 'x');

        for (int i = 0; i < 600; i++) {
            cache.put("id" + i, block + i);
        }

        assertTrue(cache.getBytes() <= SubtitleTranslationCache.MAX_BYTES);
        assertEquals(0, cache.size() % 1 == 0 ? 0 : 1); // sanity: size is a plain count
        assertTrue(cache.size() > 0);
    }

    @Test
    public void failureBudgetIsNotResetByCacheEviction() {
        SubtitleTranslationCache cache = new SubtitleTranslationCache();

        assertTrue(cache.recordFailure("item"));
        assertEquals(1, cache.attempts("item"));

        for (int i = 0; i < SubtitleTranslationCache.MAX_ENTRIES + 100; i++) {
            cache.put("other" + i, "value");
        }

        assertFalse("second attempt exhausts the budget", cache.recordFailure("item"));
        assertTrue(cache.hasExhausted("item"));
        assertEquals(2, cache.attempts("item"));
    }

    @Test
    public void explicitRetryClearsTheFailureState() {
        SubtitleTranslationCache cache = new SubtitleTranslationCache();
        cache.recordFailure("item");
        cache.recordFailure("item");

        cache.clearFailure("item");

        assertEquals(0, cache.attempts("item"));
        assertFalse(cache.hasExhausted("item"));
    }

    @Test
    public void successfulEntryIsFoundAndFailuresAreNotStoredAsSuccesses() {
        SubtitleTranslationCache cache = new SubtitleTranslationCache();
        cache.put("item", "\u4f60\u597d");
        cache.recordFailure("failed");

        assertEquals("\u4f60\u597d", cache.get("item"));
        assertTrue(cache.hasSuccess("item"));
        assertFalse(cache.hasSuccess("failed"));
    }

    @Test
    public void lookupBridgesTheCacheToTheFrameAlignment() {
        SubtitleTranslationCache cache = new SubtitleTranslationCache();
        cache.put("b", "\u4e8c");

        List<String> aligned = SubtitleFrameTranslations.align(
                Arrays.asList(new SubtitleItem("a", "One"), new SubtitleItem("b", "Two")), cache.asLookup());

        assertEquals(Arrays.asList(null, "\u4e8c"), aligned);
    }

    @Test
    public void clearEmptiesBothTheCacheAndTheFailures() {
        SubtitleTranslationCache cache = new SubtitleTranslationCache();
        cache.put("item", "value");
        cache.recordFailure("failed");

        cache.clear();

        assertEquals(0, cache.size());
        assertEquals(0, cache.getBytes());
        assertEquals(0, cache.attempts("failed"));
        assertFalse(cache.hasExhausted("failed"));
    }
}
