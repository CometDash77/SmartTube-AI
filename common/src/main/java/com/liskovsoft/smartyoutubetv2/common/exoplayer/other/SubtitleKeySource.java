package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import javax.crypto.SecretKey;

/**
 * Supplies the device-held encryption key to {@link SubtitleKeyCipher}.
 *
 * <p>The platform call (an Android keystore entry on API 23+) sits behind {@link KeyStoreAccess}, so
 * the behaviour that matters for the plan is testable without a device: the key is fetched once and
 * cached for the session, an unavailable or failing keystore yields null instead of an exception, and
 * clearing the key also removes the stored entry so a "forget the key" action really forgets it.
 * {@link SubtitleKeyCipher} treats a null key as "cannot persist", which is exactly the documented
 * fallback to the memory-only store.
 */
public class SubtitleKeySource implements SubtitleKeyCipher.KeySource {
    /** Stable alias of the encryption key; changing it would orphan existing ciphertext. */
    public static final String KEY_ALIAS = "ai_subtitle_encryption_key";

    /** Platform seam: the Android keystore in production. */
    public interface KeyStoreAccess {
        /** @return the existing key or a newly generated one, or null when the keystore is unusable */
        SecretKey getOrCreate(String alias);

        void delete(String alias);
    }

    private final KeyStoreAccess mKeyStoreAccess;
    private SecretKey mCachedKey;

    public SubtitleKeySource(KeyStoreAccess keyStoreAccess) {
        mKeyStoreAccess = keyStoreAccess;
    }

    @Override
    public SecretKey getKey() {
        if (mCachedKey != null) {
            return mCachedKey;
        }

        if (mKeyStoreAccess == null) {
            return null;
        }

        try {
            mCachedKey = mKeyStoreAccess.getOrCreate(KEY_ALIAS);
        } catch (RuntimeException e) {
            return null; // an unusable keystore must degrade to "cannot persist", not crash
        }

        return mCachedKey;
    }

    /** Removes the stored key entry as well, so nothing can decrypt old ciphertext afterwards. */
    public void deleteKey() {
        mCachedKey = null;

        if (mKeyStoreAccess != null) {
            try {
                mKeyStoreAccess.delete(KEY_ALIAS);
            } catch (RuntimeException e) {
                // The session copy is already gone; the entry may be cleaned up later.
            }
        }
    }
}
