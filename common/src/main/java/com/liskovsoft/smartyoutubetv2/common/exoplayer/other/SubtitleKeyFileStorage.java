package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.content.Context;
import android.os.Build.VERSION;

import com.liskovsoft.sharedutils.mylogger.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Stores the encrypted key in the application's no-backup directory (plan section 7).
 *
 * <p>The directory is excluded from Auto Backup and device transfer, so a restored copy cannot carry
 * the secret to another device. Writes are atomic: the text goes to a temporary sibling first and is
 * then renamed over the target, so an interrupted write cannot leave a half envelope behind.
 *
 * <p>Only ciphertext is written here. The plaintext key never reaches this class.
 */
public class SubtitleKeyFileStorage implements PersistentSubtitleKeyStore.Storage {
    /** API 1 charset: java.nio.charset.StandardCharsets is API 19 while this module supports 17. */
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String TAG = SubtitleKeyFileStorage.class.getSimpleName();
    private static final String FILE_NAME = "ai_subtitle_key";

    private final File mFile;
    private final File mTemporaryFile;

    public SubtitleKeyFileStorage(File directory) {
        mFile = new File(directory, FILE_NAME);
        mTemporaryFile = new File(directory, FILE_NAME + ".tmp");
    }

    /**
     * @return the storage, or null below API 21 where the no-backup directory does not exist (those
     * devices use the memory store instead)
     */
    public static SubtitleKeyFileStorage create(Context context) {
        if (context == null || VERSION.SDK_INT < 21) {
            return null;
        }

        return new SubtitleKeyFileStorage(context.getNoBackupFilesDir());
    }

    /** Visible for the exclusion checks of section 7: the exact stored file. */
    public File getFile() {
        return mFile;
    }

    @Override
    public String read() {
        if (!mFile.isFile()) {
            return null;
        }

        try (FileInputStream input = new FileInputStream(mFile)) {
            // java.nio.file is API 26+, so the stream API is used to stay compatible with API 23.
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1_024];
            int read;

            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }

            return new String(output.toByteArray(), UTF_8);
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "Stored key could not be read: " + e.getClass().getSimpleName());

            return null;
        }
    }

    @Override
    public void write(String value) {
        if (value == null) {
            delete();

            return;
        }

        try {
            File parent = mFile.getParentFile();

            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return;
            }

            try (FileOutputStream output = new FileOutputStream(mTemporaryFile)) {
                output.write(value.getBytes(UTF_8));
                output.flush();
                output.getFD().sync();
            }

            if (!mTemporaryFile.renameTo(mFile)) {
                // Replace explicitly when the rename cannot overwrite (java.io only: API 23 safe).
                //noinspection ResultOfMethodCallIgnored
                mFile.delete();

                if (!mTemporaryFile.renameTo(mFile)) {
                    Log.e(TAG, "Stored key could not be replaced atomically");
                }
            }
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "Stored key could not be written: " + e.getClass().getSimpleName());
        } finally {
            if (mTemporaryFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                mTemporaryFile.delete();
            }
        }
    }

    @Override
    public void delete() {
        //noinspection ResultOfMethodCallIgnored
        mFile.delete();
        //noinspection ResultOfMethodCallIgnored
        mTemporaryFile.delete();
    }
}
