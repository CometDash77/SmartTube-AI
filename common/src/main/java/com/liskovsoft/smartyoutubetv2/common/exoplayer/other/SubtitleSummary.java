package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One bounded video-summary result of the {@code VIDEO_ENHANCED} context tier (plan 4.2).
 *
 * <p>The result is deliberately tiny and closed: a short topic and a bounded terminology list. It is
 * reference data for the translator, never an instruction, and it never carries anything the model
 * invented about people (no gender or identity inference).
 */
public final class SubtitleSummary {
    public static final int MAX_TOPIC_CODE_POINTS = 300;
    public static final int MAX_TERMS = 12;
    public static final int MAX_CODE_POINTS = 800;

    private final String mTopic;
    private final List<SubtitleTextPair> mTerms;

    private SubtitleSummary(String topic, List<SubtitleTextPair> terms) {
        mTopic = topic;
        mTerms = Collections.unmodifiableList(new ArrayList<>(terms));
    }

    /**
     * Builds a bounded summary, applying the plan's caps in one place (topic length, term count and
     * the total code-point budget). Terms are dropped before the topic, because the topic carries the
     * subject of the video while a term is an optional hint.
     *
     * @return the bounded summary, or null when nothing usable remains
     */
    public static SubtitleSummary of(String topic, List<SubtitleTextPair> terms) {
        String boundedTopic = cap(topic, MAX_TOPIC_CODE_POINTS);
        List<SubtitleTextPair> boundedTerms = new ArrayList<>();
        int codePoints = boundedTopic != null ? SubtitleBatch.codePoints(boundedTopic) : 0;

        if (terms != null) {
            for (SubtitleTextPair term : terms) {
                if (boundedTerms.size() >= MAX_TERMS || term == null || !term.isUsable()) {
                    continue;
                }

                int termCodePoints = term.codePoints();

                if (codePoints + termCodePoints > MAX_CODE_POINTS) {
                    continue;
                }

                boundedTerms.add(term);
                codePoints += termCodePoints;
            }
        }

        if (boundedTopic == null && boundedTerms.isEmpty()) {
            return null;
        }

        return new SubtitleSummary(boundedTopic, boundedTerms);
    }

    /** Short topic of the video, or null when the summary had none. */
    public String getTopic() {
        return mTopic;
    }

    /** Bounded terminology hints, possibly empty. */
    public List<SubtitleTextPair> getTerms() {
        return mTerms;
    }

    public boolean isEmpty() {
        return (mTopic == null || mTopic.isEmpty()) && mTerms.isEmpty();
    }

    public int codePoints() {
        int codePoints = mTopic != null ? SubtitleBatch.codePoints(mTopic) : 0;

        for (SubtitleTextPair term : mTerms) {
            codePoints += term.codePoints();
        }

        return codePoints;
    }

    @Override
    public String toString() {
        return "SubtitleSummary{topic=" + (mTopic != null ? mTopic.length() : 0)
                + " chars, terms=" + mTerms.size() + "}";
    }

    private static String cap(String text, int maxCodePoints) {
        if (text == null) {
            return null;
        }

        String trimmed = text.trim();

        if (trimmed.isEmpty()) {
            return null;
        }

        if (trimmed.codePointCount(0, trimmed.length()) <= maxCodePoints) {
            return trimmed;
        }

        return trimmed.substring(0, trimmed.offsetByCodePoints(0, maxCodePoints));
    }
}
