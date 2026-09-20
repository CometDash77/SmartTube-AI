package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** T13 acceptance for the click-time snapshot, the bounded event log and the cache copy. */
public class SubtitleExportSnapshotTest {
    private static SubtitleExportSnapshot snapshot(Map<String, String> translations, List<String> events) {
        return new SubtitleExportSnapshot(1_700_000_000_000L,
                new SubtitleExportSnapshot.Source(true, "sabr", "application/ttml+xml", "en", "a.en", true),
                new SubtitleExportSnapshot.Session(true, true, SubtitleComposer.MODE_BILINGUAL, "zh-Hans", "OK"),
                new SubtitleExportSnapshot.Counters(1, 2, 3, 4, translations.size(), 64),
                new SubtitleTimeline(Collections.<SubtitleFrame>emptyList(), "fp"), translations, events);
    }

    @Test
    public void translationsAreCopiedAtClickTime() {
        Map<String, String> live = new LinkedHashMap<>();
        live.put("a", "one");
        SubtitleExportSnapshot snapshot = snapshot(live, new ArrayList<String>());

        live.put("b", "two");
        live.remove("a");

        assertEquals(1, snapshot.getTranslations().size());
        assertEquals("one", snapshot.getTranslations().get("a"));
    }

    @Test
    public void theCopiedCollectionsCannotBeModifiedThroughTheSnapshot() {
        SubtitleExportSnapshot snapshot = snapshot(new LinkedHashMap<String, String>(), new ArrayList<String>());

        try {
            snapshot.getTranslations().put("x", "y");
            fail("the snapshot map must be read-only");
        } catch (UnsupportedOperationException expected) {
            // expected
        }

        try {
            snapshot.getEvents().add("x");
            fail("the snapshot event list must be read-only");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    @Test
    public void aMissingPartNeverBreaksTheSnapshot() {
        SubtitleExportSnapshot snapshot = new SubtitleExportSnapshot(1L, null, null, null, null, null, null);

        assertFalse(snapshot.getSource().isBound());
        assertFalse(snapshot.getSession().isAiEnabled());
        assertEquals(0, snapshot.getCounters().getCacheEntries());
        assertNull(snapshot.getTimeline());
        assertEquals(0, snapshot.getTranslations().size());
        assertEquals(0, snapshot.getEvents().size());
        assertTrue(snapshot.getSession().getSnapshotStatus().length() > 0);
    }

    @Test
    public void printingASnapshotNeverRevealsSubtitleText() {
        Map<String, String> translations = new LinkedHashMap<>();
        translations.put("a", "SECRET-SUBTITLE-TEXT");

        String text = snapshot(translations, new ArrayList<String>()).toString();

        assertFalse(text.contains("SECRET-SUBTITLE-TEXT"));
        assertTrue(text.contains("translations=1"));
    }

    @Test
    public void eventLogReducesAnyInputToTheCodeAlphabet() {
        assertEquals("SNAPSHOT_OK", SubtitleExportEventLog.sanitize("snapshot_ok"));
        assertEquals("KEYSKLIVE123", SubtitleExportEventLog.sanitize("key=sk-live/123"));
        assertEquals("UNKNOWN", SubtitleExportEventLog.sanitize("  "));
        assertEquals("UNKNOWN", SubtitleExportEventLog.sanitize("!!!"));
    }

    @Test
    public void eventLogIsBoundedAndKeepsTheNewestEntries() {
        SubtitleExportEventLog log = new SubtitleExportEventLog(() -> 0L);

        for (int i = 0; i < SubtitleExportEventLog.MAX_EVENTS + 5; i++) {
            log.add("E" + i);
        }

        List<String> events = log.snapshot();

        assertEquals(SubtitleExportEventLog.MAX_EVENTS, events.size());
        assertEquals("+0ms E" + (SubtitleExportEventLog.MAX_EVENTS + 4),
                events.get(SubtitleExportEventLog.MAX_EVENTS - 1));

        log.clear();

        assertEquals(0, log.snapshot().size());
    }

    @Test
    public void eventLogUsesMonotonicOffsets() {
        final long[] now = {1_000};
        SubtitleExportEventLog log = new SubtitleExportEventLog(() -> now[0]);

        now[0] = 1_500;
        log.add("OK");
        now[0] = 900; // a clock that goes backwards must not produce a negative offset
        log.add("BACK");

        assertEquals(Arrays.asList("+500ms OK", "+0ms BACK"), log.snapshot());
    }

    @Test
    public void cacheSnapshotIsAConsistentCopy() {
        SubtitleTranslationCache cache = new SubtitleTranslationCache();
        cache.put("a", "\u4e00");

        Map<String, String> copy = cache.snapshot();

        cache.put("b", "\u4e8c");
        cache.recordFailure("a");

        assertEquals(1, copy.size());
        assertEquals("\u4e00", copy.get("a"));
        assertNull(copy.get("b"));
        assertEquals(2, cache.snapshot().size());
    }

    @Test
    public void anEmptyCacheSnapshotsAsEmpty() {
        Map<String, String> copy = new SubtitleTranslationCache().snapshot();

        assertEquals(0, copy.size());
    }

    @Test
    public void cacheStatusClassifiesTranslatedFailedAndNeverAttempted() {
        SubtitleTranslationCache cache = new SubtitleTranslationCache();
        cache.put("translated", "\u4e00");
        cache.recordFailure("failed");
        cache.recordFailure("failed"); // the attempt budget is used up
        cache.recordFailure("retrying");

        Map<String, String> status = cache.statusSnapshot();

        assertEquals(SubtitleTranslationCache.STATUS_TRANSLATED, status.get("translated"));
        assertEquals(SubtitleTranslationCache.STATUS_FAILED, status.get("failed"));
        assertEquals("an attempted item without a result is failed", SubtitleTranslationCache.STATUS_FAILED,
                status.get("retrying"));
        assertNull("an item the session never touched is absent, i.e. NOT_ATTEMPTED", status.get("untouched"));
    }

    @Test
    public void theSnapshotCopiesTheStatusMap() {
        Map<String, String> live = new LinkedHashMap<>();
        live.put("a", SubtitleTranslationCache.STATUS_FAILED);
        SubtitleExportSnapshot snapshot = new SubtitleExportSnapshot(1L, null, null, null, null, null, live, null);

        live.put("b", SubtitleTranslationCache.STATUS_TRANSLATED);

        assertEquals(1, snapshot.getTranslationStatus().size());
        assertEquals(SubtitleTranslationCache.STATUS_FAILED, snapshot.getTranslationStatus().get("a"));
    }

    @Test
    public void theSnapshotKeepsTheTimelineItWasGiven() {
        SubtitleTimeline timeline = new SubtitleTimeline(Collections.<SubtitleFrame>emptyList(), "fp");
        SubtitleExportSnapshot snapshot = new SubtitleExportSnapshot(1L, null, null, null, timeline, null, null);

        assertSame(timeline, snapshot.getTimeline());
    }
}
