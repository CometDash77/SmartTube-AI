package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

/**
 * Encrypted, persistent key store (the plan's API 23+ rule).
 *
 * <p>It combines the platform-supplied encryption key ({@link SubtitleKeyCipher}) with an injected
 * storage location (the application's no-backup directory on device), so the whole behaviour can be
 * verified without Android. The decrypted key is kept for this session only, and a stored value that
 * can no longer be decrypted - a device migration or a keystore reset - is removed instead of being
 * kept as an unusable secret, which is what makes the UI ask for the key again.
 */
public class PersistentSubtitleKeyStore implements SubtitleKeyStore {
    /** Storage seam: the no-backup file in production, a string slot in tests. */
    public interface Storage {
        String read();

        void write(String value);

        void delete();
    }

    private final SubtitleKeyCipher mCipher;
    private final Storage mStorage;
    private String mSessionKey;

    public PersistentSubtitleKeyStore(SubtitleKeyCipher cipher, Storage storage) {
        mCipher = cipher;
        mStorage = storage;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public boolean hasKey() {
        return getApiKey() != null;
    }

    @Override
    public boolean save(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty() || mCipher == null || mStorage == null) {
            return false;
        }

        String stored = mCipher.encrypt(apiKey);

        if (stored == null) {
            return false; // never store an unreadable or plain value
        }

        try {
            mStorage.write(stored);
        } catch (RuntimeException e) {
            return false;
        }

        mSessionKey = apiKey.trim();

        return true;
    }

    @Override
    public String getApiKey() {
        if (mSessionKey != null) {
            return mSessionKey;
        }

        if (mCipher == null || mStorage == null) {
            return null;
        }

        String stored = mStorage.read();

        if (stored == null || stored.trim().isEmpty()) {
            return null;
        }

        String decrypted = mCipher.decrypt(stored);

        if (decrypted == null) {
            // Unusable saved state: drop it so the app asks for the key again instead of pretending
            // it is configured.
            try {
                mStorage.delete();
            } catch (RuntimeException e) {
                // Nothing else to do; the state stays unusable either way.
            }

            return null;
        }

        mSessionKey = decrypted;

        return mSessionKey;
    }

    @Override
    public void clear() {
        mSessionKey = null;

        if (mStorage != null) {
            try {
                mStorage.delete();
            } catch (RuntimeException e) {
                // The in-memory copy is gone, which is what matters for this session.
            }
        }
    }

    @Override
    public String toString() {
        // Never print the key, not even a suffix.
        return "PersistentSubtitleKeyStore{configured=" + hasKey() + "}";
    }
}
