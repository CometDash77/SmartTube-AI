package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns one {@link SubtitleTimeline} plus the translations already cached for it into SRT text
 * (plan section 14, T13).
 *
 * <p>It is a pure function of its inputs: it decodes nothing, requests nothing and translates
 * nothing. Every mode mirrors a display mode of the player so the exported file shows exactly what
 * the user could already see:
 * <ul>
 *     <li>{@link #formatOriginal} always writes the original text.</li>
 *     <li>{@link #formatTranslated} writes the translation where one exists and falls back to the
 *     original line otherwise (never a placeholder, never a blank cue).</li>
 *     <li>{@link #formatBilingual} writes the original line and, when present, the translation below
 *     it.</li>
 * </ul>
 *
 * <p>Timings stay in the native microsecond base and are converted exactly once, to SRT
 * {@code HH:MM:SS,mmm}. An empty frame is a clearing boundary, not a cue, so it produces no entry.
 * The final frame of a snapshot has no known end (the fetch passes {@code C.TIME_UNSET}); its end is
 * estimated from the last known cue length and the estimate is disclosed in the archive's README.
 */
public final class SubtitleSrtFormatter {
    /** SRT files conventionally use CRLF; players accept it and plain LF. */
    private static final String EOL = "\r\n";
    /** Line break inside one cue. */
    private static final String LINE = "\n";
    public static final long DEFAULT_LAST_CUE_MS = 3_000;
    public static final long MIN_LAST_CUE_MS = 1_000;
    public static final long MAX_LAST_CUE_MS = 10_000;
    private static final long US_PER_MS = 1_000L;

    private static final int MODE_ORIGINAL = 0;
    private static final int MODE_TRANSLATED = 1;
    private static final int MODE_BILINGUAL = 2;

    private SubtitleSrtFormatter() {
    }

    /** How much of the obtained timeline already has a translation. */
    public static final class Coverage {
        private final int mFrames;
        private final int mItems;
        private final int mTranslatedItems;

        Coverage(int frames, int items, int translatedItems) {
            mFrames = frames;
            mItems = items;
            mTranslatedItems = translatedItems;
        }

        /** Frames that actually show text (clearing boundaries are not counted). */
        public int getFrames() {
            return mFrames;
        }

        /** Distinct translatable items across those frames. */
        public int getItems() {
            return mItems;
        }

        public int getTranslatedItems() {
            return mTranslatedItems;
        }

        public int getMissingItems() {
            return Math.max(0, mItems - mTranslatedItems);
        }

        /** Integer percentage; 0 when there is nothing to translate. */
        public int getCoveragePercent() {
            return mItems == 0 ? 0 : (int) (((long) mTranslatedItems * 100L) / mItems);
        }

        @Override
        public String toString() {
            return "Coverage{frames=" + mFrames + ", items=" + mItems + ", translated="
                    + mTranslatedItems + "}";
        }
    }

    /** Counts frames, distinct item ids and the ones that already have a non-blank translation. */
    public static Coverage measure(SubtitleTimeline timeline, Map<String, String> translations) {
        if (timeline == null) {
            return new Coverage(0, 0, 0);
        }

        Map<String, String> lookup = translations != null ? translations : Collections.<String, String>emptyMap();
        LinkedHashSet<String> itemIds = new LinkedHashSet<>();
        int frames = 0;

        for (SubtitleFrame frame : timeline.getFrames()) {
            List<SubtitleItem> items = visibleItems(frame);

            if (items.isEmpty()) {
                continue;
            }

            frames++;

            for (SubtitleItem item : items) {
                itemIds.add(item.getItemId());
            }
        }

        int translated = 0;

        for (String itemId : itemIds) {
            if (!isBlank(lookup.get(itemId))) {
                translated++;
            }
        }

        return new Coverage(frames, itemIds.size(), translated);
    }

    /** Original-only SRT of the whole obtained timeline; empty when there is nothing to write. */
    public static String formatOriginal(SubtitleTimeline timeline) {
        return format(timeline, null, MODE_ORIGINAL);
    }

    /** Translation SRT with an original fallback for every item that has no translation yet. */
    public static String formatTranslated(SubtitleTimeline timeline, Map<String, String> translations) {
        return format(timeline, translations, MODE_TRANSLATED);
    }

    /** Bilingual SRT: the original line, then its translation when one exists. */
    public static String formatBilingual(SubtitleTimeline timeline, Map<String, String> translations) {
        return format(timeline, translations, MODE_BILINGUAL);
    }

    /**
     * Only the cues that still have no translation: the part an interrupted or failing run left
     * behind, written as playable SRT so a user can see exactly what is missing.
     */
    public static String formatUntranslated(SubtitleTimeline timeline, Map<String, String> translations) {
        return format(timeline, translations, MODE_ORIGINAL, true);
    }

    private static String format(SubtitleTimeline timeline, Map<String, String> translations, int mode) {
        return format(timeline, translations, mode, false);
    }

    private static String format(SubtitleTimeline timeline, Map<String, String> translations, int mode,
                                 boolean onlyMissing) {
        if (timeline == null || timeline.isEmpty()) {
            return "";
        }

        Map<String, String> lookup = translations != null ? translations : Collections.<String, String>emptyMap();
        long fallbackEndUs = estimateFallbackDurationUs(timeline);
        StringBuilder out = new StringBuilder();
        int index = 0;

        for (SubtitleFrame frame : timeline.getFrames()) {
            if (frame == null) {
                continue;
            }

            List<SubtitleItem> items = visibleItems(frame);

            if (onlyMissing) {
                items = itemsWithoutTranslation(items, lookup);
            }

            if (items.isEmpty()) {
                continue; // a clearing boundary is not a cue
            }

            long startUs = frame.getStartUs();
            long endUs = frame.getEndUs();

            if (endUs < 0) {
                endUs = startUs + fallbackEndUs; // C.TIME_UNSET: no known end
            } else if (endUs <= startUs) {
                continue; // zero or negative length: never displayable
            }

            String text = compose(items, lookup, mode);

            if (isBlank(text)) {
                continue;
            }

            index++;
            out.append(index).append(EOL)
                    .append(formatTimestamp(startUs)).append(" --> ").append(formatTimestamp(endUs)).append(EOL)
                    .append(text).append(EOL)
                    .append(EOL);
        }

        return out.toString();
    }

    private static String compose(List<SubtitleItem> items, Map<String, String> translations, int mode) {
        StringBuilder text = new StringBuilder();

        for (SubtitleItem item : items) {
            String original = item.getText();
            String translation = translationOf(translations, item.getItemId());
            String line;

            switch (mode) {
                case MODE_TRANSLATED:
                    line = translation != null ? translation : original;
                    break;
                case MODE_BILINGUAL:
                    line = translation != null ? original + LINE + translation : original;
                    break;
                default:
                    line = original;
                    break;
            }

            if (text.length() > 0) {
                text.append(LINE);
            }

            text.append(line);
        }

        return text.toString();
    }

    /** A blank entry is a failed translation, not content. */
    private static String translationOf(Map<String, String> translations, String itemId) {
        String translation = translations.get(itemId);

        return isBlank(translation) ? null : translation;
    }

    /** Keeps only the items whose lookup has no usable translation. */
    private static List<SubtitleItem> itemsWithoutTranslation(List<SubtitleItem> items,
                                                              Map<String, String> translations) {
        List<SubtitleItem> missing = new ArrayList<>();

        for (SubtitleItem item : items) {
            if (translationOf(translations, item.getItemId()) == null) {
                missing.add(item);
            }
        }

        return missing;
    }

    /** The items that actually carry text; blank ones would only add empty lines. */
    private static List<SubtitleItem> visibleItems(SubtitleFrame frame) {
        List<SubtitleItem> items = new ArrayList<>();

        if (frame == null) {
            return items;
        }

        for (SubtitleItem item : frame.getItems()) {
            if (item != null && !isBlank(item.getText())) {
                items.add(item);
            }
        }

        return items;
    }

    /**
     * Length used for a frame whose end is unknown: the longest cue length the source itself
     * declared, clamped so a synthetic estimate can never pretend to cover minutes of video.
     */
    private static long estimateFallbackDurationUs(SubtitleTimeline timeline) {
        long longestUs = 0;
        long limitUs = MAX_LAST_CUE_MS * US_PER_MS;

        for (SubtitleFrame frame : timeline.getFrames()) {
            if (frame == null) {
                continue;
            }

            long durationUs = frame.getEndUs() - frame.getStartUs();

            if (durationUs > longestUs && durationUs <= limitUs) {
                longestUs = durationUs;
            }
        }

        if (longestUs <= 0) {
            longestUs = DEFAULT_LAST_CUE_MS * US_PER_MS;
        }

        if (longestUs < MIN_LAST_CUE_MS * US_PER_MS) {
            longestUs = MIN_LAST_CUE_MS * US_PER_MS;
        }

        return longestUs;
    }

    /** SRT timestamp; a position before zero would only produce a negative timecode. */
    static String formatTimestamp(long positionUs) {
        long millis = positionUs / US_PER_MS;

        if (millis < 0) {
            millis = 0;
        }

        long hours = millis / 3_600_000L;
        long minutes = (millis / 60_000L) % 60L;
        long seconds = (millis / 1_000L) % 60L;

        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis % 1_000L);
    }

    static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }
}
