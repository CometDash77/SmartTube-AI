package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.content.Context;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;

/**
 * Production assembly of the key store (plan section 7).
 *
 * <p>On API 23+ it builds the encrypted device store: the Android keystore holds the encryption key
 * ({@link AndroidKeyStoreAccess} through {@link SubtitleKeySource}), {@link SubtitleKeyCipher} does
 * AES-GCM, and {@link SubtitleKeyFileStorage} writes the ciphertext into the no-backup directory.
 * Below API 23 - or whenever any of those pieces is unavailable - the memory-only store is used and
 * the UI reports the session-only rule through {@link SubtitleKeyStore#isPersistent()}.
 */
public final class SubtitleKeyStores {
    private SubtitleKeyStores() {
    }

    public static SubtitleKeyStore create(Context context) {
        return SubtitleKeyStoreFactory.create(VERSION.SDK_INT, () -> {
            SubtitleKeyFileStorage storage = SubtitleKeyFileStorage.create(context);

            if (storage == null) {
                return null;
            }

            if (VERSION.SDK_INT < VERSION_CODES.M) {
                return null; // the platform keystore key only exists from API 23
            }

            SubtitleKeySource keySource = new SubtitleKeySource(new AndroidKeyStoreAccess());

            if (keySource.getKey() == null) {
                return null; // construction alone does not prove that AndroidKeyStore is usable
            }

            return new PersistentSubtitleKeyStore(new SubtitleKeyCipher(keySource), storage);
        });
    }
}
