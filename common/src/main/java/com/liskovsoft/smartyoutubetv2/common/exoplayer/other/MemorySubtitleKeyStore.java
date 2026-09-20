package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

/**
 * Session-only key store, the plan's rule for API 17-22 (no self-written cryptography).
 *
 * <p>The key is kept in memory for this process only; the menu must use {@link #isPersistent()} to
 * tell the user that it will have to be entered again after leaving the application.
 */
public class MemorySubtitleKeyStore implements SubtitleKeyStore {
    private String mApiKey;

    @Override
    public boolean isPersistent() {
        return false;
    }

    @Override
    public boolean hasKey() {
        return mApiKey != null;
    }

    @Override
    public boolean save(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return false;
        }

        mApiKey = apiKey.trim();

        return true;
    }

    /** @return the key for authorizing a request, or null when none was stored this session */
    @Override
    public String getApiKey() {
        return mApiKey;
    }

    @Override
    public void clear() {
        mApiKey = null;
    }

    @Override
    public String toString() {
        // Never print the key, not even a suffix.
        return "MemorySubtitleKeyStore{configured=" + hasKey() + "}";
    }
}
