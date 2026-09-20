package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import com.google.android.exoplayer2.C;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** T13 acceptance for the exported archive: entries, coverage note and the refusal path. */
public class SubtitleExportBundleTest {
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private static SubtitleExportSnapshot snapshot(SubtitleTimeline timeline, Map<String, String> translations) {
        return new SubtitleExportSnapshot(1_700_000_000_000L,
                new SubtitleExportSnapshot.Source(true, "dash", "text/vtt", "en", "a.en", true),
                new SubtitleExportSnapshot.Session(true, true, SubtitleComposer.MODE_BILINGUAL, "zh-Hans", "OK"),
                new SubtitleExportSnapshot.Counters(2, 2, 1, 0,
                        translations != null ? translations.size() : 0, 32),
                timeline, translations, Collections.<String>emptyList());
    }

    private static SubtitleTimeline timeline() {
        return new SubtitleTimeline(Arrays.asList(
                new SubtitleFrame(0, 1_000_000, Arrays.asList(
                        new SubtitleItem("a", "One"), new SubtitleItem("b", "Two"))),
                new SubtitleFrame(1_000_000, C.TIME_UNSET, Arrays.asList(
                        new SubtitleItem("c", "\u4e09")))),
                "fp");
    }

    private static Map<String, String> unzip(byte[] bytes) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes));
        ZipEntry entry;

        while ((entry = zip.getNextEntry()) != null) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4_096];
            int read;

            while ((read = zip.read(chunk)) > 0) {
                buffer.write(chunk, 0, read);
            }

            entries.put(entry.getName(), new String(buffer.toByteArray(), UTF_8));
        }

        zip.close();

        return entries;
    }

    @Test
    public void refusesWithoutATimelineInsteadOfWritingAnEmptyArchive() {
        SubtitleExportBundle.Result result = SubtitleExportBundle.build(snapshot(null, null));

        assertFalse(result.isSuccess());
        assertEquals(SubtitleExportBundle.Failure.NO_TIMELINE, result.getFailure());
        assertNull(result.getBytes());
        assertEquals(0, result.getEntries().size());
    }

    @Test
    public void refusesAnEmptyTimeline() {
        SubtitleExportBundle.Result result = SubtitleExportBundle.build(snapshot(
                new SubtitleTimeline(Collections.<SubtitleFrame>emptyList(), "fp"), null));

        assertFalse(result.isSuccess());
        assertEquals(SubtitleExportBundle.Failure.NO_TIMELINE, result.getFailure());
    }

    @Test
    public void writesTheOriginalAndTheCoverageNoteWhenNothingIsTranslated() throws IOException {
        SubtitleExportBundle.Result result = SubtitleExportBundle.build(
                snapshot(timeline(), Collections.<String, String>emptyMap()));

        assertTrue(result.isSuccess());
        assertEquals(Arrays.asList(SubtitleExportBundle.FILE_ORIGINAL, SubtitleExportBundle.FILE_README),
                result.getEntries());

        Map<String, String> entries = unzip(result.getBytes());
        String original = entries.get(SubtitleExportBundle.FILE_ORIGINAL);
        String readme = entries.get(SubtitleExportBundle.FILE_README);

        assertTrue(original.contains("One"));
        assertTrue(original.contains("Two"));
        assertTrue("the third item is CJK text", original.contains("\u4e09"));
        assertEquals(3, SubtitleExportBundle.build(snapshot(timeline(), Collections.<String, String>emptyMap()))
                .getCoverage().getItems());
        assertTrue(readme.contains("translatedItems=0"));
        assertTrue(readme.contains("coveragePercent=0"));
        assertTrue(readme.contains("missingItems=3"));
    }

    @Test
    public void addsTranslatedAndBilingualFilesWhenTranslationsExist() throws IOException {
        Map<String, String> translations = new LinkedHashMap<>();
        translations.put("a", "\u4e00");
        translations.put("b", "\u4e8c");

        SubtitleExportBundle.Result result = SubtitleExportBundle.build(snapshot(timeline(), translations));

        assertTrue(result.isSuccess());
        assertEquals(Arrays.asList(SubtitleExportBundle.FILE_ORIGINAL, SubtitleExportBundle.FILE_TRANSLATED,
                SubtitleExportBundle.FILE_BILINGUAL, SubtitleExportBundle.FILE_README), result.getEntries());
        assertEquals(2, result.getCoverage().getTranslatedItems());

        Map<String, String> entries = unzip(result.getBytes());

        assertTrue(entries.containsKey(SubtitleExportBundle.FILE_TRANSLATED));
        assertTrue(entries.get(SubtitleExportBundle.FILE_TRANSLATED).contains("\u4e00"));
        assertTrue("the untranslated item still appears through the original fallback",
                entries.get(SubtitleExportBundle.FILE_TRANSLATED).contains("\u4e09"));
        assertTrue(entries.get(SubtitleExportBundle.FILE_BILINGUAL).contains("One\n\u4e00"));
    }

    @Test
    public void theCoverageNoteDisclosesTheCacheBoundAndTheEstimatedEnd() throws IOException {
        Map<String, String> entries = unzip(SubtitleExportBundle.build(
                snapshot(timeline(), Collections.<String, String>emptyMap())).getBytes());
        String readme = entries.get(SubtitleExportBundle.FILE_README);

        assertTrue(readme.contains("bounded"));
        assertTrue(readme.contains("not a full-video translation"));
        assertTrue(readme.contains(String.valueOf(SubtitleTranslationCache.MAX_ENTRIES)));
        assertTrue(readme.contains("estimate"));
        assertTrue("the note never carries a source URL", !readme.contains("http"));
        assertNotNull(entries.get(SubtitleExportBundle.FILE_ORIGINAL));
    }

    @Test
    public void theArchiveIsReadableUtf8WithTheDocumentedEntryNames() throws IOException {
        Map<String, String> entries = unzip(SubtitleExportBundle.build(
                snapshot(timeline(), Collections.<String, String>emptyMap())).getBytes());

        assertEquals(2, entries.size());
        assertTrue(entries.containsKey("original.srt"));
        assertTrue(entries.containsKey("README.txt"));
        assertTrue(entries.get("original.srt").endsWith("\r\n\r\n"));
    }
}
