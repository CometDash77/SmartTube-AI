package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build.VERSION;
import android.os.Environment;

import com.liskovsoft.sharedutils.helpers.PermissionHelpers;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaStoreFile;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Writes one finished export into a public directory a TV file manager can browse and copy from
 * (plan section 14, T13).
 *
 * <p>Preferred location on Android 10+: {@code Documents/SmartTube/Exports/} through the existing
 * {@link MediaStoreFile} wrapper, which is the same storage path the application backup already uses
 * and needs no storage permission. On older Android the same directory is written directly and the
 * project's existing storage permission check applies: when the permission was never granted the
 * export fails with {@link SubtitleExportWriteOutcome.Status#PERMISSION_DENIED} instead of silently
 * dropping the file into a private app directory.
 *
 * <p>An existing file is never overwritten: the name carries a timestamp and, on the rare collision,
 * a numeric suffix. A write only counts as success after the stored file was read back completely,
 * so a truncated or unreadable export is reported as a failure instead of a takeable file.
 */
public class SubtitleExportFileStore {
    public static final String ROOT_DIR_NAME = "SmartTube";
    public static final String EXPORT_DIR_NAME = "Exports";
    /** Environment.DIRECTORY_DOCUMENTS is API 19; the literal keeps the API 17 path working. */
    private static final String DOCUMENTS_DIR = "Documents";
    private static final int MAX_NAME_ATTEMPTS = 20;
    private static final long SPACE_MARGIN_BYTES = 64L * 1024L;
    private static final int VERIFY_CHUNK_BYTES = 8 * 1024;

    private final Context mContext;

    public SubtitleExportFileStore(Context context) {
        mContext = context;
    }

    /**
     * Writes the bytes under the public export directory and reads the file back before reporting
     * success, so an unreadable or truncated export is never presented as a file the user can take.
     */
    public SubtitleExportWriteOutcome write(String baseName, String extension, byte[] bytes) {
        if (mContext == null || baseName == null || extension == null || bytes == null || bytes.length == 0) {
            return SubtitleExportWriteOutcome.failure(SubtitleExportWriteOutcome.Status.NO_LOCATION, null, null);
        }

        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            return SubtitleExportWriteOutcome.failure(SubtitleExportWriteOutcome.Status.NO_LOCATION, null, null);
        }

        if (!hasFreeSpace(bytes.length)) {
            return SubtitleExportWriteOutcome.failure(SubtitleExportWriteOutcome.Status.NO_SPACE, null, null);
        }

        return VERSION.SDK_INT >= 29
                ? writeThroughMediaStore(baseName, extension, bytes)
                : writeThroughPublicDirectory(baseName, extension, bytes);
    }

    @TargetApi(29)
    private SubtitleExportWriteOutcome writeThroughMediaStore(String baseName, String extension, byte[] bytes) {
        for (int attempt = 0; attempt < MAX_NAME_ATTEMPTS; attempt++) {
            String fileName = fileName(baseName, extension, attempt);
            MediaStoreFile file = new MediaStoreFile(mContext, EXPORT_DIR_NAME + "/" + fileName,
                    ROOT_DIR_NAME, DOCUMENTS_DIR);

            if (file.exists()) {
                continue; // an existing export is never overwritten
            }

            if (!file.isWritable()) {
                continue; // MediaStore renamed it (stale entry): pick another name
            }

            OutputStream out = file.openOutputStream();

            if (out == null) {
                continue;
            }

            try {
                out.write(bytes);
                out.flush();
            } catch (IOException e) {
                return failure(e, fileName, null);
            } finally {
                closeQuietly(out);
            }

            if (!verifyStoredBytes(file.openInputStream(), bytes.length)) {
                file.delete(); // a file that cannot be read back completely is not deliverable
                return SubtitleExportWriteOutcome.failure(SubtitleExportWriteOutcome.Status.WRITE_FAILED, fileName, null);
            }

            return new SubtitleExportWriteOutcome(SubtitleExportWriteOutcome.Status.OK, fileName,
                    file.resolve(), bytes.length);
        }

        return SubtitleExportWriteOutcome.failure(SubtitleExportWriteOutcome.Status.FILE_EXISTS, null, null);
    }

    private SubtitleExportWriteOutcome writeThroughPublicDirectory(String baseName, String extension, byte[] bytes) {
        if (!PermissionHelpers.hasStoragePermissions(mContext)) {
            return SubtitleExportWriteOutcome.failure(SubtitleExportWriteOutcome.Status.PERMISSION_DENIED, null, null);
        }

        File dir = new File(Environment.getExternalStoragePublicDirectory(DOCUMENTS_DIR),
                ROOT_DIR_NAME + "/" + EXPORT_DIR_NAME);

        if (!dir.exists() && !dir.mkdirs()) {
            return SubtitleExportWriteOutcome.failure(SubtitleExportWriteOutcome.Status.NO_LOCATION, null, null);
        }

        for (int attempt = 0; attempt < MAX_NAME_ATTEMPTS; attempt++) {
            String fileName = fileName(baseName, extension, attempt);
            File target = new File(dir, fileName);

            if (target.exists()) {
                continue;
            }

            FileOutputStream out = null;

            try {
                out = new FileOutputStream(target);
                out.write(bytes);
                out.flush();
            } catch (IOException e) {
                return failure(e, fileName, target.getAbsolutePath());
            } finally {
                closeQuietly(out);
            }

            InputStream readBack = null;
            boolean verified;

            try {
                readBack = new FileInputStream(target);
                verified = verifyStoredBytes(readBack, bytes.length);
            } catch (IOException e) {
                verified = false;
            } finally {
                closeQuietly(readBack);
            }

            if (!verified) {
                target.delete(); // a file that cannot be read back completely is not deliverable
                return SubtitleExportWriteOutcome.failure(SubtitleExportWriteOutcome.Status.WRITE_FAILED, fileName, null);
            }

            return new SubtitleExportWriteOutcome(SubtitleExportWriteOutcome.Status.OK, fileName,
                    target.getAbsolutePath(), bytes.length);
        }

        return SubtitleExportWriteOutcome.failure(SubtitleExportWriteOutcome.Status.FILE_EXISTS, null, null);
    }

    private static SubtitleExportWriteOutcome failure(IOException e, String fileName, String location) {
        return SubtitleExportWriteOutcome.failure(noSpace(e)
                ? SubtitleExportWriteOutcome.Status.NO_SPACE
                : SubtitleExportWriteOutcome.Status.WRITE_FAILED, fileName, location);
    }

    private static boolean noSpace(IOException e) {
        String message = e != null ? e.getMessage() : null;

        return message != null && message.contains("ENOSPC");
    }

    private static boolean hasFreeSpace(long neededBytes) {
        File root = Environment.getExternalStorageDirectory();

        if (root == null) {
            return true; // cannot measure: let the write itself decide
        }

        long usable = root.getUsableSpace();

        return usable <= 0 || usable > neededBytes + SPACE_MARGIN_BYTES;
    }

    /** The timestamp already makes a collision unlikely; the suffix keeps it impossible to overwrite. */
    private static String fileName(String baseName, String extension, int attempt) {
        String suffix = attempt == 0 ? "" : "-" + attempt;

        return baseName + suffix + "." + extension;
    }

    /**
     * Counts the bytes of the stored file. Content is verified instead of a metadata size column
     * because only a file that can be opened and read to its end is a file the user can copy away.
     * The caller owns and closes the stream.
     */
    private static boolean verifyStoredBytes(InputStream in, int expectedBytes) {
        if (in == null) {
            return false;
        }

        byte[] chunk = new byte[VERIFY_CHUNK_BYTES];
        long total = 0;

        try {
            int read;

            while ((read = in.read(chunk)) != -1) {
                total += read;

                if (total > expectedBytes) {
                    return false; // longer than what was written: not our content
                }
            }
        } catch (IOException e) {
            return false;
        }

        return total == expectedBytes;
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
                // The result already exists; a failing close changes nothing.
            }
        }
    }
}
