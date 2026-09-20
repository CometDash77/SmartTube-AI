package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

/**
 * Chooses the key store for the running platform (plan section 7).
 *
 * <p>From API 23 the encrypted device store is used; below it - and whenever the platform store
 * cannot be created at all, for example because the keystore is unavailable - the memory-only store
 * takes over, which the UI explains through {@link SubtitleKeyStore#isPersistent()}. The decision is
 * expressed here, away from the platform code, so it can be tested for every API level.
 */
public final class SubtitleKeyStoreFactory {
    /** The API level from which the encrypted device store is used. */
    public static final int PERSISTENT_FROM_API = 23;

    /** Creates the platform store; returns null when the device cannot provide one. */
    public interface PersistentKeyStoreFactory {
        SubtitleKeyStore create();
    }

    private SubtitleKeyStoreFactory() {
    }

    public static boolean canPersist(int sdkInt) {
        return sdkInt >= PERSISTENT_FROM_API;
    }

    public static SubtitleKeyStore create(int sdkInt, PersistentKeyStoreFactory persistentKeyStoreFactory) {
        if (canPersist(sdkInt) && persistentKeyStoreFactory != null) {
            SubtitleKeyStore store = persistentKeyStoreFactory.create();

            if (store != null && store.isPersistent()) {
                return store;
            }
        }

        return new MemorySubtitleKeyStore();
    }
}
