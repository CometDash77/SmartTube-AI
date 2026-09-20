package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

/**
 * The one state the AI subtitle area of the menu shows (plan section 5).
 *
 * <p>It is computed from values the app already has, so the menu does not have to reason about
 * transports: the per-video switch, whether a subtitle source is bound, whether a key is configured
 * and whether translation is currently paused (rate limit or repeated failures).
 */
public final class SubtitleAiMenuState {
    public enum Status {
        /** The AI switch is off for this video. */
        AI_OFF,
        /** AI is on but no key is configured yet: the menu must offer the key dialog. */
        NO_KEY,
        /** The key was rejected (401/403) or the balance is exhausted (402): requests stopped. */
        AUTH_FAILED,
        /** AI is on but no subtitle track is bound. */
        NO_SOURCE,
        /** Translation is paused; the original subtitles keep playing. */
        PAUSED,
        /** Translations are being prepared or are already available. */
        TRANSLATING
    }

    private SubtitleAiMenuState() {
    }

    public static Status of(boolean aiEnabled, boolean sourceBound, boolean keyConfigured, long pauseRemainingMs) {
        return of(aiEnabled, sourceBound, keyConfigured, false, pauseRemainingMs);
    }

    public static Status of(boolean aiEnabled, boolean sourceBound, boolean keyConfigured,
                            boolean authorizationStopped, long pauseRemainingMs) {
        if (!aiEnabled) {
            return Status.AI_OFF;
        }

        if (!keyConfigured) {
            return Status.NO_KEY;
        }

        if (authorizationStopped) {
            return Status.AUTH_FAILED;
        }

        if (!sourceBound) {
            return Status.NO_SOURCE;
        }

        return pauseRemainingMs > 0 ? Status.PAUSED : Status.TRANSLATING;
    }

    /** True when the menu should show the "check AI configuration" entry for this state. */
    public static boolean offersConfiguration(Status status) {
        return status == Status.NO_KEY || status == Status.AUTH_FAILED;
    }

    /** True when the state means the user still sees the original subtitles. */
    public static boolean showsOriginalOnly(Status status) {
        return status == Status.AI_OFF || status == Status.NO_KEY || status == Status.AUTH_FAILED
                || status == Status.NO_SOURCE || status == Status.PAUSED;
    }
}
