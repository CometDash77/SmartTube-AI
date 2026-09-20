package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** T07/T08 slice: the key never leaves the request header. */
public class SubtitleCredentialsTest {
    @Test
    public void blankKeysProduceNoHeader() {
        assertNull(SubtitleCredentials.bearerHeader(null));
        assertNull(SubtitleCredentials.bearerHeader("   "));
    }

    @Test
    public void headerUsesTheBearerSchemeAndTrimsTheKey() {
        assertEquals("Bearer sk-test", SubtitleCredentials.bearerHeader("  sk-test  "));
    }

    @Test
    public void redactionRemovesTheConfiguredKeyFromAMessage() {
        String message = "HTTP 401 for header Bearer sk-test-123 (host api.deepseek.com)";

        String redacted = SubtitleCredentials.redact(message, "sk-test-123");

        assertTrue(redacted.contains(SubtitleCredentials.REDACTED));
        assertTrue(!redacted.contains("sk-test-123"));
        assertTrue(redacted.contains("api.deepseek.com")); // unaffected context stays readable
    }

    @Test
    public void redactionLeavesTextWithoutTheKeyUntouched() {
        assertEquals("plain failure", SubtitleCredentials.redact("plain failure", "sk-test-123"));
        assertEquals("plain failure", SubtitleCredentials.redact("plain failure", null));
    }

    @Test
    public void redactionToleratesAnEmptyMessage() {
        assertEquals("", SubtitleCredentials.redact("", "sk-test"));
        assertNull(SubtitleCredentials.redact(null, "sk-test"));
    }
}
