package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds the local subtitle archive of one export (plan section 14, T13).
 *
 * <p>The archive is produced from the obtained timeline and the translation state of this session; it
 * never translates and never starts a DeepSeek call. Every state a user can be in is exportable:
 * original only, translation only, bilingual, a bilingual run that was interrupted halfway, and items
 * whose translation failed. The archive says which state each cue is in instead of hiding it behind a
 * silent fallback.
 *
 * <p>Entries:
 * <ul>
 *     <li>{@link #FILE_ORIGINAL} — the whole obtained timeline.</li>
 *     <li>{@link #FILE_TRANSLATED} — translation text with the original as fallback, so no cue is blank.</li>
 *     <li>{@link #FILE_BILINGUAL} — original above translation; the original alone where none exists.</li>
 *     <li>{@link #FILE_UNTRANSLATED} — only the cues without a translation (the interrupted/failed part).</li>
 *     <li>{@link #FILE_STATUS} — per-cue status: TRANSLATED / FAILED / NOT_ATTEMPTED.</li>
 *     <li>{@link #FILE_README} — the coverage and state note.</li>
 * </ul>
 *
 * <p>When no usable timeline exists the builder refuses instead of writing a successful looking empty
 * archive, because the user has to be told that the timeline was not ready.
 */
public final class SubtitleExportBundle {
    public static final String FILE_ORIGINAL = "original.srt";
    public static final String FILE_TRANSLATED = "translated.srt";
    public static final String FILE_BILINGUAL = "bilingual.srt";
    public static final String FILE_UNTRANSLATED = "untranslated.srt";
    /** Rule-segmented sentences: they never replace the raw files, they are added next to them. */
    public static final String FILE_SEGMENTED_ORIGINAL = "segmented-original.srt";
    public static final String FILE_SEGMENTED_TRANSLATED = "segmented-translated.srt";
    public static final String FILE_SEGMENTED_BILINGUAL = "segmented-bilingual.srt";
    public static final String FILE_STATUS = "translation-status.txt";
    public static final String FILE_README = "README.txt";
    public static final String FILE_NAME_PREFIX = "SmartTube-subtitles-";
    public static final String FILE_EXTENSION = "zip";
    /** API 1 charset: java.nio.charset.StandardCharsets is API 19. */
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String EOL = "\r\n";
    private static final String LINE = "\n";
    private static final int MAX_NOTE_VALUE_LENGTH = 64;

    /** Local failure of the content build; maps to the stable UI codes of the controller. */
    public enum Failure {
        NONE,
        /** No timeline, an empty timeline or a timeline without a single timeline frame. */
        NO_TIMELINE,
        /** The archive could not be encoded (should stay unreachable for in-memory output). */
        ENCODING_FAILED
    }

    public static final class Result {
        private final Failure mFailure;
        private final byte[] mBytes;
        private final List<String> mEntries;
        private final SubtitleSrtFormatter.Coverage mCoverage;
        private final int mFailedItems;
        private final int mNotAttemptedItems;

        Result(Failure failure, byte[] bytes, List<String> entries, SubtitleSrtFormatter.Coverage coverage,
               int failedItems, int notAttemptedItems) {
            mFailure = failure;
            mBytes = bytes;
            mEntries = entries != null
                    ? Collections.unmodifiableList(new ArrayList<>(entries))
                    : Collections.<String>emptyList();
            mCoverage = coverage;
            mFailedItems = failedItems;
            mNotAttemptedItems = notAttemptedItems;
        }

        public boolean isSuccess() {
            return mFailure == Failure.NONE && mBytes != null && mBytes.length > 0;
        }

        public Failure getFailure() {
            return mFailure;
        }

        /** Null unless the build succeeded. */
        public byte[] getBytes() {
            return mBytes;
        }

        public List<String> getEntries() {
            return mEntries;
        }

        /** Null when there was no timeline to measure. */
        public SubtitleSrtFormatter.Coverage getCoverage() {
            return mCoverage;
        }

        /** Items that were attempted at least once without a stored translation. */
        public int getFailedItems() {
            return mFailedItems;
        }

        /** Items the session never attempted (an interrupted or never started run). */
        public int getNotAttemptedItems() {
            return mNotAttemptedItems;
        }
    }

    private SubtitleExportBundle() {
    }

    public static Result build(SubtitleExportSnapshot snapshot) {
        SubtitleTimeline timeline = snapshot != null ? snapshot.getTimeline() : null;

        if (timeline == null || timeline.isEmpty()) {
            return failure(Failure.NO_TIMELINE, null, 0, 0);
        }

        Map<String, String> translations = snapshot.getTranslations();
        Map<String, String> status = snapshot.getTranslationStatus();
        SubtitleSrtFormatter.Coverage coverage = SubtitleSrtFormatter.measure(timeline, translations);

        if (coverage.getFrames() == 0) {
            return failure(Failure.NO_TIMELINE, coverage, 0, 0);
        }

        int failedItems = countStatus(timeline, status, SubtitleTranslationCache.STATUS_FAILED);
        int notAttemptedItems = coverage.getMissingItems() - failedItems;

        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            ZipOutputStream zip = new ZipOutputStream(buffer);
            List<String> entries = new ArrayList<>();
            long modifiedMs = snapshot.getCreatedAtMs();

            put(zip, FILE_ORIGINAL, SubtitleSrtFormatter.formatOriginal(timeline).getBytes(UTF_8), entries, modifiedMs);

            if (coverage.getTranslatedItems() > 0) {
                put(zip, FILE_TRANSLATED,
                        SubtitleSrtFormatter.formatTranslated(timeline, translations).getBytes(UTF_8), entries, modifiedMs);
                put(zip, FILE_BILINGUAL,
                        SubtitleSrtFormatter.formatBilingual(timeline, translations).getBytes(UTF_8), entries, modifiedMs);
            }

            if (coverage.getMissingItems() > 0) {
                put(zip, FILE_UNTRANSLATED,
                        SubtitleSrtFormatter.formatUntranslated(timeline, translations).getBytes(UTF_8), entries, modifiedMs);
            }

            SubtitleTimeline segmented = snapshot.getSegmentedTimeline();

            if (segmented != null && !segmented.isEmpty()) {
                // The derived sentences are an extra view of the same subtitles (plan 4.3.8): the raw
                // files above stay untouched and keep their own item ids.
                SubtitleSrtFormatter.Coverage segmentedCoverage = SubtitleSrtFormatter.measure(segmented, translations);
                put(zip, FILE_SEGMENTED_ORIGINAL,
                        SubtitleSrtFormatter.formatOriginal(segmented).getBytes(UTF_8), entries, modifiedMs);

                if (segmentedCoverage.getTranslatedItems() > 0) {
                    put(zip, FILE_SEGMENTED_TRANSLATED,
                            SubtitleSrtFormatter.formatTranslated(segmented, translations).getBytes(UTF_8),
                            entries, modifiedMs);
                    put(zip, FILE_SEGMENTED_BILINGUAL,
                            SubtitleSrtFormatter.formatBilingual(segmented, translations).getBytes(UTF_8),
                            entries, modifiedMs);
                }
            }

            put(zip, FILE_STATUS, statusNote(snapshot, coverage, failedItems, notAttemptedItems).getBytes(UTF_8),
                    entries, modifiedMs);
            put(zip, FILE_README, readme(snapshot, coverage, failedItems, notAttemptedItems).getBytes(UTF_8),
                    entries, modifiedMs);
            zip.finish();
            zip.close();

            byte[] bytes = buffer.toByteArray();

            if (bytes.length == 0) {
                return failure(Failure.ENCODING_FAILED, coverage, failedItems, notAttemptedItems);
            }

            return new Result(Failure.NONE, bytes, entries, coverage, failedItems, notAttemptedItems);
        } catch (IOException e) {
            return failure(Failure.ENCODING_FAILED, coverage, failedItems, notAttemptedItems);
        }
    }

    private static Result failure(Failure failure, SubtitleSrtFormatter.Coverage coverage, int failedItems,
                                  int notAttemptedItems) {
        return new Result(failure, null, Collections.<String>emptyList(), coverage, failedItems, notAttemptedItems);
    }

    /** Distinct timeline items whose recorded state equals the wanted one. */
    private static int countStatus(SubtitleTimeline timeline, Map<String, String> status, String wanted) {
        if (timeline == null || status == null || status.isEmpty()) {
            return 0;
        }

        java.util.LinkedHashSet<String> counted = new java.util.LinkedHashSet<>();

        for (SubtitleFrame frame : timeline.getFrames()) {
            if (frame == null) {
                continue;
            }

            for (SubtitleItem item : frame.getItems()) {
                if (item != null && wanted.equals(status.get(item.getItemId()))) {
                    counted.add(item.getItemId());
                }
            }
        }

        return counted.size();
    }

    private static void put(ZipOutputStream zip, String name, byte[] content, List<String> entries,
                            long modifiedMs) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(modifiedMs);
        zip.putNextEntry(entry);
        zip.write(content);
        zip.closeEntry();
        entries.add(name);
    }

    /**
     * Per-cue state file. It is the answer to "which subtitles are translated, which failed and which
     * were never reached", and it keeps working when the run was interrupted: such cues simply stay
     * {@code NOT_ATTEMPTED} instead of being presented as translated.
     */
    /** One line about the rule-segmented files, so a reader knows why they are (not) present. */
    static String segmentedNote(SubtitleExportSnapshot snapshot) {
        SubtitleTimeline segmented = snapshot != null ? snapshot.getSegmentedTimeline() : null;

        return segmented == null || segmented.isEmpty()
                ? "Rule segmentation was off at export time: no segmented-*.srt file is included."
                : "segmented-*.srt: the same subtitles merged into rule-based sentences ("
                + segmented.size() + " frames); the raw files keep the original cue boundaries.";
    }

    static String statusNote(SubtitleExportSnapshot snapshot, SubtitleSrtFormatter.Coverage coverage,
                             int failedItems, int notAttemptedItems) {
        SubtitleExportSnapshot.Session session = snapshot.getSession();
        Map<String, String> translations = snapshot.getTranslations();
        Map<String, String> status = snapshot.getTranslationStatus();
        StringBuilder out = new StringBuilder();

        out.append("# SmartTube translation status of one export").append(EOL);
        out.append("# status: ").append(SubtitleTranslationCache.STATUS_TRANSLATED)
                .append(" = a translation exists; ").append(SubtitleTranslationCache.STATUS_FAILED)
                .append(" = attempted without a result; NOT_ATTEMPTED = never reached (interrupted run)")
                .append(EOL);
        out.append("# index\tstart\tend\tstatus\toriginal").append(EOL);

        int index = 0;

        for (SubtitleFrame frame : snapshot.getTimeline().getFrames()) {
            List<SubtitleItem> items = frame != null ? frame.getItems() : Collections.<SubtitleItem>emptyList();

            if (items.isEmpty()) {
                continue;
            }

            for (SubtitleItem item : items) {
                if (item == null || SubtitleSrtFormatter.isBlank(item.getText())) {
                    continue;
                }

                index++;
                out.append(index).append('\t')
                        .append(SubtitleSrtFormatter.formatTimestamp(frame.getStartUs())).append('\t')
                        .append(SubtitleSrtFormatter.formatTimestamp(frame.getEndUs())).append('\t')
                        .append(stateOf(status, item.getItemId())).append('\t')
                        .append(oneLine(item.getText())).append(EOL);
            }
        }

        out.append(EOL);
        out.append("totalItems=").append(coverage.getItems()).append(EOL);
        out.append("translatedItems=").append(coverage.getTranslatedItems()).append(EOL);
        out.append("failedItems=").append(failedItems).append(EOL);
        out.append("notAttemptedItems=").append(Math.max(0, notAttemptedItems)).append(EOL);
        out.append("coveragePercent=").append(coverage.getCoveragePercent()).append(EOL);
        out.append("aiTranslationRunningAtExport=").append(session.isAiEnabled()).append(EOL);
        out.append("keyConfiguredAtExport=").append(session.isKeyConfigured()).append(EOL);
        out.append("targetLanguageAtExport=").append(value(session.getTargetLanguage())).append(EOL);
        out.append("subtitleSnapshotStatusAtExport=").append(value(session.getSnapshotStatus())).append(EOL);

        return out.toString();
    }

    private static String stateOf(Map<String, String> status, String itemId) {
        String state = status != null ? status.get(itemId) : null;

        return state != null ? state : "NOT_ATTEMPTED";
    }

    /**
     * The coverage note. It states what was exported, that missing translations fall back to the
     * original, and that the bounded cache may have dropped older results: it never claims that the
     * whole video was translated.
     */
    static String readme(SubtitleExportSnapshot snapshot, SubtitleSrtFormatter.Coverage coverage,
                         int failedItems, int notAttemptedItems) {
        SubtitleExportSnapshot.Source source = snapshot.getSource();
        SubtitleExportSnapshot.Session session = snapshot.getSession();
        StringBuilder out = new StringBuilder();

        out.append("SmartTube local subtitle export / SmartTube \u672c\u5730\u5b57\u5e55\u5bfc\u51fa").append(EOL);
        out.append("generatedAt=").append(date(snapshot.getCreatedAtMs())).append(EOL);
        out.append("sourceLanguage=").append(value(source.getLanguageCode())).append(EOL);
        out.append("sourceMime=").append(value(source.getMimeType())).append(EOL);
        out.append("targetLanguage=").append(value(session.getTargetLanguage())).append(EOL);
        out.append("frames=").append(coverage.getFrames()).append(EOL);
        out.append("items=").append(coverage.getItems()).append(EOL);
        out.append("translatedItems=").append(coverage.getTranslatedItems()).append(EOL);
        out.append("failedItems=").append(failedItems).append(EOL);
        out.append("notAttemptedItems=").append(Math.max(0, notAttemptedItems)).append(EOL);
        out.append("missingItems=").append(coverage.getMissingItems()).append(EOL);
        out.append("coveragePercent=").append(coverage.getCoveragePercent()).append(EOL);
        out.append(EOL);
        out.append("Missing translations fall back to the original text. / \u7f3a\u5931\u8bd1\u6587\u5904\u4f7f\u7528\u539f\u6587\u56de\u9000\u3002").append(EOL);
        out.append("Only data the app had already obtained is exported; this export translates nothing and calls no AI service. / \u672c\u5bfc\u51fa\u53ea\u4f7f\u7528\u5e94\u7528\u5df2\u53d6\u5f97\u7684\u6570\u636e\uff0c\u4e0d\u4f1a\u7ffb\u8bd1\u4efb\u4f55\u5185\u5bb9\uff0c\u4e5f\u4e0d\u4f1a\u8c03\u7528 AI \u670d\u52a1\u3002").append(EOL);
        out.append("The translation cache is bounded (").append(SubtitleTranslationCache.MAX_ENTRIES)
                .append(" entries / ").append(SubtitleTranslationCache.MAX_BYTES)
                .append(" bytes), so older translations may have been evicted. This is not a full-video translation. / \u8bd1\u6587\u7f13\u5b58\u6709\u5bb9\u91cf\u4e0a\u9650\uff08")
                .append(SubtitleTranslationCache.MAX_ENTRIES).append(" \u6761 / ")
                .append(SubtitleTranslationCache.MAX_BYTES)
                .append(" \u5b57\u8282\uff09\uff0c\u8f83\u65e9\u7684\u8bd1\u6587\u53ef\u80fd\u5df2\u88ab\u6dd8\u6c70\uff1b\u8fd9\u4e0d\u4ee3\u8868\u6574\u90e8\u89c6\u9891\u5df2\u7ffb\u8bd1\u3002").append(EOL);
        out.append("Timecodes reuse the original timeline; the last cue's end is an estimate because the source has no known end. / \u65f6\u95f4\u7801\u6cbf\u7528\u539f\u59cb\u65f6\u95f4\u8f74\uff1b\u6700\u540e\u4e00\u6761\u5b57\u5e55\u7684\u7ed3\u675f\u65f6\u95f4\u4e3a\u4f30\u7b97\u503c\uff0c\u56e0\u4e3a\u6765\u6e90\u6ca1\u6709\u5df2\u77e5\u7ed3\u675f\u65f6\u95f4\u3002").append(EOL);
        out.append(EOL);
        out.append("Files: ").append(FILE_ORIGINAL).append(" = original; ").append(FILE_TRANSLATED)
                .append(" = translation with original fallback; ").append(FILE_BILINGUAL)
                .append(" = original above translation; ").append(FILE_UNTRANSLATED)
                .append(" = only the cues still without a translation; ").append(FILE_STATUS)
                .append(" = per-cue state (TRANSLATED / FAILED / NOT_ATTEMPTED).").append(EOL);
        out.append(segmentedNote(snapshot)).append(EOL);
        out.append("This export does not need the AI switch or an API key, and works with an interrupted or failed translation run. / \u672c\u5bfc\u51fa\u4e0d\u9700\u8981 AI \u5f00\u5173\u6216 API Key\uff0c\u7ffb\u8bd1\u88ab\u4e2d\u65ad\u6216\u5931\u8d25\u65f6\u4e5f\u80fd\u5bfc\u51fa\u3002").append(EOL);

        if (coverage.getTranslatedItems() == 0) {
            out.append("No translation was available at export time. / \u5bfc\u51fa\u65f6\u6ca1\u6709\u53ef\u7528\u7684\u8bd1\u6587\u3002").append(EOL);
        }

        return out.toString();
    }

    private static String date(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(millis));
    }

    /** One line, no control characters, bounded: a note file must not become a payload dump. */
    private static String value(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "unknown";
        }

        StringBuilder out = new StringBuilder();

        for (int i = 0; i < raw.length() && out.length() < MAX_NOTE_VALUE_LENGTH; i++) {
            char c = raw.charAt(i);

            if (c >= ' ' && c != '\u007f') {
                out.append(c);
            }
        }

        return out.length() == 0 ? "unknown" : out.toString();
    }

    /** Keeps one subtitle line on one row of the status table. */
    private static String oneLine(String text) {
        return text == null ? "" : text.replace('\t', ' ').replace(LINE, " / ").replace(EOL, " / ");
    }
}
