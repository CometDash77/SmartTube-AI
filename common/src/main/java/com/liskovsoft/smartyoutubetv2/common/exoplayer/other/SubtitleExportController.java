package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs one local export per request, off the playback thread, and reports the real stored file
 * (plan section 14, T13).
 *
 * <p>Rules the class owns:
 * <ul>
 *     <li>The session snapshot is taken <em>before</em> the background work starts, so a video or
 *     track change during the export cannot mix new content into the finished file.</li>
 *     <li>A second press while an export is running is refused instead of queued: the menu can tell
 *     the user that an export is already running.</li>
 *     <li>Only the actual write result counts as success; a refused or short write is a failure with
 *     a stable code.</li>
 *     <li>The callback runs through the UI poster, so a dialog is only ever touched on the UI
 *     thread.</li>
 * </ul>
 *
 * <p>Every dependency is a seam, so the orchestration is unit-testable without Android.
 */
public class SubtitleExportController {
    public enum Kind {
        SUBTITLES,
        DIAGNOSTICS
    }

    /** Supplies the click-time session snapshot; called on the caller's (UI) thread. */
    public interface SnapshotProvider {
        SubtitleExportSnapshot snapshot();
    }

    /** Supplies the platform values of the diagnostic report. */
    public interface EnvironmentProvider {
        SubtitleDiagnosticReport.Environment environment();
    }

    /** Writes one finished file into the public export directory. */
    public interface FileWriter {
        SubtitleExportWriteOutcome write(String baseName, String extension, byte[] bytes);
    }

    /** Delivers the result back to the UI thread. */
    public interface UiPoster {
        void post(Runnable task);
    }

    public interface Listener {
        void onExportFinished(ExportResult result);
    }

    public interface Clock {
        long currentTimeMillis();
    }

    /** Stable codes the menu maps onto a message; they never contain user data. */
    public static final String CODE_UNAVAILABLE = "EXPORT_UNAVAILABLE";
    public static final String CODE_NO_TIMELINE = "NO_TIMELINE";
    public static final String CODE_ENCODING_FAILED = "ENCODING_FAILED";
    public static final String CODE_FAILED = "EXPORT_FAILED";

    public static final class ExportResult {
        private final Kind mKind;
        private final boolean mSuccess;
        private final String mFileName;
        private final String mLocation;
        private final long mBytes;
        private final String mFailureCode;
        private final SubtitleSrtFormatter.Coverage mCoverage;

        public ExportResult(Kind kind, boolean success, String fileName, String location, long bytes,
                            String failureCode, SubtitleSrtFormatter.Coverage coverage) {
            mKind = kind;
            mSuccess = success;
            mFileName = fileName;
            mLocation = location;
            mBytes = bytes;
            mFailureCode = failureCode;
            mCoverage = coverage;
        }

        static ExportResult success(Kind kind, String fileName, String location, long bytes,
                                    SubtitleSrtFormatter.Coverage coverage) {
            return new ExportResult(kind, true, fileName, location, bytes, null, coverage);
        }

        static ExportResult failure(Kind kind, String code) {
            return new ExportResult(kind, false, null, null, 0, code, null);
        }

        public Kind getKind() {
            return mKind;
        }

        public boolean isSuccess() {
            return mSuccess;
        }

        public String getFileName() {
            return mFileName;
        }

        public String getLocation() {
            return mLocation;
        }

        public long getBytes() {
            return mBytes;
        }

        /** Null when the export succeeded. */
        public String getFailureCode() {
            return mFailureCode;
        }

        /** Coverage of a subtitle export; null for the diagnostic report. */
        public SubtitleSrtFormatter.Coverage getCoverage() {
            return mCoverage;
        }

        @Override
        public String toString() {
            return "ExportResult{" + mKind + ", success=" + mSuccess + ", name=" + mFileName
                    + ", code=" + mFailureCode + "}";
        }
    }

    private final SnapshotProvider mSnapshotProvider;
    private final EnvironmentProvider mEnvironmentProvider;
    private final FileWriter mFileWriter;
    private final UiPoster mUiPoster;
    private final Clock mClock;
    private final Executor mExecutor;
    private final AtomicBoolean mBusy = new AtomicBoolean();

    public SubtitleExportController(SnapshotProvider snapshotProvider,
                                    EnvironmentProvider environmentProvider,
                                    FileWriter fileWriter, UiPoster uiPoster, Clock clock,
                                    Executor executor) {
        mSnapshotProvider = snapshotProvider;
        mEnvironmentProvider = environmentProvider;
        mFileWriter = fileWriter;
        mUiPoster = uiPoster;
        mClock = clock;
        mExecutor = executor;
    }

    public boolean isBusy() {
        return mBusy.get();
    }

    public boolean exportSubtitles(Listener listener) {
        return start(Kind.SUBTITLES, listener);
    }

    public boolean exportDiagnostics(Listener listener) {
        return start(Kind.DIAGNOSTICS, listener);
    }

    /** File stamp of one export; local time, so the user recognises when it was taken. */
    static String fileStamp(long millis) {
        return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date(millis));
    }

    private boolean start(final Kind kind, final Listener listener) {
        if (mExecutor == null || !mBusy.compareAndSet(false, true)) {
            return false;
        }

        final SubtitleExportSnapshot snapshot;
        final long timestamp;

        try {
            // The snapshot must exist before the work leaves this thread.
            snapshot = mSnapshotProvider != null ? mSnapshotProvider.snapshot() : null;
            timestamp = mClock != null ? mClock.currentTimeMillis() : System.currentTimeMillis();
        } catch (RuntimeException e) {
            mBusy.set(false); // a failing snapshot must never leave the button stuck

            return false;
        }

        try {
            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    ExportResult result;

                    try {
                        result = build(kind, snapshot, timestamp);
                    } catch (RuntimeException e) {
                        result = ExportResult.failure(kind, CODE_FAILED);
                    }

                    mBusy.set(false);
                    post(listener, result);
                }
            });
        } catch (RejectedExecutionException e) {
            mBusy.set(false);

            return false;
        }

        return true;
    }

    private ExportResult build(Kind kind, SubtitleExportSnapshot snapshot, long timestamp) {
        if (kind == Kind.DIAGNOSTICS) {
            SubtitleDiagnosticReport.Environment environment =
                    mEnvironmentProvider != null ? mEnvironmentProvider.environment() : null;
            byte[] bytes = SubtitleDiagnosticReport.buildUtf8(snapshot, environment);

            return write(kind, SubtitleDiagnosticReport.FILE_NAME_PREFIX,
                    SubtitleDiagnosticReport.FILE_EXTENSION, bytes, null, timestamp);
        }

        SubtitleExportBundle.Result bundle = SubtitleExportBundle.build(snapshot);

        if (!bundle.isSuccess()) {
            return ExportResult.failure(kind, bundle.getFailure() == SubtitleExportBundle.Failure.NO_TIMELINE
                    ? CODE_NO_TIMELINE
                    : CODE_ENCODING_FAILED);
        }

        return write(kind, SubtitleExportBundle.FILE_NAME_PREFIX, SubtitleExportBundle.FILE_EXTENSION,
                bundle.getBytes(), bundle.getCoverage(), timestamp);
    }

    private ExportResult write(Kind kind, String prefix, String extension, byte[] bytes,
                               SubtitleSrtFormatter.Coverage coverage, long timestamp) {
        if (mFileWriter == null) {
            return ExportResult.failure(kind, SubtitleExportWriteOutcome.Status.NO_LOCATION.name());
        }

        SubtitleExportWriteOutcome outcome;

        try {
            outcome = mFileWriter.write(prefix + fileStamp(timestamp), extension, bytes);
        } catch (RuntimeException e) {
            outcome = null;
        }

        if (outcome == null || !outcome.isSuccess()) {
            return ExportResult.failure(kind, outcome != null
                    ? outcome.getFailureCode()
                    : SubtitleExportWriteOutcome.Status.WRITE_FAILED.name());
        }

        return ExportResult.success(kind, outcome.getFileName(), outcome.getLocation(), outcome.getBytes(), coverage);
    }

    private void post(final Listener listener, final ExportResult result) {
        if (listener == null) {
            return;
        }

        if (mUiPoster == null) {
            listener.onExportFinished(result);

            return;
        }

        mUiPoster.post(new Runnable() {
            @Override
            public void run() {
                listener.onExportFinished(result);
            }
        });
    }
}
