package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * OkHttp implementation of the transport seam (plan 6.1).
 *
 * <p>The client is built from a clean builder: no body or profiler interceptor, no implicit retry and
 * neither redirects nor SSL redirects are followed, so a key can never reach another host. Timeouts
 * are the plan's initial values. The response body is read through {@link SubtitleResponseReader}, so
 * the 256 KiB bound applies before any parsing.
 */
public class SubtitleOkHttpTranslationClient implements SubtitleTranslationClient {
    public static final long CONNECT_TIMEOUT_MS = 5_000;
    public static final long READ_TIMEOUT_MS = 15_000;
    public static final long CALL_TIMEOUT_MS = 20_000;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static OkHttpClient sClient;

    private final OkHttpClient mClient;

    public SubtitleOkHttpTranslationClient() {
        this(sharedClient());
    }

    SubtitleOkHttpTranslationClient(OkHttpClient client) {
        mClient = client;
    }

    private static synchronized OkHttpClient sharedClient() {
        if (sClient == null) {
            sClient = new OkHttpClient.Builder()
                    .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .callTimeout(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .retryOnConnectionFailure(false)
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build();
        }

        return sClient;
    }

    @Override
    public Cancellable send(final SubtitleTranslationRequest request, final String systemInstruction,
                            final ResponseHandler handler) {
        if (request == null || handler == null) {
            if (handler != null) {
                handler.onTransportFailure();
            }

            return () -> { };
        }

        final Call call = mClient.newCall(buildRequest(request));

        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call failedCall, IOException e) {
                handler.onTransportFailure();
            }

            @Override
            public void onResponse(Call respondedCall, Response response) {
                try (ResponseBody body = response.body()) {
                    SubtitleResponseReader.Result read = SubtitleResponseReader.read(body != null ? body.byteStream() : null);

                    if (read.isTooLarge()) {
                        handler.onTransportFailure(); // an over-sized body is a refused attempt, not a partial answer
                        return;
                    }

                    handler.onResponse(response.code(), parseRetryAfterMs(response.header("Retry-After")),
                            isTruncated(read.getBody()), read.getBody());
                } catch (IOException e) {
                    handler.onTransportFailure();
                }
            }
        });

        return call::cancel;
    }

    /** Package-visible so the wire shape can be asserted without any network access. */
    static Request buildRequest(SubtitleTranslationRequest request) {
        return new Request.Builder()
                .url(request.getUrl())
                .header("Authorization", request.getAuthorization())
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .post(RequestBody.create(JSON, request.getBody()))
                .build();
    }

    /**
     * The service reports a length-truncated completion inside the JSON body. Detecting it here would
     * require parsing twice, so the transport only forwards the raw flag when the body itself says so.
     */
    static boolean isTruncated(String body) {
        return body != null && body.contains("\"finish_reason\":\"length\"");
    }

    /** {@code Retry-After} is either a delay in seconds or an HTTP date; only seconds are honoured. */
    static long parseRetryAfterMs(String headerValue) {
        if (headerValue == null) {
            return -1;
        }

        try {
            return Long.parseLong(headerValue.trim()) * 1_000L;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
