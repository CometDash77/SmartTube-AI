package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.content.Context;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** T08 slice: the encrypted key lives in the no-backup directory and is written atomically. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public class SubtitleKeyFileStorageTest {
    private static SubtitleKeyFileStorage storage(Context context) {
        return new SubtitleKeyFileStorage(context.getNoBackupFilesDir());
    }

    @Test
    public void theFileSitsInTheNoBackupDirectory() {
        Context context = RuntimeEnvironment.application;
        SubtitleKeyFileStorage storage = SubtitleKeyFileStorage.create(context);

        assertNotNull(storage);
        assertEquals(context.getNoBackupFilesDir(), storage.getFile().getParentFile());
        assertTrue("the path is the platform's no-backup location",
                storage.getFile().getAbsolutePath().contains("no_backup"));
    }

    @Test
    public void writingAndReadingRoundTrips() {
        SubtitleKeyFileStorage storage = storage(RuntimeEnvironment.application);

        assertNull("nothing stored yet", storage.read());

        storage.write("1:0102:0304");

        assertEquals("1:0102:0304", storage.read());
    }

    @Test
    public void anOverwriteReplacesThePreviousValueWithoutLeavingATemporaryFile() {
        Context context = RuntimeEnvironment.application;
        SubtitleKeyFileStorage storage = storage(context);

        storage.write("first");
        storage.write("second");

        assertEquals("second", storage.read());
        assertFalse(new File(context.getNoBackupFilesDir(), "ai_subtitle_key.tmp").exists());
    }

    @Test
    public void deletingRemovesTheStoredValue() {
        SubtitleKeyFileStorage storage = storage(RuntimeEnvironment.application);
        storage.write("value");

        storage.delete();

        assertNull(storage.read());
        assertFalse(storage.getFile().exists());
    }

    @Test
    public void writingNullDeletesTheValue() {
        SubtitleKeyFileStorage storage = storage(RuntimeEnvironment.application);
        storage.write("value");

        storage.write(null);

        assertNull(storage.read());
    }

    @Test
    public void aMissingDirectoryIsCreatedOnDemand() {
        Context context = RuntimeEnvironment.application;
        File directory = new File(context.getNoBackupFilesDir(), "nested/keys");
        SubtitleKeyFileStorage storage = new SubtitleKeyFileStorage(directory);

        storage.write("value");

        assertEquals("value", storage.read());
    }
}
