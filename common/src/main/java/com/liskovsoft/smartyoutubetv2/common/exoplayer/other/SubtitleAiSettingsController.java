package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

/**
 * Owns the non-sensitive AI settings and the key store, and turns menu actions into the smallest
 * correct side effect (plan sections 6.3 and 8).
 *
 * <p>Only endpoint, model, target language and expression instruction changes invalidate a running
 * session (their results were produced under another configuration); a display-mode change and the
 * per-video switch are saved but leave the session alone, because they only repaint. The API key is
 * never held here: it is read through {@link SubtitleKeyStore} when a request is authorized, which
 * also keeps this class free of any printable secret.
 */
public class SubtitleAiSettingsController {
    /** Notified when the translation configuration changed and running results became stale. */
    public interface ConfigurationChangeListener {
        void onConfigurationChanged();
    }

    private final SubtitleAiPrefsStore mPrefsStore;
    private final SubtitleKeyStore mKeyStore;
    private final ConfigurationChangeListener mListener;
    private SubtitleAiSettings mSettings;

    public SubtitleAiSettingsController(SubtitleAiPrefsStore prefsStore, SubtitleKeyStore keyStore,
                                        ConfigurationChangeListener listener) {
        mPrefsStore = prefsStore;
        mKeyStore = keyStore;
        mListener = listener;
        mSettings = prefsStore != null ? prefsStore.load() : SubtitleAiSettings.defaults();
    }

    public SubtitleAiSettings getSettings() {
        return mSettings;
    }

    public boolean isKeyConfigured() {
        return mKeyStore != null && mKeyStore.hasKey();
    }

    /** True when the key survives this process, so the menu can explain the other case. */
    public boolean isKeyPersistent() {
        return mKeyStore != null && mKeyStore.isPersistent();
    }

    /** The per-video switch: saved, and enabling/disabling only affects the session in place. */
    public void setEnabled(boolean enabled) {
        if (mSettings.isEnabled() == enabled) {
            return;
        }

        mSettings = SubtitleAiSettings.withEnabled(mSettings, enabled);
        persist();
    }

    /** A display-mode change is saved and repaints; it must not invalidate the session. */
    public void setDisplayMode(int displayMode) {
        if (mSettings.getDisplayMode() == displayMode) {
            return;
        }

        mSettings = SubtitleAiSettings.withDisplayMode(mSettings, displayMode);
        persist();
    }

    /** Endpoint, model, target language and instruction changes void results of the old setup. */
    public void setTargetLanguage(String targetLanguage) {
        updateConfig(mSettings.getConfig().getEndpointBaseUrl(), mSettings.getConfig().getModel(),
                targetLanguage, mSettings.getInstruction());
    }

    public void setModel(String model) {
        updateConfig(mSettings.getConfig().getEndpointBaseUrl(), model,
                mSettings.getTargetLanguage(), mSettings.getInstruction());
    }

    public void setEndpointBaseUrl(String endpointBaseUrl) {
        updateConfig(endpointBaseUrl, mSettings.getConfig().getModel(),
                mSettings.getTargetLanguage(), mSettings.getInstruction());
    }

    public void setInstruction(String instruction) {
        updateConfig(mSettings.getConfig().getEndpointBaseUrl(), mSettings.getConfig().getModel(),
                mSettings.getTargetLanguage(), instruction);
    }

    /** Restores the default expression instruction (the plan's "恢复默认"). */
    public void restoreDefaultInstruction() {
        updateConfig(mSettings.getConfig().getEndpointBaseUrl(), mSettings.getConfig().getModel(),
                mSettings.getTargetLanguage(), null);
    }

    /** For a "clear the key" action: forgets the key and stops authorizing requests. */
    public void clearKey() {
        if (mKeyStore != null) {
            mKeyStore.clear();
        }

        notifyConfigurationChanged();
    }

    public SubtitleTranslationService.ConfigProvider asConfigProvider() {
        return () -> mSettings.getConfig();
    }

    public SubtitleTranslationService.KeyProvider asKeyProvider() {
        return () -> mKeyStore != null ? mKeyStore.getApiKey() : null;
    }

    private void updateConfig(String endpointBaseUrl, String model, String targetLanguage, String instruction) {
        SubtitleAiSettings updated = SubtitleAiSettings.create(mSettings.isEnabled(), mSettings.getDisplayMode(),
                targetLanguage, instruction, endpointBaseUrl, model);

        if (updated.getConfig().namespace().equals(mSettings.getConfig().namespace())) {
            return; // nothing that affects cached results changed
        }

        mSettings = updated;
        persist();
        notifyConfigurationChanged();
    }

    private void persist() {
        if (mPrefsStore != null) {
            mPrefsStore.save(mSettings);
        }
    }

    private void notifyConfigurationChanged() {
        if (mListener != null) {
            mListener.onConfigurationChanged();
        }
    }
}
