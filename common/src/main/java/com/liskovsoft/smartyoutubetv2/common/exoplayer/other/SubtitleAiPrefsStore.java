package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Persistence of the non-sensitive AI subtitle settings (plan sections 6.1 and 8).
 *
 * <p>Only the endpoint, model, target language, display mode, expression instruction, the AI switch,
 * the smart-context tier, the segmentation switch and the notification switch are stored, as one
 * JSON object through an injected backend, so this class stays free of prefs details and can be
 * tested without a device. The API key is not part of it: it belongs to {@link SubtitleKeyStore},
 * which has its own storage and exclusion rules.
 *
 * <p>The settings written by earlier releases may lack the keys added for the Kiss features; those
 * fall back to their defaults on load, so no migration step is needed and an unreadable value never
 * breaks playback.
 */
public class SubtitleAiPrefsStore {
    public static final String STORAGE_KEY = "ai_subtitle_settings_v1";

    /** Backend seam: the application prefs in production, a map in tests. */
    public interface Backend {
        String get(String key);

        void put(String key, String value);
    }

    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_MODE = "mode";
    private static final String KEY_TARGET = "target";
    private static final String KEY_INSTRUCTION = "instruction";
    private static final String KEY_ENDPOINT = "endpoint";
    private static final String KEY_MODEL = "model";
    private static final String KEY_CONTEXT_TIER = "contextTier";
    private static final String KEY_RULE_SEGMENTATION = "ruleSegmentation";
    private static final String KEY_LOAD_NOTIFICATIONS = "loadNotifications";

    private final Backend mBackend;

    public SubtitleAiPrefsStore(Backend backend) {
        mBackend = backend;
    }

    /** Saves the non-sensitive part of the settings; the key is never written here. */
    public void save(SubtitleAiSettings settings) {
        if (mBackend == null || settings == null) {
            return;
        }

        try {
            JSONObject json = new JSONObject();
            // The per-video AI switch is deliberately NOT persisted: a new video must start with AI
            // off, while target language, mode, endpoint, model and instruction are remembered.
            json.put(KEY_ENABLED, false);
            json.put(KEY_MODE, settings.getDisplayMode());
            json.put(KEY_TARGET, settings.getTargetLanguage());
            json.put(KEY_INSTRUCTION, settings.getInstruction());
            json.put(KEY_ENDPOINT, settings.getConfig().getEndpointBaseUrl());
            json.put(KEY_MODEL, settings.getConfig().getModel());
            json.put(KEY_CONTEXT_TIER, settings.getContextTier());
            json.put(KEY_RULE_SEGMENTATION, settings.usesRuleSegmentation());
            json.put(KEY_LOAD_NOTIFICATIONS, settings.showsLoadNotifications());

            mBackend.put(STORAGE_KEY, json.toString());
        } catch (JSONException e) {
            // Nothing sensible to do: keep the previous settings rather than storing half of them.
        }
    }

    /**
     * @return the stored settings, or the plan's defaults when nothing usable was stored
     */
    public SubtitleAiSettings load() {
        if (mBackend == null) {
            return SubtitleAiSettings.defaults();
        }

        String stored = mBackend.get(STORAGE_KEY);

        if (stored == null || stored.trim().isEmpty()) {
            return SubtitleAiSettings.defaults();
        }

        try {
            JSONObject json = new JSONObject(stored);

            return SubtitleAiSettings.create(false, json.optInt(KEY_MODE, 0),
                    json.optString(KEY_TARGET, null), json.optString(KEY_INSTRUCTION, null),
                    json.optString(KEY_ENDPOINT, null), json.optString(KEY_MODEL, null),
                    json.optInt(KEY_CONTEXT_TIER, SubtitleAiSettings.CONTEXT_BASIC),
                    json.optBoolean(KEY_RULE_SEGMENTATION, false),
                    json.optBoolean(KEY_LOAD_NOTIFICATIONS, true));
        } catch (JSONException e) {
            return SubtitleAiSettings.defaults(); // a damaged value must not break playback
        }
    }

    public void clear() {
        if (mBackend != null) {
            mBackend.put(STORAGE_KEY, null);
        }
    }
}
