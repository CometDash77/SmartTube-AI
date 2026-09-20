package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Test;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** T13 acceptance for the desensitised diagnostic report: whitelist, availability and no leaks. */
public class SubtitleDiagnosticReportTest {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String SUBTITLE_TEXT = "SECRET-SUBTITLE-TEXT";
    private static final String SECRET_KEY = "sk-live-1234567890";
    private static final String SECRET_URL = "https://api.example.com/v1?token=abc";

    private static SubtitleExportSnapshot richSnapshot() {
        Map<String, String> translations = new LinkedHashMap<>();
        translations.put("a", "\u4e00");
        translations.put("b", SUBTITLE_TEXT);

        SubtitleExportEventLog events = new SubtitleExportEventLog(() -> 1_000);
        events.add("SNAPSHOT_OK");
        events.add("KEY " + SECRET_KEY + " " + SECRET_URL);

        SubtitleTimeline timeline = new SubtitleTimeline(Arrays.asList(
                new SubtitleFrame(0, 1_000_000, Arrays.asList(new SubtitleItem("a", "One"))),
                new SubtitleFrame(1_000_000, 2_000_000, Arrays.asList(new SubtitleItem("b", "Two")))),
                "fingerprint");

        return new SubtitleExportSnapshot(1_700_000_000_000L,
                new SubtitleExportSnapshot.Source(SubtitleExportSnapshot.PlayerReadiness.READY, true,
                        SubtitleSourceBinder.Status.BOUND, "dash", "text/vtt", "en", "a.en", true),
                new SubtitleExportSnapshot.Session(true, true, SubtitleComposer.MODE_BILINGUAL, "zh-Hans", "OK",
                        "ALREADY_READY", true),
                new SubtitleExportSnapshot.Counters(3, 2, 1, 1, translations.size(), 64, 2, 3, 4),
                timeline, translations, events.snapshot());
    }

    private static SubtitleDiagnosticReport.Environment environment() {
        return new SubtitleDiagnosticReport.Environment("1.2.3", 42, "org.smarttube.beta",
                "11", 30, "NVIDIA", "SHIELD Android TV", 123_456_789L);
    }

    private static List<String> valueLines(String report) {
        List<String> lines = new ArrayList<>();

        for (String line : report.split("\n")) {
            String trimmed = line.replace("\r", "");

            if (!trimmed.isEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                lines.add(trimmed);
            }
        }

        return lines;
    }

    @Test
    public void reportContainsEveryWhitelistedField() {
        String report = SubtitleDiagnosticReport.build(richSnapshot(), environment());

        for (String field : SubtitleDiagnosticReport.WHITELIST) {
            if ("event".equals(field)) {
                assertTrue("event0 missing", report.contains("event0="));
                assertTrue("event1 missing", report.contains("event1="));
                continue;
            }

            assertTrue(field + " missing", report.contains(field + "="));
        }
    }

    @Test
    public void reportShowsTheSessionFactsItExistsFor() {
        String report = SubtitleDiagnosticReport.build(richSnapshot(), environment());

        assertTrue(report.contains("sourceType=dash"));
        assertTrue(report.contains("sourceMime=text/vtt"));
        assertTrue(report.contains("snapshotStatus=OK"));
        assertTrue("format version 2", report.contains("formatVersion=2"));
        assertTrue(report.contains("sourceBound=true"));
        assertTrue(report.contains("subtitlesSelected=true"));
        assertTrue(report.contains("sourceStatus=BOUND"));
        assertTrue(report.contains("playerReadiness=READY"));
        assertTrue(report.contains("lastTimelineRequestResult=ALREADY_READY"));
        assertTrue(report.contains("timelineRequestInFlight=true"));
        assertTrue(report.contains("timelineRequests=2"));
        assertTrue(report.contains("timelineInstalls=3"));
        assertTrue(report.contains("timelineSkips=4"));
        assertTrue(report.contains("timelineFrames=2"));
        assertTrue(report.contains("cacheLimitEntries=" + SubtitleTranslationCache.MAX_ENTRIES));
        assertTrue(report.contains("displayMode=BILINGUAL"));
        assertTrue(report.contains("deviceModel=SHIELD"));
        assertTrue(report.contains("excluded="));
        assertTrue(report.contains("apiKey"));
    }

    @Test
    public void reportWorksWithoutAnySessionTimelineOrKey() {
        String report = SubtitleDiagnosticReport.build(null, null);

        assertTrue(report.contains("timelineFrames=0"));
        assertTrue(report.contains("timelineItems=0"));
        assertTrue(report.contains("aiEnabled=false"));
        assertTrue(report.contains("keyConfigured=false"));
        assertTrue(report.contains("sourceBound=false"));
        assertTrue("a missing observation is never reported as ready", report.contains("playerReadiness=NO_HOST"));
        assertTrue(report.contains("subtitlesSelected=false"));
        assertTrue(report.contains("sourceStatus=UNBOUND"));
        assertTrue(report.contains("lastTimelineRequestResult=NOT_REQUESTED"));
        assertTrue(report.contains("timelineRequestInFlight=false"));
        assertTrue(report.contains("timelineRequests=0"));
        assertTrue(report.contains("snapshotStatus=NOT_REQUESTED"));
        assertTrue(report.contains("eventCount=0"));
        assertTrue(report.contains("appVersionName=unknown"));
        assertTrue(report.length() > 200);
    }

    @Test
    public void reportLeaksNoSecretAndNoSubtitleText() {
        String report = SubtitleDiagnosticReport.build(richSnapshot(), environment());

        assertFalse("subtitle text must never leave the subtitle archive",
                report.contains(SUBTITLE_TEXT));
        assertFalse("the configured key must never be printed", report.contains(SECRET_KEY));
        assertFalse("an event may not smuggle a URL", report.contains(SECRET_URL));
        assertFalse(report.contains("token=abc"));
        assertFalse(report.contains("api.example.com"));

        for (String line : valueLines(report)) {
            String value = line.substring(line.indexOf('=') + 1);

            assertFalse(line, value.contains("http://"));
            assertFalse(line, value.contains("https://"));
            assertFalse(line, value.contains("Bearer"));
            assertFalse(line, value.contains("Cookie"));
            assertFalse(line, value.contains("Authorization"));
            assertFalse(line, value.contains("="));
        }
    }

    @Test
    public void hostileEventCodesAreReducedToTheCodeAlphabet() {
        assertEquals("KEYSKLIVE1234567890", SubtitleExportEventLog.sanitize("key=sk-live-1234567890"));
        assertEquals("KEYSKLIVEHTTPX", SubtitleExportEventLog.sanitize("key=sk-live / http://x"));
        assertEquals("UNKNOWN", SubtitleExportEventLog.sanitize("===="));
        assertEquals("UNKNOWN", SubtitleExportEventLog.sanitize(null));

        String report = SubtitleDiagnosticReport.build(richSnapshot(), environment());
        String eventLine = null;

        for (String line : valueLines(report)) {
            if (line.startsWith("event1=")) {
                eventLine = line;
            }
        }

        assertTrue(eventLine != null);
        assertTrue(eventLine, eventLine.matches("event1=\\+\\d+ms [A-Z0-9_]+"));
    }

    @Test
    public void valuesStayBoundedAndOnOneLine() {
        assertEquals("unknown", SubtitleDiagnosticReport.value(null));
        assertEquals("unknown", SubtitleDiagnosticReport.value("   "));
        assertEquals("ab_c", SubtitleDiagnosticReport.value("a\nb=c"));
        assertEquals(80, SubtitleDiagnosticReport.value(new String(new char[500]).replace('\0', 'x')).length());
    }

    @Test
    public void reportIsUtf8Encoded() {
        SubtitleExportSnapshot snapshot = richSnapshot();
        byte[] bytes = SubtitleDiagnosticReport.buildUtf8(snapshot, environment());

        assertTrue(bytes.length > 0);
        assertEquals(SubtitleDiagnosticReport.build(snapshot, environment()), new String(bytes, UTF_8));
    }

    @Test
    public void environmentDefaultsAreSafe() {
        SubtitleDiagnosticReport.Environment unknown = SubtitleDiagnosticReport.Environment.unknown();

        assertEquals(-1, unknown.getAppVersionCode());
        assertEquals(-1, unknown.getAndroidSdk());
        assertEquals(-1L, unknown.getStorageFreeBytes());
        assertEquals("unknown", SubtitleDiagnosticReport.value(unknown.getAppVersionName()));
    }
}
