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

/** T08/T09 slice: menu actions mapped onto the smallest correct side effect. */
@RunWith(RobolectricTestRunner.class)
public class SubtitleAiSettingsControllerTest {
    private Map<String, String> mStore;
    private int mConfigChanges;
    private int mCredentialChanges;
    private SubtitleAiSettingsController mController;

    @Before
    public void setUp() {
        mStore = new HashMap<>();
        mConfigChanges = 0;
        MemorySubtitleKeyStore keyStore = new MemorySubtitleKeyStore();
        keyStore.save("sk-test");
        mController = new SubtitleAiSettingsController(
                new SubtitleAiPrefsStore(new SubtitleAiPrefsStore.Backend() {
                    @Override
                    public String get(String key) {
                        return mStore.get(key);
                    }

                    @Override
                    public void put(String key, String value) {
                        mStore.put(key, value);
                    }
                }), keyStore, () -> mConfigChanges++);
        mController.setCredentialChangeListener(() -> mCredentialChanges++);
    }

    @Test
    public void settingsAreLoadedFromStorageAndTheKeyIsReported() {
        assertFalse(mController.getSettings().isEnabled());
        assertTrue(mController.isKeyConfigured());
        assertFalse("the memory store must report itself as session-only", mController.isKeyPersistent());
    }

    @Test
    public void enablingAndModeChangesAreSavedWithoutInvalidatingTheSession() {
        mController.setEnabled(true);
        mController.setDisplayMode(SubtitleComposer.MODE_BILINGUAL);

        assertEquals(0, mConfigChanges);
        assertEquals(SubtitleComposer.MODE_BILINGUAL, mController.getSettings().getDisplayMode());
        assertTrue(mStore.containsKey(SubtitleAiPrefsStore.STORAGE_KEY));
        assertTrue(mController.getSettings().isEnabled());
    }

    @Test
    public void changingTheTargetLanguageInvalidatesTheSessionOnce() {
        mController.setEnabled(true);
        int before = mConfigChanges;

        mController.setTargetLanguage("zh-CN");

        assertEquals(before + 1, mConfigChanges);
        assertEquals(SubtitleLanguageSupport.ZH_HANS, mController.getSettings().getTargetLanguage());
    }

    @Test
    public void repeatingTheSameConfigurationChangeDoesNotNotifyAgain() {
        mController.setTargetLanguage("fr");
        int before = mConfigChanges;

        mController.setTargetLanguage("fr");

        assertEquals("an idempotent setter must not churn the session", before, mConfigChanges);
    }

    @Test
    public void modelEndpointAndInstructionChangesEachInvalidate() {
        mController.setModel("other-model");
        mController.setEndpointBaseUrl("https://example.com");
        mController.setInstruction("Be concise.");

        assertEquals(3, mConfigChanges);
        assertEquals("other-model", mController.getSettings().getConfig().getModel());
        assertEquals("https://example.com", mController.getSettings().getConfig().getEndpointBaseUrl());
        assertEquals("Be concise.", mController.getSettings().getInstruction());
    }

    @Test
    public void restoringTheDefaultInstructionClearsIt() {
        mController.setInstruction("Be concise.");

        mController.restoreDefaultInstruction();

        assertNull(mController.getSettings().getInstruction());
    }

    @Test
    public void clearingTheKeyStopsAuthorizingAndInvalidates() {
        int before = mConfigChanges;

        mController.clearKey();

        assertFalse(mController.isKeyConfigured());
        assertNull(mController.asKeyProvider().getApiKey());
        assertEquals(before + 1, mConfigChanges);
    }

    @Test
    public void aSavedKeyGoesThroughTheActiveStoreAndNotifiesTheCredentialListener() {
        assertTrue(mController.saveKey("  sk-replaced  "));

        assertEquals("sk-replaced", mController.asKeyProvider().getApiKey());
        assertEquals(1, mCredentialChanges);
        assertEquals("a key change must not invalidate cached results", 0, mConfigChanges);
    }

    @Test
    public void aBlankKeyIsRefusedWithoutReplacingTheStoredOne() {
        assertFalse(mController.saveKey("   "));
        assertFalse(mController.saveKey(null));

        assertEquals("sk-test", mController.asKeyProvider().getApiKey());
        assertEquals(0, mCredentialChanges);
    }

    @Test
    public void aDifferentEndpointOriginForgetsTheKeyAndAsksAgain() {
        mController.setEndpointBaseUrl("https://other.example.com");

        assertFalse("the key must never be sent to another origin", mController.isKeyConfigured());
        assertEquals(1, mCredentialChanges);
    }

    @Test
    public void theSameEndpointOriginKeepsTheKey() {
        mController.setEndpointBaseUrl("https://api.deepseek.com/v1");

        assertTrue(mController.isKeyConfigured());
        assertEquals(0, mCredentialChanges);
    }

    @Test
    public void clearingTheKeyNotifiesBothListeners() {
        mController.clearKey();

        assertFalse(mController.isKeyConfigured());
        assertEquals(1, mCredentialChanges);
        assertEquals(1, mConfigChanges);
    }

    @Test
    public void providersExposeTheCurrentSettingsWithoutAnySecret() {
        mController.setTargetLanguage("zh-TW");

        assertEquals(SubtitleLanguageSupport.ZH_HANT, mController.asConfigProvider().getConfig().getTargetLanguage());
        assertEquals("sk-test", mController.asKeyProvider().getApiKey());
        assertFalse(mController.asConfigProvider().getConfig().namespace().contains("sk-"));
    }

    @Test
    public void editedModelAndEndpointPersistAndReachTheActualRequest() throws Exception {
        mController.setEndpointBaseUrl("https://api.deepseek.com/v1");
        mController.setModel("deepseek-v4-pro");
        org.json.JSONObject stored = new org.json.JSONObject(mStore.get(SubtitleAiPrefsStore.STORAGE_KEY));
        assertEquals("deepseek-v4-pro", stored.getString("model"));
        assertEquals("https://api.deepseek.com/v1", stored.getString("endpoint"));
        SubtitleTranslationRequest request = SubtitleTranslationRequest.create(
                mController.asConfigProvider().getConfig(), "local-placeholder", "en", SubtitleConnectionTest.sampleBatch());
        assertEquals("https://api.deepseek.com/v1/chat/completions", request.getUrl());
        assertEquals("deepseek-v4-pro", new org.json.JSONObject(request.getBody()).getString("model"));
    }
}
