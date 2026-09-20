package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** T13 acceptance for the export orchestration: click-time snapshot, one job at a time, real result. */
public class SubtitleExportControllerTest {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final long FIXED_TIME = 1_700_000_000_000L;

    /** Never runs a task on its own: the test decides when the background work happens. */
    private static final class HoldingExecutor implements Executor {
        private final List<Runnable> mTasks = new ArrayList<>();

        @Override
        public void execute(Runnable task) {
            mTasks.add(task);
        }

        void runAll() {
            List<Runnable> pending = new ArrayList<>(mTasks);
            mTasks.clear();

            for (Runnable task : pending) {
                task.run();
            }
        }

        int size() {
            return mTasks.size();
        }
    }

    private static final class RecordingWriter implements SubtitleExportController.FileWriter {
        private final List<String> mBaseNames = new ArrayList<>();
        private final List<String> mExtensions = new ArrayList<>();
        private final List<byte[]> mPayloads = new ArrayList<>();
        private SubtitleExportWriteOutcome.Status mStatus = SubtitleExportWriteOutcome.Status.OK;
        private RuntimeException mFailure;

        @Override
        public SubtitleExportWriteOutcome write(String baseName, String extension, byte[] bytes) {
            if (mFailure != null) {
                throw mFailure;
            }

            mBaseNames.add(baseName);
            mExtensions.add(extension);
            mPayloads.add(bytes);
            String name = baseName + "." + extension;

            if (mStatus != SubtitleExportWriteOutcome.Status.OK) {
                return SubtitleExportWriteOutcome.failure(mStatus, null, null);
            }

            return new SubtitleExportWriteOutcome(SubtitleExportWriteOutcome.Status.OK, name,
                    "/storage/emulated/0/Documents/SmartTube/Exports/" + name, bytes.length);
        }
    }

    private static SubtitleItem item(String id, String text) {
        return new SubtitleItem(id, text);
    }

    private static SubtitleFrame frame(long startUs, long endUs, SubtitleItem... items) {
        return new SubtitleFrame(startUs, endUs, Arrays.asList(items));
    }

    private static SubtitleTimeline timeline(String text) {
        return new SubtitleTimeline(Arrays.asList(frame(0, 1_000_000, item("a", text))), "fp");
    }

    private static SubtitleExportSnapshot snapshot(SubtitleTimeline timeline, Map<String, String> translations,
                                                   boolean aiEnabled, boolean keyConfigured) {
        return new SubtitleExportSnapshot(FIXED_TIME,
                new SubtitleExportSnapshot.Source(true, "dash", "text/vtt", "en", "a.en", true),
                new SubtitleExportSnapshot.Session(aiEnabled, keyConfigured, SubtitleComposer.MODE_BILINGUAL,
                        "zh-Hans", "OK"),
                new SubtitleExportSnapshot.Counters(1, 1, 0, 0,
                        translations != null ? translations.size() : 0, 16),
                timeline, translations, new ArrayList<String>());
    }

    private static SubtitleExportController controller(SubtitleExportController.SnapshotProvider provider,
                                                      RecordingWriter writer, HoldingExecutor executor,
                                                      List<Runnable> posted,
                                                      List<SubtitleExportController.ExportResult> results) {
        return new SubtitleExportController(provider, SubtitleDiagnosticReport.Environment::unknown, writer,
                posted::add, () -> FIXED_TIME, executor);
    }

    private static List<String> zipEntryNames(byte[] bytes) throws IOException {
        List<String> names = new ArrayList<>();
        ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes));
        ZipEntry entry;

        while ((entry = zip.getNextEntry()) != null) {
            names.add(entry.getName());
        }

        zip.close();

        return names;
    }

    private static String readEntry(byte[] bytes, String wanted) throws IOException {
        ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes));
        ZipEntry entry;

        while ((entry = zip.getNextEntry()) != null) {
            if (wanted.equals(entry.getName())) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[4_096];
                int read;

                while ((read = zip.read(chunk)) > 0) {
                    buffer.write(chunk, 0, read);
                }

                zip.close();

                return new String(buffer.toByteArray(), UTF_8);
            }
        }

        zip.close();

        return null;
    }

    @Test
    public void exportsTheSubtitlesOfTheSnapshotTakenAtClickTime() throws IOException {
        HoldingExecutor executor = new HoldingExecutor();
        RecordingWriter writer = new RecordingWriter();
        List<Runnable> posted = new ArrayList<>();
        List<SubtitleExportController.ExportResult> results = new ArrayList<>();
        SubtitleExportController controller = controller(
                () -> snapshot(timeline("Hello"), emptyTranslations(), true, true), writer, executor, posted, results);

        assertTrue(controller.exportSubtitles(results::add));
        assertTrue(controller.isBusy());
        assertEquals("nothing runs on the UI thread", 0, writer.mBaseNames.size());

        executor.runAll();

        assertFalse(controller.isBusy());
        assertEquals(1, writer.mBaseNames.size());
        assertTrue(writer.mBaseNames.get(0).matches("SmartTube-subtitles-\\d{8}-\\d{6}"));
        assertEquals("zip", writer.mExtensions.get(0));
        assertTrue(zipEntryNames(writer.mPayloads.get(0)).contains(SubtitleExportBundle.FILE_ORIGINAL));

        assertEquals(0, results.size());
        posted.get(0).run();
        assertEquals(1, results.size());
        assertTrue(results.get(0).isSuccess());
        assertEquals(SubtitleExportController.Kind.SUBTITLES, results.get(0).getKind());
        assertTrue(results.get(0).getLocation().endsWith(".zip"));
        assertEquals(1, results.get(0).getCoverage().getItems());
    }

    @Test
    public void diagnosticsNeedNoTimelineNoKeyAndNoAiSwitch() throws IOException {
        HoldingExecutor executor = new HoldingExecutor();
        RecordingWriter writer = new RecordingWriter();
        List<Runnable> posted = new ArrayList<>();
        List<SubtitleExportController.ExportResult> results = new ArrayList<>();
        SubtitleExportController controller = controller(
                () -> snapshot(null, null, false, false), writer, executor, posted, results);

        assertTrue(controller.exportDiagnostics(results::add));
        executor.runAll();
        posted.get(0).run();

        assertEquals("txt", writer.mExtensions.get(0));
        assertTrue(writer.mBaseNames.get(0).startsWith("SmartTube-diagnostics-"));
        assertTrue(results.get(0).isSuccess());
        assertTrue(new String(writer.mPayloads.get(0), UTF_8).contains("timelineFrames=0"));
        assertTrue(new String(writer.mPayloads.get(0), UTF_8).contains("aiEnabled=false"));
        assertNull("the report has no subtitle coverage", results.get(0).getCoverage());
    }

    @Test
    public void aSecondPressWhileBusyIsRefusedInsteadOfQueued() {
        HoldingExecutor executor = new HoldingExecutor();
        RecordingWriter writer = new RecordingWriter();
        List<Runnable> posted = new ArrayList<>();
        SubtitleExportController controller = controller(
                () -> snapshot(timeline("Hello"), emptyTranslations(), true, true), writer, executor, posted,
                new ArrayList<>());

        assertTrue(controller.exportSubtitles(null));
        assertFalse(controller.exportSubtitles(null));
        assertFalse(controller.exportDiagnostics(null));
        assertEquals(1, executor.size());

        executor.runAll();

        assertEquals("the refused presses never reached the file writer", 1, writer.mBaseNames.size());
    }

    @Test
    public void aFailedWriteIsReportedByItsStableCodeAndReleasesTheButton() {
        HoldingExecutor executor = new HoldingExecutor();
        RecordingWriter writer = new RecordingWriter();
        writer.mStatus = SubtitleExportWriteOutcome.Status.PERMISSION_DENIED;
        List<Runnable> posted = new ArrayList<>();
        List<SubtitleExportController.ExportResult> results = new ArrayList<>();
        SubtitleExportController controller = controller(
                () -> snapshot(timeline("Hello"), emptyTranslations(), true, true), writer, executor, posted, results);

        assertTrue(controller.exportSubtitles(results::add));
        executor.runAll();
        posted.get(0).run();

        assertFalse(results.get(0).isSuccess());
        assertEquals("PERMISSION_DENIED", results.get(0).getFailureCode());
        assertFalse(controller.isBusy());

        writer.mStatus = SubtitleExportWriteOutcome.Status.OK;
        assertTrue("a later press works again", controller.exportSubtitles(null));
    }

    @Test
    public void theSnapshotIsFixedBeforeTheBackgroundWorkStarts() throws IOException {
        HoldingExecutor executor = new HoldingExecutor();
        RecordingWriter writer = new RecordingWriter();
        List<Runnable> posted = new ArrayList<>();
        final SubtitleExportSnapshot[] current = {snapshot(timeline("Old video"), emptyTranslations(), true, true)};

        SubtitleExportController controller = controller(() -> current[0], writer, executor, posted,
                new ArrayList<>());

        assertTrue(controller.exportSubtitles(null));
        // A video or track change while the export runs must not leak into the archive.
        current[0] = snapshot(timeline("New video"), emptyTranslations(), true, true);

        executor.runAll();

        String original = readEntry(writer.mPayloads.get(0), SubtitleExportBundle.FILE_ORIGINAL);
        assertTrue(original.contains("Old video"));
        assertFalse(original.contains("New video"));
    }

    @Test
    public void aThrowingWriterBecomesAStableFailureAndReleasesTheButton() {
        HoldingExecutor executor = new HoldingExecutor();
        RecordingWriter writer = new RecordingWriter();
        writer.mFailure = new IllegalStateException("boom");
        List<Runnable> posted = new ArrayList<>();
        List<SubtitleExportController.ExportResult> results = new ArrayList<>();
        SubtitleExportController controller = controller(
                () -> snapshot(timeline("Hello"), emptyTranslations(), true, true), writer, executor, posted, results);

        assertTrue(controller.exportSubtitles(results::add));
        executor.runAll();
        posted.get(0).run();

        assertEquals("WRITE_FAILED", results.get(0).getFailureCode());
        assertFalse(controller.isBusy());
    }

    @Test
    public void noTimelineRefusesWithItsOwnCodeAndNeverTouchesTheDisk() {
        HoldingExecutor executor = new HoldingExecutor();
        RecordingWriter writer = new RecordingWriter();
        List<Runnable> posted = new ArrayList<>();
        List<SubtitleExportController.ExportResult> results = new ArrayList<>();
        SubtitleExportController controller = controller(
                () -> snapshot(null, null, false, false), writer, executor, posted, results);

        assertTrue(controller.exportSubtitles(results::add));
        executor.runAll();
        posted.get(0).run();

        assertFalse(results.get(0).isSuccess());
        assertEquals(SubtitleExportController.CODE_NO_TIMELINE, results.get(0).getFailureCode());
        assertEquals("a refused export writes no pseudo-success file", 0, writer.mBaseNames.size());
    }

    @Test
    public void aFailingSnapshotProviderRefusesWithoutLeavingTheButtonStuck() {
        HoldingExecutor executor = new HoldingExecutor();
        RecordingWriter writer = new RecordingWriter();
        SubtitleExportController controller = controller(() -> {
            throw new IllegalStateException("no player");
        }, writer, executor, new ArrayList<>(), new ArrayList<>());

        assertFalse(controller.exportSubtitles(null));
        assertFalse(controller.isBusy());
        assertEquals(0, executor.size());
    }

    @Test
    public void theFileStampComesFromTheClickTimeClock() {
        HoldingExecutor executor = new HoldingExecutor();
        RecordingWriter writer = new RecordingWriter();
        SubtitleExportController controller = controller(
                () -> snapshot(timeline("Hello"), emptyTranslations(), true, true), writer, executor,
                new ArrayList<>(), new ArrayList<>());

        controller.exportSubtitles(null);
        executor.runAll();
        controller.exportSubtitles(null);
        executor.runAll();

        assertEquals(writer.mBaseNames.get(0), writer.mBaseNames.get(1));
    }

    private static Map<String, String> emptyTranslations() {
        return new LinkedHashMap<>();
    }
}
