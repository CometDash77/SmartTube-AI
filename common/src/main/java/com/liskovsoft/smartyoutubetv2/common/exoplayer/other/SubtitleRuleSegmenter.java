package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import com.google.android.exoplayer2.C;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Derives rule-based sentences from the native snapshot (Kiss feature: rule segmentation, plan 4.3).
 *
 * <p>It never invents timing and never rewrites the original text: it only groups whole native items
 * that belong together. The output is a derived {@link SubtitleTimeline} whose items are sentences
 * (one stable id per sentence) plus the clearing frames of the original, so the existing planner,
 * cache, translation and display path can consume it unchanged.
 *
 * <p>Boundaries (plan 4.3.2/4.3.3):
 * <ul>
 *     <li>A sentence ends at end-of-sentence punctuation (a closing quote after it still counts), at
 *     a pause longer than one second, at a length cap or at the ten-second duration cap.</li>
 *     <li>A clearing frame, a speaker change, a music/non-speech marker, an overlapping multi-slot
 *     frame and an unknown end time are hard boundaries: nothing is merged across them.</li>
 *     <li>Space languages cap at 100 code points / 15 words; Han and Kana scripts cap at 30 code
 *     points and are joined without a space; Korean keeps its spaces.</li>
 *     <li>An item that cannot be split stays whole and is counted as an unsplit.</li>
 * </ul>
 *
 * <p>The derived timeline is only usable when its segments are ordered, non-overlapping, non-negative
 * and never cover an original clearing region; otherwise the caller keeps the raw timeline.
 */
public class SubtitleRuleSegmenter {
    /** Version of these rules; part of every segment id and of the translation identity. */
    public static final int RULE_VERSION = SubtitleAiSettings.SEGMENTATION_RULE_VERSION;
    public static final int MAX_SPACE_CODE_POINTS = 100;
    public static final int MAX_SPACE_WORDS = 15;
    public static final int MAX_CJK_CODE_POINTS = 30;
    public static final long MAX_DURATION_US = 10_000_000L;
    public static final long PAUSE_BOUNDARY_US = 1_000_000L;
    public static final String FAILURE_NO_FRAMES = "no_frames";
    public static final String FAILURE_INVALID = "invalid_derived_timeline";

    /** End-of-sentence punctuation; a closing quote or bracket after it still ends the sentence. */
    private static final String SENTENCE_END = ".!?\u3002\uff01\uff1f\u2026\uff0e";
    private static final String CLOSING = "\")]\u201d\u2019\u300d\u300f\uff09\u3011\u300b";
    /** Tiny closed set: an abbreviation is not a sentence end, a decimal is not either. */
    private static final Set<String> ABBREVIATIONS = new HashSet<>(Arrays.asList(
            "mr", "mrs", "ms", "dr", "prof", "st", "vs", "etc", "e.g", "i.e", "no", "fig", "inc", "jr"));

    /** One derived sentence: stable id, member item ids, span and the original text it shows. */
    public static final class Segment {
        private final String mSegmentId;
        private final List<String> mMemberItemIds;
        private final long mStartUs;
        private final long mEndUs;
        private final String mSourceText;
        private final boolean mUnsplit;

        private Segment(String segmentId, List<String> memberItemIds, long startUs, long endUs,
                        String sourceText, boolean unsplit) {
            mSegmentId = segmentId;
            mMemberItemIds = Collections.unmodifiableList(new ArrayList<>(memberItemIds));
            mStartUs = startUs;
            mEndUs = endUs;
            mSourceText = sourceText;
            mUnsplit = unsplit;
        }

        public String getSegmentId() {
            return mSegmentId;
        }

        public List<String> getMemberItemIds() {
            return mMemberItemIds;
        }

        public long getStartUs() {
            return mStartUs;
        }

        public long getEndUs() {
            return mEndUs;
        }

        public String getSourceText() {
            return mSourceText;
        }

        /** True when a single over-long item had no reliable inner boundary and stayed whole. */
        public boolean isUnsplit() {
            return mUnsplit;
        }
    }

    /** The timeline to consume plus the counts the diagnostics report. */
    public static final class Result {
        private final SubtitleTimeline mTimeline;
        private final List<Segment> mSegments;
        private final int mLongUnsplit;
        private final int mHardBoundaries;
        private final String mFallback;
        private final int mRuleVersion;

        private Result(SubtitleTimeline timeline, List<Segment> segments, int longUnsplit,
                       int hardBoundaries, String fallback) {
            mTimeline = timeline;
            mSegments = Collections.unmodifiableList(new ArrayList<>(segments));
            mLongUnsplit = longUnsplit;
            mHardBoundaries = hardBoundaries;
            mFallback = fallback;
            mRuleVersion = fallback == null ? RULE_VERSION : 0;
        }

        /** True when the derived timeline may replace the raw one. */
        public boolean isDerived() {
            return mFallback == null && !mSegments.isEmpty();
        }

        /** The timeline to consume: the derived one, or the raw snapshot on a fallback. */
        public SubtitleTimeline getTimeline() {
            return mTimeline;
        }

        public List<Segment> getSegments() {
            return mSegments;
        }

        public int getSegmentCount() {
            return mSegments.size();
        }

        /** Items that stayed whole because a single native item had no reliable inner boundary. */
        public int getLongUnsplit() {
            return mLongUnsplit;
        }

        /** Hard boundaries: clearing frame, speaker change, non-speech, multi-slot, unknown end. */
        public int getHardBoundaries() {
            return mHardBoundaries;
        }

        /** Small fallback reason, or null when the derived timeline is usable. */
        public String getFallback() {
            return mFallback;
        }

        public int getRuleVersion() {
            return mRuleVersion;
        }
    }

    /**
     * @param sourceLanguage language code of the selected track; the joining rule also inspects the
     *                       script itself, so a wrong or missing code cannot break the text
     * @return the derived result, or a fallback result that carries the raw timeline
     */
    public Result segment(SubtitleTimeline raw, String sourceLanguage) {
        if (raw == null || raw.isEmpty()) {
            return new Result(raw, Collections.<Segment>emptyList(), 0, 0, FAILURE_NO_FRAMES);
        }

        List<Entry> entries = extractEntries(raw);
        List<Segment> segments = new ArrayList<>();
        int hardBoundaries = 0;
        List<Entry> current = new ArrayList<>();

        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);

            if (entry.mPassthrough) {
                if (!current.isEmpty()) {
                    segments.add(closeSegment(current, raw));
                    current = new ArrayList<>();
                }

                hardBoundaries++;
                continue; // the raw frame of this region is copied into the derived timeline
            }

            if (current.isEmpty()) {
                current.add(entry);

                if (entry.isHardBoundary()) {
                    hardBoundaries++;
                    segments.add(closeSegment(current, raw));
                    current = new ArrayList<>();
                }

                continue;
            }

            Entry previous = current.get(current.size() - 1);
            boolean pause = entry.mStartUs - previous.mEndUs > PAUSE_BOUNDARY_US;
            boolean sentenceEnd = endsSentence(previous.mText);
            boolean tooLong = exceedsLength(current, entry)
                    || entry.mEndUs - current.get(0).mStartUs > MAX_DURATION_US;
            boolean boundary = entry.mHardBoundary || entry.isHardBoundary() || pause || sentenceEnd || tooLong;

            if (boundary) {
                if (entry.mHardBoundary || entry.isHardBoundary()) {
                    hardBoundaries++;
                }

                segments.add(closeSegment(current, raw));
                current = new ArrayList<>();
            }

            current.add(entry);

            if (entry.isHardBoundary()) {
                // A speaker change or a non-speech marker is a whole sentence: nothing may be merged
                // onto it either.
                hardBoundaries++;
                segments.add(closeSegment(current, raw));
                current = new ArrayList<>();
            }
        }

        if (!current.isEmpty()) {
            segments.add(closeSegment(current, raw));
        }

        int longUnsplit = 0;

        for (Segment segment : segments) {
            if (segment.isUnsplit()) {
                longUnsplit++;
            }
        }

        List<SubtitleFrame> frames = buildFrames(raw, segments);

        if (!isUsable(segments, frames)) {
            return new Result(raw, segments, longUnsplit, hardBoundaries, FAILURE_INVALID);
        }

        String fingerprint = raw.getContentFingerprint() + "-r" + RULE_VERSION;

        return new Result(new SubtitleTimeline(frames, fingerprint), segments, longUnsplit,
                hardBoundaries, null);
    }

    /** One distinct native item: stable id, text and the span of the frames that carried it. */
    private static final class Entry {
        private final String mItemId;
        private final String mText;
        private final long mStartUs;
        private long mEndUs;
        private final boolean mHardBoundary;
        /** A region the rules do not segment: its raw frame stays in the derived timeline as it is. */
        private final boolean mPassthrough;

        private Entry(String itemId, String text, long startUs, long endUs, boolean hardBoundary,
                      boolean passthrough) {
            mItemId = itemId;
            mText = text;
            mStartUs = startUs;
            mEndUs = endUs;
            mHardBoundary = hardBoundary;
            mPassthrough = passthrough;
        }

        private boolean isHardBoundary() {
            return mHardBoundary || isNonSpeech(mText);
        }
    }

    /**
     * Distinct items in time order, one per stable id, with the span of the frames that showed them.
     * A clearing frame, an empty text or an overlapping frame marks the next entry as a boundary, so
     * a derived sentence can never cover a region that had no text.
     */
    private static List<Entry> extractEntries(SubtitleTimeline raw) {
        List<Entry> entries = new ArrayList<>();
        Map<String, Integer> positions = new HashMap<>();
        boolean boundaryBefore = false;

        for (SubtitleFrame frame : raw.getFrames()) {
            if (frame.isEmpty()) {
                boundaryBefore = true;
                continue;
            }

            // Overlapping (multi-slot) cues and an unknown end time cannot be merged safely, so the
            // snapshot's own frame is kept for that region instead of failing the whole timeline.
            boolean passthrough = frame.getItems().size() > 1 || frame.getEndUs() == C.TIME_UNSET;

            for (SubtitleItem item : frame.getItems()) {
                String text = item.getText();

                if (text == null || text.trim().isEmpty()) {
                    boundaryBefore = true;
                    continue;
                }

                Integer at = positions.get(item.getItemId());

                if (at != null) {
                    entries.get(at).mEndUs = frame.getEndUs();
                    continue;
                }

                entries.add(new Entry(item.getItemId(), text.trim(), frame.getStartUs(), frame.getEndUs(),
                        boundaryBefore, passthrough));
                positions.put(item.getItemId(), entries.size() - 1);
                boundaryBefore = false;
            }

            if (passthrough) {
                boundaryBefore = true; // nothing may merge across a region that keeps its raw frame
            }
        }

        return entries;
    }

    private static Segment closeSegment(List<Entry> members, SubtitleTimeline raw) {
        Entry first = members.get(0);
        Entry last = members.get(members.size() - 1);
        List<String> ids = new ArrayList<>(members.size());
        boolean cjk = false;

        for (Entry member : members) {
            ids.add(member.mItemId);

            if (usesCjkScript(member.mText)) {
                cjk = true;
            }
        }

        StringBuilder text = new StringBuilder();
        int codePoints = 0;

        for (int i = 0; i < members.size(); i++) {
            if (i > 0 && !cjk) {
                text.append(' ');
            }

            text.append(members.get(i).mText);
            codePoints += SubtitleBatch.codePoints(members.get(i).mText);
        }

        int cap = cjk ? MAX_CJK_CODE_POINTS : MAX_SPACE_CODE_POINTS;
        boolean unsplit = members.size() == 1
                && (codePoints > cap || last.mEndUs - first.mStartUs > MAX_DURATION_US);
        String segmentId = raw.getContentFingerprint() + "-r" + RULE_VERSION + "-" + first.mItemId
                + "-" + last.mItemId + "-" + members.size();

        return new Segment(segmentId, ids, first.mStartUs, last.mEndUs, text.toString(), unsplit);
    }

    /**
     * Rebuilds the frame sequence for the derived items: one frame per segment, and the original
     * clearing frames everywhere the snapshot had no text, so a sentence never covers a blank region.
     */
    private static List<SubtitleFrame> buildFrames(SubtitleTimeline raw, List<Segment> segments) {
        List<SubtitleFrame> rawFrames = raw.getFrames();
        List<SubtitleFrame> frames = new ArrayList<>();
        int pointer = 0;

        for (Segment segment : segments) {
            while (pointer < rawFrames.size() && rawFrames.get(pointer).getStartUs() < segment.getStartUs()) {
                frames.add(copyOf(rawFrames.get(pointer)));
                pointer++;
            }

            while (pointer < rawFrames.size() && rawFrames.get(pointer).getStartUs() < segment.getEndUs()) {
                pointer++; // the frames of a segment are replaced by the segment's own frame
            }

            SubtitleItem item = new SubtitleItem(segment.getSegmentId(), segment.getSourceText());
            frames.add(new SubtitleFrame(segment.getStartUs(), segment.getEndUs(),
                    Collections.singletonList(item)));
        }

        while (pointer < rawFrames.size()) {
            frames.add(copyOf(rawFrames.get(pointer)));
            pointer++;
        }

        return frames;
    }

    /**
     * A frame the rules do not segment keeps exactly the snapshot's own items (an overlapping cue, an
     * unknown end or a clearing frame), so that region looks the same as with the rule off.
     */
    private static SubtitleFrame copyOf(SubtitleFrame frame) {
        return new SubtitleFrame(frame.getStartUs(), frame.getEndUs(), frame.getItems());
    }

    /**
     * Order and duration of the derived frames. One violation refuses the whole derived timeline,
     * because a half-correct sentence timeline would show the wrong line.
     */
    private static boolean isUsable(List<Segment> segments, List<SubtitleFrame> frames) {
        if (segments.isEmpty() || frames.isEmpty()) {
            return false;
        }

        long previousEnd = Long.MIN_VALUE;

        for (Segment segment : segments) {
            if (segment.getEndUs() <= segment.getStartUs() || segment.getStartUs() < previousEnd) {
                return false;
            }

            previousEnd = segment.getEndUs();
        }

        long previousStart = Long.MIN_VALUE;
        Set<Long> starts = new HashSet<>();

        for (SubtitleFrame frame : frames) {
            if (frame.getStartUs() < previousStart || !starts.add(frame.getStartUs())) {
                return false;
            }

            previousStart = frame.getStartUs();
        }

        return true;
    }

    /** True when the text ends a sentence; a trailing closing quote or bracket still counts. */
    static boolean endsSentence(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        String trimmed = text.trim();
        int index = trimmed.length() - 1;

        while (index >= 0 && CLOSING.indexOf(trimmed.charAt(index)) >= 0) {
            index--;
        }

        if (index < 0) {
            return false;
        }

        char last = trimmed.charAt(index);

        if (SENTENCE_END.indexOf(last) < 0) {
            return false;
        }

        if (last != '.') {
            return true;
        }

        if (index > 0 && Character.isDigit(trimmed.charAt(index - 1))) {
            return false; // a decimal, not a sentence end
        }

        int wordStart = index;

        while (wordStart > 0 && !Character.isWhitespace(trimmed.charAt(wordStart - 1))) {
            wordStart--;
        }

        return !ABBREVIATIONS.contains(trimmed.substring(wordStart, index).toLowerCase());
    }

    /** Music, sound effect or a speaker change marker: kept on screen, never merged. */
    static boolean isNonSpeech(String text) {
        if (text == null) {
            return false;
        }

        String trimmed = text.trim();

        return (trimmed.startsWith("[") && trimmed.endsWith("]"))
                || (trimmed.startsWith("(") && trimmed.endsWith(")"))
                || trimmed.startsWith("\u266a") || trimmed.startsWith("\u266b")
                || trimmed.startsWith("-") || trimmed.startsWith("\u2014");
    }

    /** Han, Kana and CJK punctuation: no space between parts and the shorter length cap. */
    static boolean usesCjkScript(String text) {
        if (text == null) {
            return false;
        }

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if ((c >= 0x3040 && c <= 0x30FF) || (c >= 0x3400 && c <= 0x4DBF)
                    || (c >= 0x4E00 && c <= 0x9FFF) || (c >= 0xF900 && c <= 0xFAFF)
                    || (c >= 0x3000 && c <= 0x303F) || (c >= 0xFF00 && c <= 0xFFEF)) {
                return true;
            }
        }

        return false;
    }

    private static boolean exceedsLength(List<Entry> members, Entry candidate) {
        boolean cjk = usesCjkScript(candidate.mText);
        int codePoints = SubtitleBatch.codePoints(candidate.mText);
        int words = words(candidate.mText);

        for (Entry member : members) {
            if (!cjk && usesCjkScript(member.mText)) {
                cjk = true;
            }

            codePoints += SubtitleBatch.codePoints(member.mText);
            words += words(member.mText);
        }

        if (cjk) {
            return codePoints > MAX_CJK_CODE_POINTS;
        }

        return codePoints > MAX_SPACE_CODE_POINTS || words > MAX_SPACE_WORDS;
    }

    private static int words(String text) {
        if (text == null) {
            return 0;
        }

        String trimmed = text.trim();

        return trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
    }
}
