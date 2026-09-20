package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** T08 slice: the settings store really works on the application preferences. */
@RunWith(RobolectricTestRunner.class)
public class AppPrefsSubtitleAiBackendTest {
    @Test
    public void valuesRoundTripThroughTheApplicationPreferences() {
        AppPrefsSubtitleAiBackend backend = new AppPrefsSubtitleAiBackend(RuntimeEnvironment.application);

        assertNull(backend.get("ai_subtitle_test_key"));

        backend.put("ai_subtitle_test_key", "value");
        assertEquals("value", backend.get("ai_subtitle_test_key"));

        backend.put("ai_subtitle_test_key", "other");
        assertEquals("other", backend.get("ai_subtitle_test_key"));

        backend.put("ai_subtitle_test_key", null);

        String cleared = backend.get("ai_subtitle_test_key");
        org.junit.Assert.assertTrue("a cleared value must read back as empty, not as the old content",
                cleared == null || cleared.trim().isEmpty());
    }

    @Test
    public void theSettingsStoreUsesItEndToEnd() {
        SubtitleAiPrefsStore store = new SubtitleAiPrefsStore(
                new AppPrefsSubtitleAiBackend(RuntimeEnvironment.application));

        store.save(SubtitleAiSettings.create(true, SubtitleComposer.MODE_BILINGUAL, "zh-CN", "Be concise.", null, null));

        SubtitleAiSettings loaded = store.load();

        // The per-video switch is deliberately not persisted (plan section 5): a new video starts
        // with AI off, while the target language, mode and instruction are remembered.
        assertEquals(false, loaded.isEnabled());
        assertEquals(SubtitleComposer.MODE_BILINGUAL, loaded.getDisplayMode());
        assertEquals(SubtitleLanguageSupport.ZH_HANS, loaded.getTargetLanguage());
        assertEquals("Be concise.", loaded.getInstruction());
    }
}
