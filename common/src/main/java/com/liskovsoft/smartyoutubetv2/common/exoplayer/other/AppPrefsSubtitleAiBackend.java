package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;

/**
 * Application prefs backend for the non-sensitive AI settings (plan section 8).
 *
 * <p>It reuses the project's existing profile-aware preferences through {@link AppPrefs#getData} and
 * {@link AppPrefs#setData} - the same mechanism other data classes use - instead of inventing a new
 * storage and instead of occupying {@code PlayerData} serialization slots. Only the settings JSON of
 * {@link SubtitleAiPrefsStore} passes through here; the API key has its own encrypted store.
 */
public class AppPrefsSubtitleAiBackend implements SubtitleAiPrefsStore.Backend {
    private final AppPrefs mAppPrefs;

    public AppPrefsSubtitleAiBackend(Context context) {
        mAppPrefs = AppPrefs.instance(context);
    }

    @Override
    public String get(String key) {
        return mAppPrefs != null && key != null ? mAppPrefs.getData(key) : null;
    }

    @Override
    public void put(String key, String value) {
        if (mAppPrefs != null && key != null) {
            // AppPrefs stores data as a file: writing null would leave the previous content in
            // place, so a cleared value is written as an empty file, which the settings store then
            // treats as "nothing stored".
            mAppPrefs.setData(key, value == null ? "" : value);
        }
    }
}
