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

    /**
     * Notified when the stored credential changed. This is deliberately not a configuration change:
     * the key is not part of the cache namespace, so translations produced with the previous key stay
     * valid, while a stopped session has to be allowed to start again.
     */
    public interface CredentialChangeListener {
        void onCredentialChanged();
    }

    private final SubtitleAiPrefsStore mPrefsStore;
    private final SubtitleKeyStore mKeyStore;
    private final ConfigurationChangeListener mListener;
    private CredentialChangeListener mCredentialListener;
    private SubtitleAiSettings mSettings;

    public SubtitleAiSettingsController(SubtitleAiPrefsStore prefsStore, SubtitleKeyStore keyStore,
                                        ConfigurationChangeListener listener) {
        mPrefsStore = prefsStore;
        mKeyStore = keyStore;
        mListener = listener;
        mSettings = prefsStore != null ? prefsStore.load() : SubtitleAiSettings.defaults();
    }

    public void setCredentialChangeListener(CredentialChangeListener credentialListener) {
        mCredentialListener = credentialListener;
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

    /**
     * Smart-context tier (Kiss feature: smart context, plan section 2): basic neighbours, coherent
     * examples or the one-shot video enhancement. It changes the request content, so the namespace
     * changes and the running session is rebuilt exactly once.
     *
     * @return true when the tier actually changed
     */
    public boolean setContextTier(int contextTier) {
        if (mSettings.getContextTier() == contextTier) {
            return false;
        }

        updateContent(SubtitleAiSettings.withContextTier(mSettings, contextTier));

        return true;
    }

    /**
     * Rule segmentation on/off (Kiss feature: rule segmentation). It changes the translated units,
     * so it is part of the identity and rebuilds the session exactly once.
     *
     * @return true when the switch actually changed
     */
    public boolean setRuleSegmentation(boolean ruleSegmentation) {
        if (mSettings.usesRuleSegmentation() == ruleSegmentation) {
            return false;
        }

        updateContent(SubtitleAiSettings.withRuleSegmentation(mSettings, ruleSegmentation));

        return true;
    }

    /**
     * Load-notification switch (Kiss feature: load notifications). Display-only by plan 4.5: it is
     * persisted but never invalidates a session, never clears anything and never starts a request.
     */
    public void setLoadNotifications(boolean loadNotifications) {
        if (mSettings.showsLoadNotifications() == loadNotifications) {
            return;
        }

        mSettings = SubtitleAiSettings.withLoadNotifications(mSettings, loadNotifications);
        persist();
    }

    /** Persists a content-affecting change and notifies once, only when the identity really moved. */
    private void updateContent(SubtitleAiSettings updated) {
        boolean contentChanged = !updated.getConfig().namespace().equals(mSettings.getConfig().namespace());

        mSettings = updated;
        persist();

        if (contentChanged) {
            notifyConfigurationChanged();
        }
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

    /**
     * A different origin means the key must not be sent to the new host, so the stored credential is
     * forgotten and the user is asked for it again (plan 6.3/18.3).
     */
    public void setEndpointBaseUrl(String endpointBaseUrl) {
        String previous = mSettings.getConfig().getEndpointBaseUrl();
        updateConfig(endpointBaseUrl, mSettings.getConfig().getModel(),
                mSettings.getTargetLanguage(), mSettings.getInstruction());

        if (!SubtitleEndpoint.isSameOrigin(previous, endpointBaseUrl)) {
            forgetKey();

            if (mCredentialListener != null) {
                mCredentialListener.onCredentialChanged();
            }
        }
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

    /**
     * Stores a key through the same store the running service reads, so a replacement takes effect
     * without a restart (task N2). Blank input is refused instead of replacing a stored key.
     *
     * @return true when the key was stored
     */
    public boolean saveKey(String apiKey) {
        if (mKeyStore == null || apiKey == null || apiKey.trim().isEmpty()) {
            return false;
        }

        if (!mKeyStore.save(apiKey.trim())) {
            return false;
        }

        if (mCredentialListener != null) {
            mCredentialListener.onCredentialChanged();
        }

        return true;
    }

    /** For a "clear the key" action: forgets the key and stops authorizing requests. */
    public void clearKey() {
        forgetKey();
        notifyConfigurationChanged();

        if (mCredentialListener != null) {
            mCredentialListener.onCredentialChanged();
        }
    }

    private void forgetKey() {
        if (mKeyStore != null) {
            mKeyStore.clear();
        }
    }

    public SubtitleTranslationService.ConfigProvider asConfigProvider() {
        return () -> mSettings.getConfig();
    }

    public SubtitleTranslationService.KeyProvider asKeyProvider() {
        return () -> mKeyStore != null ? mKeyStore.getApiKey() : null;
    }

    private void updateConfig(String endpointBaseUrl, String model, String targetLanguage, String instruction) {
        SubtitleAiSettings updated = SubtitleAiSettings.create(mSettings.isEnabled(), mSettings.getDisplayMode(),
                targetLanguage, instruction, endpointBaseUrl, model, mSettings.getContextTier(),
                mSettings.usesRuleSegmentation(), mSettings.showsLoadNotifications());

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
