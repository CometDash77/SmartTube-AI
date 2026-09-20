package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import androidx.annotation.RequiresApi;

import com.liskovsoft.sharedutils.mylogger.Log;

import java.security.KeyStore;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Android keystore implementation of the device key seam (plan section 7, API 23+).
 *
 * <p>It keeps one AES key inside the platform keystore under a stable alias, so the key material
 * never leaves secure hardware and cannot be exported with the application data. Any failure is
 * reported as null, which makes the caller fall back to the memory-only store.
 *
 * <p>Platform-bound: this class is compile-verified in this workspace, but a real keystore can only
 * be exercised with an instrumented test on a device - see T08/T11 of the plan.
 */
@RequiresApi(VERSION_CODES.M)
public class AndroidKeyStoreAccess implements SubtitleKeySource.KeyStoreAccess {
    private static final String TAG = AndroidKeyStoreAccess.class.getSimpleName();
    private static final String PROVIDER = "AndroidKeyStore";
    private static final int KEY_SIZE_BITS = 256;

    @Override
    public SecretKey getOrCreate(String alias) {
        if (alias == null || alias.isEmpty()) {
            return null;
        }

        try {
            KeyStore keyStore = KeyStore.getInstance(PROVIDER);
            keyStore.load(null);

            java.security.Key existing = keyStore.getKey(alias, null);

            if (existing instanceof SecretKey) {
                return (SecretKey) existing;
            }

            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER);
            generator.init(new KeyGenParameterSpec.Builder(alias,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE_BITS)
                    .build());

            return generator.generateKey();
        } catch (Exception e) {
            Log.e(TAG, "Keystore key unavailable: " + e.getClass().getSimpleName());

            return null;
        }
    }

    @Override
    public void delete(String alias) {
        if (alias == null || alias.isEmpty()) {
            return;
        }

        try {
            KeyStore keyStore = KeyStore.getInstance(PROVIDER);
            keyStore.load(null);

            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias);
            }
        } catch (Exception e) {
            Log.e(TAG, "Keystore entry could not be deleted: " + e.getClass().getSimpleName());
        }
    }

    /** True when the platform can hold an encryption key at all. */
    public static boolean isSupported() {
        return VERSION.SDK_INT >= VERSION_CODES.M;
    }

    /** Unused placeholder that keeps the AES algorithm explicit for readers of the stored format. */
    static SecretKeySpec algorithmDescription() {
        return new SecretKeySpec(new byte[KEY_SIZE_BITS / 8], KeyProperties.KEY_ALGORITHM_AES);
    }
}
