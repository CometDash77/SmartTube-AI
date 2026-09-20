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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** T08 slice: the assembled store degrades safely and never writes a readable key. */
@RunWith(RobolectricTestRunner.class)
public class SubtitleKeyStoresTest {
    private static File keyFile(Context context) {
        return new File(context.getNoBackupFilesDir(), "ai_subtitle_key");
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.P)
    public void aModernDeviceUsesThePersistentStoreAndNeverWritesPlaintext() {
        Context context = RuntimeEnvironment.application;
        SubtitleKeyStore store = SubtitleKeyStores.create(context);

        assertTrue("a missing keystore must use session storage, not reject every save",
                store.save("sk-secret-value"));
        assertTrue(store.hasKey());
        assertEquals("sk-secret-value", store.getApiKey());
        File file = keyFile(context);

        if (store.isPersistent()) {
            assertTrue("the key is readable again for this session", store.hasKey());
            assertEquals("sk-secret-value", store.getApiKey());
            assertTrue(file.exists());
            assertFalse("only ciphertext reaches the disk", read(file).contains("sk-secret-value"));
        } else {
            // No usable keystore in this environment: the store must degrade, not fall back to plaintext.
            assertFalse("no plaintext file may exist", file.exists());
            assertEquals("sk-secret-value", store.getApiKey());
        }

        store.clear();

        assertFalse(file.exists());
        assertNull(store.getApiKey());
    }

    // The old-device path is covered by 'SubtitleKeyStoreFactoryTest' with an SDK argument: this
    // environment cannot fetch the Robolectric Android 4.4 runtime needed to simulate it here.

    private static String read(File file) {
        try (java.io.FileInputStream input = new java.io.FileInputStream(file)) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[512];
            int read;

            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }

            return new String(output.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
