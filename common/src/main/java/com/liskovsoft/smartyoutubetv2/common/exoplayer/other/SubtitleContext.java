package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable reference material of one translation request (plan 4.2): the neighbouring original
 * lines, the verified translation examples and the optional frozen video summary.
 *
 * <p>Everything here is data for the model. The fixed system instruction still owns the output
 * rules, so nothing in this object can add, remove or rename a requested id.
 */
public final class SubtitleContext {
    public static final int MAX_BEFORE = 3;
    public static final int MAX_AFTER = 2;
    public static final int MAX_NEIGHBOUR_CODE_POINTS = 500;
    public static final int MAX_EXAMPLES = 8;
    public static final int MAX_EXAMPLE_CODE_POINTS = 1_000;

    public static final SubtitleContext EMPTY = new SubtitleContext(
            Collections.<String>emptyList(), Collections.<String>emptyList(),
            Collections.<SubtitleTextPair>emptyList(), null);

    private final List<String> mBefore;
    private final List<String> mAfter;
    private final List<SubtitleTextPair> mExamples;
    private final SubtitleSummary mSummary;

    public SubtitleContext(List<String> before, List<String> after, List<SubtitleTextPair> examples,
                           SubtitleSummary summary) {
        mBefore = Collections.unmodifiableList(before == null ? new ArrayList<String>() : new ArrayList<>(before));
        mAfter = Collections.unmodifiableList(after == null ? new ArrayList<String>() : new ArrayList<>(after));
        mExamples = Collections.unmodifiableList(
                examples == null ? new ArrayList<SubtitleTextPair>() : new ArrayList<>(examples));
        mSummary = summary;
    }

    /** Original lines immediately before the batch anchor, in time order. */
    public List<String> getBefore() {
        return mBefore;
    }

    /** Original lines immediately after the batch anchor, in time order. */
    public List<String> getAfter() {
        return mAfter;
    }

    /** Verified original/translation pairs of this session, in time order. */
    public List<SubtitleTextPair> getExamples() {
        return mExamples;
    }

    /** The frozen video summary, or null when this request has none. */
    public SubtitleSummary getSummary() {
        return mSummary;
    }

    public boolean isEmpty() {
        return mBefore.isEmpty() && mAfter.isEmpty() && mExamples.isEmpty() && mSummary == null;
    }

    /** Code points of all context material; part of the request's 6,000 code-point budget. */
    public int codePoints() {
        int codePoints = 0;

        for (String text : mBefore) {
            codePoints += SubtitleBatch.codePoints(text);
        }

        for (String text : mAfter) {
            codePoints += SubtitleBatch.codePoints(text);
        }

        for (SubtitleTextPair example : mExamples) {
            codePoints += example.codePoints();
        }

        if (mSummary != null) {
            codePoints += mSummary.codePoints();
        }

        return codePoints;
    }

    @Override
    public String toString() {
        return "SubtitleContext{before=" + mBefore.size() + ", after=" + mAfter.size()
                + ", examples=" + mExamples.size() + ", summary=" + (mSummary != null) + "}";
    }
}
