package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

/**
 * Storage of the translation API key.
 *
 * <p>The interface is deliberately tiny and never exposes a key for display: there is no suffix
 * accessor, no "configured" string built from the key, and no way to read the key back except to
 * authorize one request. Implementations decide the platform rules (encrypted device storage on
 * API 23+, memory only below it), and the menu only needs {@link #isPersistent()} to explain which
 * rule applies.
 */
public interface SubtitleKeyStore extends SubtitleTranslationService.KeyProvider {
    /** False when the key lives only in memory for this session, so the UI must say so. */
    boolean isPersistent();

    boolean hasKey();

    /** @return true when a usable key was stored; a blank key is refused */
    boolean save(String apiKey);

    /** Clears the memory copy and any persisted ciphertext. */
    void clear();
}
