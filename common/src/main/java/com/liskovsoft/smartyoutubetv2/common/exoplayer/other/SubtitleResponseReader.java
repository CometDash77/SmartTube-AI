package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * Reads a response body with the plan's hard bound (section 6.1, initial value 256 KiB).
 *
 * <p>An over-sized body is refused as a whole instead of being truncated: a half response would be
 * half a JSON document, and the caller must fail the batch rather than accept a partial answer.
 */
public final class SubtitleResponseReader {
    /** API 1 charset: java.nio.charset.StandardCharsets is API 19 while this module supports 17. */
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    public static final int MAX_RESPONSE_BYTES = 256 * 1024;

    public static final class Result {
        private final String mBody;
        private final boolean mTooLarge;

        Result(String body, boolean tooLarge) {
            mBody = body;
            mTooLarge = tooLarge;
        }

        /** Response text, or null when the body was too large. */
        public String getBody() {
            return mBody;
        }

        public boolean isTooLarge() {
            return mTooLarge;
        }

        public boolean isUsable() {
            return !mTooLarge && mBody != null;
        }
    }

    private SubtitleResponseReader() {
    }

    public static Result read(InputStream stream) throws IOException {
        if (stream == null) {
            return new Result(null, false);
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8 * 1024];

        while (true) {
            int read = stream.read(buffer);

            if (read == -1) {
                break;
            }

            if (output.size() + read > MAX_RESPONSE_BYTES) {
                return new Result(null, true);
            }

            output.write(buffer, 0, read);
        }

        return new Result(new String(output.toByteArray(), UTF_8), false);
    }
}
