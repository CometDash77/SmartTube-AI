package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Strictly validates the one-shot context analysis before it can become session context (plan 4.2).
 *
 * <p>The fixed schema is {@code {"topic":"...","terms":[{"source":"...","translation":"..."}]}}. A
 * truncated, over-sized, malformed or empty answer is refused as a whole: a damaged summary must fall
 * back to the coherent tier instead of half-entering the context. The failure reason is a small code,
 * never the response body.
 */
public final class SubtitleSummaryParser {
    public static final String FAILURE_EMPTY = "empty";
    public static final String FAILURE_TRUNCATED = "truncated";
    public static final String FAILURE_MALFORMED = "malformed";
    public static final String FAILURE_TOO_LARGE = "too_large";
    /** Plan 4.2 bounds the summary response; the comparison is over characters, not encoded bytes. */
    public static final int MAX_RESPONSE_CHARS = 16 * 1_024;

    public static final class Result {
        private final SubtitleSummary mSummary;
        private final String mFailure;

        Result(SubtitleSummary summary, String failure) {
            mSummary = summary;
            mFailure = failure;
        }

        /** The bounded summary, or null when the answer was refused. */
        public SubtitleSummary getSummary() {
            return mSummary;
        }

        public boolean isFailed() {
            return mFailure != null;
        }

        /** Small failure code, or null on success. */
        public String getFailure() {
            return mFailure;
        }
    }

    private SubtitleSummaryParser() {
    }

    public static Result parse(String body, boolean truncated) {
        if (truncated) {
            return new Result(null, FAILURE_TRUNCATED);
        }

        if (body == null || body.trim().isEmpty()) {
            return new Result(null, FAILURE_EMPTY);
        }

        if (body.length() > MAX_RESPONSE_CHARS) {
            return new Result(null, FAILURE_TOO_LARGE);
        }

        JSONObject root;

        try {
            root = new JSONObject(body);

            // Chat Completions wraps the model's JSON in message.content, exactly like a translation.
            if (root.has("choices")) {
                JSONObject choice = root.getJSONArray("choices").getJSONObject(0);
                String finish = choice.optString("finish_reason");

                if ("length".equals(finish)) {
                    return new Result(null, FAILURE_TRUNCATED);
                }

                if (!"stop".equals(finish)) {
                    return new Result(null, FAILURE_MALFORMED);
                }

                Object content = choice.getJSONObject("message").opt("content");

                if (!(content instanceof String)) {
                    return new Result(null, FAILURE_EMPTY);
                }

                root = new JSONObject((String) content);
            }
        } catch (JSONException e) {
            return new Result(null, FAILURE_MALFORMED);
        }

        List<SubtitleTextPair> terms = new ArrayList<>();
        JSONArray rawTerms = root.optJSONArray("terms");

        if (rawTerms != null) {
            for (int i = 0; i < rawTerms.length(); i++) {
                JSONObject term = rawTerms.optJSONObject(i);

                if (term == null) {
                    continue;
                }

                SubtitleTextPair pair = new SubtitleTextPair(term.optString("source", null),
                        term.optString("translation", null));

                if (pair.isUsable()) {
                    terms.add(pair);
                }
            }
        }

        SubtitleSummary summary = SubtitleSummary.of(root.optString("topic", null), terms);

        return summary == null ? new Result(null, FAILURE_MALFORMED) : new Result(summary, null);
    }
}
