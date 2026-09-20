package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

/**
 * Builds the authorization header and keeps the key out of anything that could be written out.
 *
 * <p>The key is only ever placed in the request header; every message, log line or failure code that
 * passes through {@link #redact} has the key removed first. There is no method that returns a stored
 * key or a key suffix for display.
 */
public final class SubtitleCredentials {
    public static final String REDACTED = "***";
    private static final String BEARER_PREFIX = "Bearer ";

    private SubtitleCredentials() {
    }

    /**
     * @return the header value, or null when no usable key is configured
     */
    public static String bearerHeader(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return null;
        }

        return BEARER_PREFIX + apiKey.trim();
    }

    /** Removes the configured key from a message before it can be logged or reported. */
    public static String redact(String text, String apiKey) {
        if (text == null) {
            return null;
        }

        if (apiKey == null || apiKey.trim().isEmpty()) {
            return text;
        }

        return text.replace(apiKey.trim(), REDACTED);
    }
}
