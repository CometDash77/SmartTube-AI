package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Builds the fixed request payload of the translation protocol (plan 6.2).
 *
 * <p>The payload is produced with a JSON serializer, never by string concatenation, so subtitle text
 * containing quotes, newlines or control characters cannot break the protocol. The timeline stays
 * local: only text and stable ids leave the device.
 */
public final class SubtitleRequestBuilder {
    public static final String KEY_SOURCE_LANGUAGE = "sourceLanguage";
    public static final String KEY_TARGET_LANGUAGE = "targetLanguage";
    public static final String KEY_CONTEXT_BEFORE = "contextBefore";
    public static final String KEY_CONTEXT_AFTER = "contextAfter";
    public static final String KEY_TRANSLATION_EXAMPLES = "translationExamples";
    public static final String KEY_VIDEO_CONTEXT = "videoContext";
    public static final String KEY_TOPIC = "topic";
    public static final String KEY_TERMS = "terms";
    public static final String KEY_ITEMS = "items";
    public static final String KEY_TEXT = "text";
    public static final String KEY_ID = "id";
    public static final String KEY_SOURCE = "source";
    public static final String KEY_TRANSLATION = "translation";
    /** Output bound of plan 6.1; the transport additionally bounds the response to 256 KiB. */
    public static final int MAX_OUTPUT_TOKENS = 4_096;
    /** Output bound of the one-shot context analysis (plan 4.2). */
    public static final int MAX_SUMMARY_OUTPUT_TOKENS = 1_024;
    public static final String KEY_TITLE = "title";
    public static final String KEY_DESCRIPTION = "description";
    public static final String KEY_SAMPLES = "samples";

    private SubtitleRequestBuilder() {
    }

    public static String buildPayload(String sourceLanguage, String targetLanguage, SubtitleBatch batch)
            throws JSONException {
        return buildPayload(sourceLanguage, targetLanguage, batch, SubtitleAiSettings.CONTEXT_BASIC);
    }

    /**
     * Builds the payload of the selected context tier (plan 4.2): every tier sends the neighbouring
     * lines, the coherent tier adds the session's verified translation examples and the enhanced tier
     * adds the frozen video context. Reference fields are only present when the tier allows them and
     * when they carry usable data, so a basic request never grows and a missing summary cannot turn
     * into an empty field.
     */
    public static String buildPayload(String sourceLanguage, String targetLanguage, SubtitleBatch batch,
                                      int contextTier) throws JSONException {
        JSONObject root = new JSONObject();
        root.put(KEY_SOURCE_LANGUAGE, sourceLanguage);
        root.put(KEY_TARGET_LANGUAGE, targetLanguage);
        root.put(KEY_CONTEXT_BEFORE, toContextArray(batch != null ? batch.getContextBefore() : null));
        root.put(KEY_CONTEXT_AFTER, toContextArray(batch != null ? batch.getContextAfter() : null));
        SubtitleContext context = batch != null ? batch.getContext() : null;

        if (contextTier >= SubtitleAiSettings.CONTEXT_COHERENT && context != null) {
            JSONArray examples = new JSONArray();

            for (SubtitleTextPair example : context.getExamples()) {
                if (example.isUsable()) {
                    examples.put(toPair(example));
                }
            }

            if (examples.length() > 0) {
                root.put(KEY_TRANSLATION_EXAMPLES, examples);
            }
        }

        if (contextTier >= SubtitleAiSettings.CONTEXT_VIDEO_ENHANCED && context != null) {
            SubtitleSummary summary = context.getSummary();

            if (summary != null && !summary.isEmpty()) {
                root.put(KEY_VIDEO_CONTEXT, toSummary(summary));
            }
        }

        JSONArray items = new JSONArray();

        if (batch != null) {
            for (SubtitleItem item : batch.getItems()) {
                JSONObject entry = new JSONObject();
                entry.put(KEY_ID, item.getItemId());
                entry.put(KEY_TEXT, item.getText());
                items.put(entry);
            }
        }

        root.put(KEY_ITEMS, items);

        return root.toString();
    }

    /**
     * Payload of the one-shot context analysis (plan 4.2): bounded metadata plus a bounded sample of
     * the original lines. The caller trims the sample; this method only serializes what it is given.
     */
    public static String buildSummaryPayload(String sourceLanguage, String title, String description,
                                             List<String> samples) throws JSONException {
        JSONObject root = new JSONObject();
        root.put(KEY_SOURCE_LANGUAGE, sourceLanguage);

        if (title != null && !title.trim().isEmpty()) {
            root.put(KEY_TITLE, title.trim());
        }

        if (description != null && !description.trim().isEmpty()) {
            root.put(KEY_DESCRIPTION, description.trim());
        }

        JSONArray array = new JSONArray();

        if (samples != null) {
            for (String sample : samples) {
                if (sample != null && !sample.trim().isEmpty()) {
                    array.put(sample.trim());
                }
            }
        }

        root.put(KEY_SAMPLES, array);

        return root.toString();
    }

    /**
     * Wraps the payload into the Chat Completions request of plan 6.1: the fixed model, a system
     * message carrying the program-owned instruction, the payload as the user message, no streaming,
     * thinking disabled and JSON output requested. Strict validation still happens locally.
     */
    public static String buildChatCompletionsBody(SubtitleTranslationConfig config, String systemInstruction,
                                                 String payload, String userInstruction) throws JSONException {
        return buildChatCompletionsBody(config, systemInstruction, payload, userInstruction, MAX_OUTPUT_TOKENS);
    }

    /**
     * The same fixed Chat Completions envelope with an explicit output bound. The context analysis has
     * its own, smaller bound (plan 4.2) while sharing the transport, the model and the key.
     */
    public static String buildChatCompletionsBody(SubtitleTranslationConfig config, String systemInstruction,
                                                 String payload, String userInstruction,
                                                 int maxOutputTokens) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("model", config != null ? config.getModel() : SubtitleTranslationConfig.DEFAULT_MODEL);
        root.put("stream", false);
        root.put("max_tokens", maxOutputTokens);

        JSONObject thinking = new JSONObject();
        thinking.put("type", "disabled");
        root.put("thinking", thinking);

        JSONObject responseFormat = new JSONObject();
        responseFormat.put("type", "json_object");
        root.put("response_format", responseFormat);

        JSONArray messages = new JSONArray();
        messages.put(message("system", systemInstruction));
        messages.put(message("user", payload));

        if (userInstruction != null && !userInstruction.trim().isEmpty()) {
            // The protocol stays in the system message; the user preference is data as well.
            messages.put(message("user", userInstruction.trim()));
        }

        root.put("messages", messages);

        return root.toString();
    }

    private static JSONObject message(String role, String content) throws JSONException {
        JSONObject message = new JSONObject();
        message.put("role", role);
        message.put("content", content);

        return message;
    }

    private static JSONObject toPair(SubtitleTextPair pair) throws JSONException {
        JSONObject entry = new JSONObject();
        entry.put(KEY_SOURCE, pair.getSource());
        entry.put(KEY_TRANSLATION, pair.getTarget());

        return entry;
    }

    /** The frozen summary as bounded reference data, never as an instruction (plan 4.2). */
    private static JSONObject toSummary(SubtitleSummary summary) throws JSONException {
        JSONObject entry = new JSONObject();

        if (summary.getTopic() != null) {
            entry.put(KEY_TOPIC, summary.getTopic());
        }

        JSONArray terms = new JSONArray();

        for (SubtitleTextPair term : summary.getTerms()) {
            if (term.isUsable()) {
                terms.put(toPair(term));
            }
        }

        entry.put(KEY_TERMS, terms);

        return entry;
    }

    private static JSONArray toContextArray(List<String> texts) throws JSONException {
        JSONArray array = new JSONArray();

        if (texts != null) {
            for (String text : texts) {
                JSONObject entry = new JSONObject();
                entry.put(KEY_TEXT, text);
                array.put(entry);
            }
        }

        return array;
    }
}
