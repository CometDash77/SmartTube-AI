package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Adapts the prepared request plus the transport seam to the dispatcher's service contract.
 *
 * <p>Every transport or protocol problem becomes a dispatcher failure, so the retry budget, the
 * failure cooldown and the batch-failure marking stay in one place instead of being re-implemented
 * per transport. A configuration without a usable endpoint or key fails immediately without a call.
 */
public class SubtitleTranslationService implements SubtitleTranslationDispatcher.TranslationService {
    /** Supplies the current configuration; it may change between batches. */
    public interface ConfigProvider {
        SubtitleTranslationConfig getConfig();
    }

    /** Supplies the key only for the duration of a call; it is never stored here. */
    public interface KeyProvider {
        String getApiKey();
    }

    private static final SubtitleTranslationDispatcher.TranslationCall NO_CALL = () -> { };

    private final SubtitleTranslationClient mClient;
    private final ConfigProvider mConfigProvider;
    private final KeyProvider mKeyProvider;
    /** Supplies the language of the track being translated; it may change with the selected track. */
    public interface SourceLanguageProvider {
        String getSourceLanguage();
    }

    private final SourceLanguageProvider mSourceLanguageProvider;
    private final String mUserStyle;
    private long mLastRetryDelayMs;

    public SubtitleTranslationService(SubtitleTranslationClient client, ConfigProvider configProvider,
                                      KeyProvider keyProvider, String sourceLanguage, String userStyle) {
        this(client, configProvider, keyProvider, () -> sourceLanguage, userStyle);
    }

    public SubtitleTranslationService(SubtitleTranslationClient client, ConfigProvider configProvider,
                                      KeyProvider keyProvider, SourceLanguageProvider sourceLanguageProvider, String userStyle) {
        mClient = client;
        mConfigProvider = configProvider;
        mKeyProvider = keyProvider;
        mSourceLanguageProvider = sourceLanguageProvider;
        mUserStyle = userStyle;
    }

    @Override
    public SubtitleTranslationDispatcher.TranslationCall translate(SubtitleBatch batch,
                                                                  SubtitleTranslationDispatcher.Callback callback) {
        SubtitleTranslationConfig config = mConfigProvider != null ? mConfigProvider.getConfig() : null;
        String apiKey = mKeyProvider != null ? mKeyProvider.getApiKey() : null;
        SubtitleTranslationRequest request;

        try {
            String sourceLanguage = mSourceLanguageProvider != null ? mSourceLanguageProvider.getSourceLanguage() : null;
            request = SubtitleTranslationRequest.create(config, apiKey, sourceLanguage, batch);
        } catch (JSONException e) {
            callback.onFailure(batch);
            return NO_CALL;
        }

        if (request == null) {
            callback.onFailure(batch); // no endpoint or no key: never call out
            return NO_CALL;
        }

        SubtitleTranslationClient.Cancellable call = mClient.send(request,
                request.getSystemInstruction(config, mUserStyle), new SubtitleTranslationClient.ResponseHandler() {
                    @Override
                    public void onResponse(int status, long retryAfterMs, boolean truncated, String body) {
                        SubtitleResponseHandler.Outcome outcome = SubtitleResponseHandler.handle(
                                status, retryAfterMs, 0, truncated, body, batch.getItemIds());

                        mLastRetryDelayMs = outcome.isDelivered() ? 0 : outcome.getDelayMs();

                        if (outcome.isDelivered()) {
                            callback.onSuccess(batch, align(batch, outcome.getTranslations()));
                        } else {
                            callback.onFailure(batch);
                        }
                    }

                    @Override
                    public void onTransportFailure() {
                        callback.onFailure(batch);
                    }
                });

        return call != null ? call::cancel : NO_CALL;
    }

    /**
     * Delay of the last failed attempt (for example a rate limit's {@code Retry-After}), or 0 when
     * the last attempt succeeded or needs no wait. The owner of the dispatcher feeds this into
     * {@link SubtitleTranslationDispatcher#pauseFor(long)}.
     */
    public long getLastRetryDelayMs() {
        return mLastRetryDelayMs;
    }

    /** One entry per batch item, after final validation; a missing entry stays null. */
    static List<String> align(SubtitleBatch batch, Map<String, String> translations) {
        List<String> aligned = new ArrayList<>(batch.getItems().size());

        for (SubtitleItem item : batch.getItems()) {
            aligned.add(translations.get(item.getItemId()));
        }

        return aligned;
    }
}
