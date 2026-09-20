package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** T07 slice: endpoint rules of section 6.1 (plain JUnit: no Android or Robolectric needed). */
public class SubtitleEndpointTest {
    @Test
    public void defaultBaseUrlIsTheOfficialHost() {
        assertEquals("https://api.deepseek.com/chat/completions", SubtitleEndpoint.chatCompletionsUrl(null));
        assertEquals("https://api.deepseek.com/chat/completions", SubtitleEndpoint.chatCompletionsUrl("   "));
        assertEquals("https://api.deepseek.com/chat/completions", SubtitleEndpoint.chatCompletionsUrl(SubtitleEndpoint.DEFAULT_BASE_URL));
    }

    @Test
    public void trailingSlashAndV1SuffixAreNormalisedOnce() {
        assertEquals("https://api.deepseek.com/chat/completions", SubtitleEndpoint.chatCompletionsUrl("https://api.deepseek.com/"));
        assertEquals("https://api.deepseek.com/v1/chat/completions", SubtitleEndpoint.chatCompletionsUrl("https://api.deepseek.com/v1"));
        assertEquals("https://api.deepseek.com/v1/chat/completions", SubtitleEndpoint.chatCompletionsUrl("https://api.deepseek.com/v1/"));
    }

    @Test
    public void anAlreadyCompletePathIsNotDuplicated() {
        assertEquals("https://api.deepseek.com/v1/chat/completions",
                SubtitleEndpoint.chatCompletionsUrl("https://api.deepseek.com/v1/chat/completions"));
    }

    @Test
    public void explicitPortIsKept() {
        assertEquals("https://example.com:8443/chat/completions", SubtitleEndpoint.chatCompletionsUrl("https://example.com:8443"));
    }

    @Test
    public void plainHttpIsRefused() {
        assertNull(SubtitleEndpoint.chatCompletionsUrl("http://api.deepseek.com"));
    }

    @Test
    public void userInfoQueryAndFragmentAreRefused() {
        assertNull(SubtitleEndpoint.chatCompletionsUrl("https://user:pass@api.deepseek.com"));
        assertNull(SubtitleEndpoint.chatCompletionsUrl("https://api.deepseek.com?key=1"));
        assertNull(SubtitleEndpoint.chatCompletionsUrl("https://api.deepseek.com#frag"));
    }

    @Test
    public void missingHostOrForeignSchemeIsRefused() {
        assertNull(SubtitleEndpoint.chatCompletionsUrl("https:///chat"));
        assertNull(SubtitleEndpoint.chatCompletionsUrl("ftp://api.deepseek.com"));
        assertNull(SubtitleEndpoint.chatCompletionsUrl("not a url"));
    }

    @Test
    public void keyBindingFollowsTheOrigin() {
        assertTrue(SubtitleEndpoint.isSameOrigin("https://api.deepseek.com", "https://api.deepseek.com/v1"));
        assertFalse(SubtitleEndpoint.isSameOrigin("https://api.deepseek.com", "https://other.example.com"));
        assertFalse(SubtitleEndpoint.isSameOrigin("https://api.deepseek.com", "http://api.deepseek.com"));
    }
}
