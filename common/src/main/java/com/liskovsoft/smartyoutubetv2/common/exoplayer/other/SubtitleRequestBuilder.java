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
    public static final String KEY_ITEMS = "items";
    public static final String KEY_TEXT = "text";
    public static final String KEY_ID = "id";
    /** Output bound of plan 6.1; the transport additionally bounds the response to 256 KiB. */
    public static final int MAX_OUTPUT_TOKENS = 4_096;

    private SubtitleRequestBuilder() {
    }

    public static String buildPayload(String sourceLanguage, String targetLanguage, SubtitleBatch batch)
            throws JSONException {
        JSONObject root = new JSONObject();
        root.put(KEY_SOURCE_LANGUAGE, sourceLanguage);
        root.put(KEY_TARGET_LANGUAGE, targetLanguage);
        root.put(KEY_CONTEXT_BEFORE, toContextArray(batch != null ? batch.getContextBefore() : null));
        root.put(KEY_CONTEXT_AFTER, toContextArray(batch != null ? batch.getContextAfter() : null));

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
     * Wraps the payload into the Chat Completions request of plan 6.1: the fixed model, a system
     * message carrying the program-owned instruction, the payload as the user message, no streaming,
     * thinking disabled and JSON output requested. Strict validation still happens locally.
     */
    public static String buildChatCompletionsBody(SubtitleTranslationConfig config, String systemInstruction,
                                                 String payload, String userInstruction) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("model", config != null ? config.getModel() : SubtitleTranslationConfig.DEFAULT_MODEL);
        root.put("stream", false);
        root.put("max_tokens", MAX_OUTPUT_TOKENS);

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
