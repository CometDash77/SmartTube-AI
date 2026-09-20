package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** T07 slice: the response bound refuses rather than truncates. */
public class SubtitleResponseReaderTest {
    private static byte[] bytes(int length) {
        byte[] data = new byte[length];

        for (int i = 0; i < length; i++) {
            data[i] = 'x';
        }

        return data;
    }

    @Test
    public void bodyWithinTheBoundIsReadCompletely() throws Exception {
        String body = "{\"items\":[]}";

        SubtitleResponseReader.Result result = SubtitleResponseReader.read(
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));

        assertTrue(result.isUsable());
        assertEquals(body, result.getBody());
        assertFalse(result.isTooLarge());
    }

    @Test
    public void exactlyTheBoundIsAccepted() throws Exception {
        SubtitleResponseReader.Result result = SubtitleResponseReader.read(
                new ByteArrayInputStream(bytes(SubtitleResponseReader.MAX_RESPONSE_BYTES)));

        assertTrue(result.isUsable());
        assertEquals(SubtitleResponseReader.MAX_RESPONSE_BYTES, result.getBody().length());
    }

    @Test
    public void oneByteOverTheBoundIsRefusedWithoutTruncating() throws Exception {
        SubtitleResponseReader.Result result = SubtitleResponseReader.read(
                new ByteArrayInputStream(bytes(SubtitleResponseReader.MAX_RESPONSE_BYTES + 1)));

        assertTrue(result.isTooLarge());
        assertFalse(result.isUsable());
        assertNull(result.getBody());
    }

    @Test
    public void emptyBodyIsEmptyNotNull() throws Exception {
        SubtitleResponseReader.Result result = SubtitleResponseReader.read(new ByteArrayInputStream(new byte[0]));

        assertEquals("", result.getBody());
        assertFalse(result.isTooLarge());
    }

    @Test
    public void nullStreamIsSafe() throws Exception {
        SubtitleResponseReader.Result result = SubtitleResponseReader.read(null);

        assertFalse(result.isUsable());
        assertNull(result.getBody());
    }

    @Test
    public void ioFailurePropagatesToTheCaller() {
        java.io.InputStream failing = new java.io.InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("broken stream");
            }
        };

        try {
            SubtitleResponseReader.read(failing);
            org.junit.Assert.fail("expected an IOException");
        } catch (IOException expected) {
            // the caller maps this to a transport failure and the batch may be retried once
        }
    }
}
