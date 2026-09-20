package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Strictly validates the fixed translation response before anything can be shown (plan 6.2).
 *
 * <p>A damaged or truncated response fails the whole batch; per-entry problems (missing, non-string,
 * blank or over-long translation) and duplicated ids only invalidate their own entry, and ids that
 * were not requested are dropped. The failure reason is a small code, never the response body.
 */
public final class SubtitleResponseParser {
    /** Per-entry sanity cap; an over-long translation is dropped instead of half-accepted. */
    public static final int MAX_TRANSLATION_CODE_POINTS = 4_000;

    public static final String FAILURE_EMPTY = "empty";
    public static final String FAILURE_TRUNCATED = "truncated";
    public static final String FAILURE_MALFORMED = "malformed";
    public static final String FAILURE_NO_ITEMS = "no_items";
    public static final String FAILURE_NO_TRANSLATIONS = "no_translations";

    public static final class Result {
        private final Map<String, String> mTranslations;
        private final String mFailure;

        Result(Map<String, String> translations, String failure) {
            mTranslations = Collections.unmodifiableMap(translations);
            mFailure = failure;
        }

        /** Valid translations by requested id; empty when the batch failed. */
        public Map<String, String> getTranslations() {
            return mTranslations;
        }

        public boolean isBatchFailed() {
            return mFailure != null;
        }

        /** Small failure code, or null on success. */
        public String getFailure() {
            return mFailure;
        }

        @Override
        public String toString() {
            return "Result{translations=" + mTranslations.size() + ", failure=" + mFailure + "}";
        }
    }

    private SubtitleResponseParser() {
    }

    /**
     * @param body         raw response text
     * @param truncated    {@code finish_reason == length} was reported
     * @param requestedIds ids of the batch that was sent
     */
    public static Result parse(String body, boolean truncated, Collection<String> requestedIds) {
        if (truncated) {
            return new Result(Collections.<String, String>emptyMap(), FAILURE_TRUNCATED);
        }

        if (body == null || body.trim().isEmpty()) {
            return new Result(Collections.<String, String>emptyMap(), FAILURE_EMPTY);
        }

        JSONArray items;

        try {
            JSONObject root = new JSONObject(body);
            // Chat Completions wraps the model's JSON in message.content. The old parser
            // treated every real API response as missing items; fixtures only supplied content.
            if (root.has("choices")) {
                JSONObject choice = root.getJSONArray("choices").getJSONObject(0);
                if ("length".equals(choice.optString("finish_reason"))) {
                    return new Result(Collections.<String, String>emptyMap(), FAILURE_TRUNCATED);
                }
                if (!"stop".equals(choice.optString("finish_reason"))) {
                    return new Result(Collections.<String, String>emptyMap(), FAILURE_MALFORMED);
                }
                Object content = choice.getJSONObject("message").opt("content");
                if (!(content instanceof String)) {
                    return new Result(Collections.<String, String>emptyMap(), FAILURE_EMPTY);
                }
                root = new JSONObject((String) content);
            }
            items = root.optJSONArray("items");
        } catch (JSONException e) {
            return new Result(Collections.<String, String>emptyMap(), FAILURE_MALFORMED);
        }

        if (items == null) {
            return new Result(Collections.<String, String>emptyMap(), FAILURE_NO_ITEMS);
        }

        Set<String> requested = requestedIds == null ? Collections.<String>emptySet() : new HashSet<>(requestedIds);
        Map<String, Integer> occurrences = new HashMap<>();
        Map<String, String> candidates = new LinkedHashMap<>();

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);

            if (item == null) {
                continue;
            }

            String id = item.optString("id", null);

            if (id == null || !requested.contains(id)) {
                continue; // unknown id: dropped, never guessed onto another entry
            }

            occurrences.put(id, occurrences.containsKey(id) ? occurrences.get(id) + 1 : 1);

            Object raw = item.opt("translation");

            if (!(raw instanceof String)) {
                continue;
            }

            String translation = (String) raw;

            if (translation.trim().isEmpty() || translation.codePointCount(0, translation.length()) > MAX_TRANSLATION_CODE_POINTS) {
                continue;
            }

            candidates.put(id, translation);
        }

        Map<String, String> translations = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : candidates.entrySet()) {
            // A duplicated id is void: the service broke the id protocol for that entry.
            if (occurrences.get(entry.getKey()) == 1) {
                translations.put(entry.getKey(), entry.getValue());
            }
        }

        return new Result(translations, !requested.isEmpty() && translations.isEmpty()
                ? FAILURE_NO_TRANSLATIONS : null);
    }
}
