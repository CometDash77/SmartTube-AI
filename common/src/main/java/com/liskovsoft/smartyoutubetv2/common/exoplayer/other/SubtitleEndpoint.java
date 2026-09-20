package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Validates and normalises the translation endpoint (plan 6.1).
 *
 * <p>Only HTTPS base URLs without user information, query or fragment are accepted, one
 * {@code /chat/completions} suffix is appended exactly once, and anything else returns null so the
 * caller can refuse the configuration instead of leaking a key to an unintended host.
 */
public final class SubtitleEndpoint {
    public static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    public static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    private SubtitleEndpoint() {
    }

    /**
     * @param baseUrl user configured base URL, optionally ending in {@code /v1}
     * @return the chat completions URL, or null when the base URL is unacceptable
     */
    public static String chatCompletionsUrl(String baseUrl) {
        String candidate = baseUrl == null || baseUrl.trim().isEmpty() ? DEFAULT_BASE_URL : baseUrl.trim();
        URI uri;

        try {
            uri = new URI(candidate);
        } catch (URISyntaxException e) {
            return null;
        }

        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isEmpty()) {
            return null; // plain HTTP, a missing host or any other scheme is refused
        }

        if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            return null;
        }

        String path = uri.getPath() == null ? "" : uri.getPath();

        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        if (!path.endsWith(CHAT_COMPLETIONS_PATH)) {
            path = path + CHAT_COMPLETIONS_PATH;
        }

        String port = uri.getPort() > 0 ? ":" + uri.getPort() : "";

        return "https://" + uri.getHost() + port + path;
    }

    /** True when two base URLs share the same origin, so a stored key may stay bound to it. */
    public static boolean isSameOrigin(String first, String second) {
        String firstUrl = chatCompletionsUrl(first);
        String secondUrl = chatCompletionsUrl(second);

        if (firstUrl == null || secondUrl == null) {
            return false;
        }

        try {
            URI firstUri = new URI(firstUrl);
            URI secondUri = new URI(secondUrl);

            return firstUri.getHost().equals(secondUri.getHost()) && firstUri.getPort() == secondUri.getPort();
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
