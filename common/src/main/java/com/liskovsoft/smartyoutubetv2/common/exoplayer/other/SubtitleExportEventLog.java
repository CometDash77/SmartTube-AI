package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import java.util.ArrayList;
import java.util.List;

/**
 * Bounded ring of recent session events for the diagnostic report (plan section 14, T13).
 *
 * <p>Only enumerated codes reach the report: {@link #sanitize(String)} reduces whatever the caller
 * passes to upper-case letters, digits and underscores, so no subtitle text, URL or credential can
 * travel through an event entry even by mistake. The timestamps are monotonic offsets, not wall-clock
 * times, so nothing here identifies the user.
 */
public final class SubtitleExportEventLog {
    public static final int MAX_EVENTS = 40;
    private static final int MAX_CODE_LENGTH = 48;

    /** Monotonic clock; the same seam the prefetch dispatcher uses. */
    public interface Clock {
        long elapsedRealtimeMs();
    }

    private final Clock mClock;
    private final long mStartMs;
    private final List<String> mEvents = new ArrayList<>();

    public SubtitleExportEventLog(Clock clock) {
        mClock = clock != null ? clock : () -> 0L;
        mStartMs = mClock.elapsedRealtimeMs();
    }

    public synchronized void add(String code) {
        long offsetMs = mClock.elapsedRealtimeMs() - mStartMs;
        mEvents.add("+" + Math.max(0, offsetMs) + "ms " + sanitize(code));

        while (mEvents.size() > MAX_EVENTS) {
            mEvents.remove(0);
        }
    }

    public synchronized List<String> snapshot() {
        return new ArrayList<>(mEvents);
    }

    public synchronized void clear() {
        mEvents.clear();
    }

    /** Restricts an event to the code alphabet: letters, digits and underscores only. */
    static String sanitize(String code) {
        if (code == null) {
            return "UNKNOWN";
        }

        StringBuilder out = new StringBuilder();

        for (int i = 0; i < code.length() && out.length() < MAX_CODE_LENGTH; i++) {
            char c = Character.toUpperCase(code.charAt(i));

            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_') {
                out.append(c);
            }
        }

        return out.length() == 0 ? "UNKNOWN" : out.toString();
    }
}
