package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** T10 slice: the reader consumes an ExoPlayer data source without changing the request. */
@RunWith(RobolectricTestRunner.class)
public class SubtitleDataSourceInputStreamTest {
    private static class FakeDataSource implements DataSource {
        private final byte[] data;
        private int position;
        int opens;
        int closes;
        String lastUrl;
        private boolean openThrows;

        FakeDataSource(String text) {
            data = text.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public long open(DataSpec dataSpec) throws IOException {
            opens++;
            lastUrl = dataSpec.uri.toString();

            if (openThrows) {
                throw new IOException("open failed");
            }

            return data.length;
        }

        @Override
        public int read(byte[] buffer, int offset, int readLength) {
            if (position >= data.length) {
                return -1;
            }

            int length = Math.min(readLength, data.length - position);
            System.arraycopy(data, position, buffer, offset, length);
            position += length;

            return length;
        }

        @Override
        public android.net.Uri getUri() {
            return null;
        }

        @Override
        public void close() {
            closes++;
        }

        @Override
        public void addTransferListener(TransferListener transferListener) {
        }
    }

    private static String readAll(SubtitleDataSourceInputStream stream, int chunk) throws IOException {
        StringBuilder text = new StringBuilder();
        byte[] buffer = new byte[chunk];
        int read;

        while ((read = stream.read(buffer, 0, buffer.length)) != -1) {
            text.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
        }

        return text.toString();
    }

    @Test
    public void thePayloadIsReadCompletelyAcrossChunks() throws Exception {
        FakeDataSource source = new FakeDataSource("WEBVTT\n\nhello");

        try (SubtitleDataSourceInputStream stream = new SubtitleDataSourceInputStream(source, "https://example.com/subs")) {
            assertEquals("WEBVTT\n\nhello", readAll(stream, 4));
        }

        assertEquals("the exact selected URL is opened", "https://example.com/subs", source.lastUrl);
        assertEquals(1, source.opens);
        assertEquals(1, source.closes);
    }

    @Test
    public void theSourceIsOpenedLazilyAndOnlyOnce() throws Exception {
        FakeDataSource source = new FakeDataSource("ab");

        try (SubtitleDataSourceInputStream stream = new SubtitleDataSourceInputStream(source, "https://example.com/subs")) {
            assertEquals(0, source.opens);
            assertEquals('a', stream.read());
            assertEquals(1, source.opens);
            assertEquals('b', stream.read());
            assertEquals(1, source.opens);
        }
    }

    @Test
    public void readingPastTheEndReturnsMinusOneAndStaysThere() throws Exception {
        FakeDataSource source = new FakeDataSource("");
        SubtitleDataSourceInputStream stream = new SubtitleDataSourceInputStream(source, "https://example.com/subs");

        assertEquals(-1, stream.read(new byte[8], 0, 8));
        assertEquals(-1, stream.read(new byte[8], 0, 8));
        assertEquals(0, stream.available());

        stream.close();
    }

    @Test
    public void closingTwiceReleasesTheSourceOnce() throws Exception {
        FakeDataSource source = new FakeDataSource("x");
        SubtitleDataSourceInputStream stream = new SubtitleDataSourceInputStream(source, "https://example.com/subs");
        stream.read();

        stream.close();
        stream.close();

        assertEquals(1, source.closes);
        try {
            stream.read();
            org.junit.Assert.fail("a closed stream must not read");
        } catch (IOException expected) {
            // expected: the reader must not be able to use a released source
        }
    }

    @Test
    public void aMissingSourceIsAnEmptyStream() throws Exception {
        try (SubtitleDataSourceInputStream stream = new SubtitleDataSourceInputStream(null, "https://example.com/subs")) {
            assertEquals(-1, stream.read());
            assertFalse(stream.available() == 1);
        }
    }

    @Test
    public void anOpenFailurePropagatesAsAnIoException() {
        FakeDataSource source = new FakeDataSource("data");
        source.openThrows = true;

        try (SubtitleDataSourceInputStream stream = new SubtitleDataSourceInputStream(source, "https://example.com/subs")) {
            stream.read();
            org.junit.Assert.fail("expected an IOException");
        } catch (IOException expected) {
            assertTrue(true);
        }
    }
}
