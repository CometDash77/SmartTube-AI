package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

/**
 * Result of writing one export file into the public export directory (plan section 14, T13).
 *
 * <p>A private or app-only location is never reported as success: when the public directory cannot be
 * used, the outcome carries a stable failure code instead of a path the user cannot reach with a file
 * manager.
 */
public final class SubtitleExportWriteOutcome {
    /** Stable failure codes; they are printable and contain no user data. */
    public enum Status {
        OK,
        /** No usable public directory (no context, storage not mounted). */
        NO_LOCATION,
        /** Legacy Android needs the storage permission before anything can be written. */
        PERMISSION_DENIED,
        /** The target filesystem reported too little free space. */
        NO_SPACE,
        /** Every candidate name already exists; an existing export is never overwritten. */
        FILE_EXISTS,
        /** The write itself failed (I/O error, short file). */
        WRITE_FAILED
    }

    private final Status mStatus;
    private final String mFileName;
    private final String mLocation;
    private final long mBytes;

    public SubtitleExportWriteOutcome(Status status, String fileName, String location, long bytes) {
        mStatus = status != null ? status : Status.WRITE_FAILED;
        mFileName = fileName;
        mLocation = location;
        mBytes = bytes;
    }

    public static SubtitleExportWriteOutcome failure(Status status, String fileName, String location) {
        return new SubtitleExportWriteOutcome(status, fileName, location, 0);
    }

    public boolean isSuccess() {
        return mStatus == Status.OK && mFileName != null;
    }

    public Status getStatus() {
        return mStatus;
    }

    /** Stable code for the UI and for the diagnostic event log. */
    public String getFailureCode() {
        return mStatus.name();
    }

    /** Actual stored file name; null when nothing was written. */
    public String getFileName() {
        return mFileName;
    }

    /** Absolute path the user can navigate to with a file manager; null when nothing was written. */
    public String getLocation() {
        return mLocation;
    }

    public long getBytes() {
        return mBytes;
    }

    @Override
    public String toString() {
        return "WriteOutcome{" + mStatus + ", name=" + mFileName + ", bytes=" + mBytes + "}";
    }
}
