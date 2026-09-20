package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** T08 slice: non-sensitive settings round-trip, key material excluded. */
@RunWith(RobolectricTestRunner.class)
public class SubtitleAiPrefsStoreTest {
    private Map<String, String> mMap;
    private SubtitleAiPrefsStore mStore;

    @Before
    public void setUp() {
        mMap = new HashMap<>();
        mStore = new SubtitleAiPrefsStore(new SubtitleAiPrefsStore.Backend() {
            @Override
            public String get(String key) {
                return mMap.get(key);
            }

            @Override
            public void put(String key, String value) {
                mMap.put(key, value);
            }
        });
    }

    @Test
    public void nothingStoredLoadsThePlanDefaults() {
        SubtitleAiSettings loaded = mStore.load();

        assertFalse(loaded.isEnabled());
        assertEquals(SubtitleComposer.MODE_ORIGINAL_ONLY, loaded.getDisplayMode());
        assertEquals(SubtitleLanguageSupport.DEFAULT_TARGET_LANGUAGE, loaded.getTargetLanguage());
        assertNull(loaded.getInstruction());
    }

    @Test
    public void settingsRoundTrip() {
        mStore.save(SubtitleAiSettings.create(true, SubtitleComposer.MODE_BILINGUAL, "zh-TW", "Be concise.",
                "https://example.com/v1", "custom-model"));

        SubtitleAiSettings loaded = mStore.load();

        assertFalse("the per-video switch must not survive into the next video", loaded.isEnabled());
        assertEquals(SubtitleComposer.MODE_BILINGUAL, loaded.getDisplayMode());
        assertEquals(SubtitleLanguageSupport.ZH_HANT, loaded.getTargetLanguage());
        assertEquals("Be concise.", loaded.getInstruction());
        assertEquals("https://example.com/v1", loaded.getConfig().getEndpointBaseUrl());
        assertEquals("custom-model", loaded.getConfig().getModel());
    }

    @Test
    public void hostileInstructionTextSurvivesTheRoundTrip() {
        // The control character sits inside the text: a trailing one is trimmed as whitespace by
        // the instruction rule, which is intended and covered by SubtitleProtocolInstructionTest.
        String hostile = "He said \"stop\"\nand \\ {not json} \u0007 end";
        mStore.save(SubtitleAiSettings.create(false, SubtitleComposer.MODE_TRANSLATION_ONLY, "en", hostile, null, null));

        assertEquals(hostile, mStore.load().getInstruction());
    }

    @Test
    public void damagedStoredValueFallsBackToDefaults() {
        mMap.put(SubtitleAiPrefsStore.STORAGE_KEY, "{not json");

        SubtitleAiSettings loaded = mStore.load();

        assertFalse(loaded.isEnabled());
        assertEquals(SubtitleComposer.MODE_ORIGINAL_ONLY, loaded.getDisplayMode());
    }

    @Test
    public void storedValueCarriesNoKeyMaterial() {
        mStore.save(SubtitleAiSettings.create(true, SubtitleComposer.MODE_BILINGUAL, "en", "style", null, null));

        String stored = mMap.get(SubtitleAiPrefsStore.STORAGE_KEY);

        assertFalse(stored.contains("sk-"));
        assertFalse(stored.contains("Bearer"));
        assertTrue(stored.contains("endpoint"));
    }

    @Test
    public void clearRemovesTheStoredSettings() {
        mStore.save(SubtitleAiSettings.create(true, 0, "en", null, null, null));

        mStore.clear();

        assertFalse(mStore.load().isEnabled());
    }
}
