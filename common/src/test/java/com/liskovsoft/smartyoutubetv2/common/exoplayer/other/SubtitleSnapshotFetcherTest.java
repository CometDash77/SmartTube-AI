package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.MimeTypes;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** T10 slice: one synchronous snapshot fetch with an explicit cancellation check. */
@RunWith(RobolectricTestRunner.class)
public class SubtitleSnapshotFetcherTest {
    private static final String VTT = "WEBVTT\n\n00:00:00.000 --> 00:00:02.000\nHello\n";

    private static Format vttFormat() {
        return Format.createTextSampleFormat("en", MimeTypes.TEXT_VTT, 0, "English");
    }

    private static SelectedSubtitleSource source() {
        return new SelectedSubtitleSource(1, "https://example.com/subs", "en", "en", "English", MimeTypes.TEXT_VTT, null, null, true);
    }

    private static SubtitleSnapshotFetcher fetcher(SubtitleSnapshotFetcher.PayloadFactory factory) {
        return new SubtitleSnapshotFetcher(new SubtitleSnapshotReader(), factory);
    }

    private static SubtitleSnapshotFetcher.PayloadFactory vttFactory() {
        return source -> new ByteArrayInputStream(VTT.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void aSuccessfulFetchReturnsTheTimeline() {
        SubtitleSnapshotFetcher.Result result = fetcher(vttFactory())
                .fetch(source(), vttFormat(), 2_000_000, () -> false);

        assertEquals(SubtitleSnapshotReader.Status.OK, result.getStatus());
        assertTrue(result.isUsable());
        assertEquals("Hello", result.getTimeline().frameAt(0).getTexts().get(0));
    }

    @Test
    public void anUnsupportedFormatIsRefusedWithoutDecoding() {
        Format video = Format.createVideoSampleFormat("v", null, "avc1", -1, -1, 1920, 1080, 30, null, null);

        SubtitleSnapshotFetcher.Result result = fetcher(vttFactory())
                .fetch(source(), video, 2_000_000, () -> false);

        assertEquals(SubtitleSnapshotReader.Status.UNSUPPORTED_FORMAT, result.getStatus());
        assertNull(result.getTimeline());
    }

    @Test
    public void aCancelledFetchDoesNotEvenOpenThePayload() {
        boolean[] opened = {false};
        SubtitleSnapshotFetcher.Result result = fetcher(source -> {
            opened[0] = true;

            return new ByteArrayInputStream(new byte[0]);
        }).fetch(source(), vttFormat(), 2_000_000, () -> true);

        assertEquals(SubtitleSnapshotReader.Status.CANCELLED, result.getStatus());
        assertFalse("cancellation must be honoured before the fetch", opened[0]);
    }

    @Test
    public void aMissingSourceIsReportedAsNoSource() {
        boolean[] opened = {false};
        SubtitleSnapshotFetcher.Result result = fetcher(source -> {
            opened[0] = true;

            return new ByteArrayInputStream(new byte[0]);
        }).fetch(null, vttFormat(), 2_000_000, () -> false);

        assertEquals(SubtitleSnapshotReader.Status.NO_SOURCE, result.getStatus());
        assertFalse(opened[0]);
    }

    @Test
    public void aFailingPayloadFactoryBecomesAnIoFailure() {
        SubtitleSnapshotFetcher.Result result = fetcher(source -> {
            throw new IllegalStateException("no data source");
        }).fetch(source(), vttFormat(), 2_000_000, () -> false);

        assertEquals(SubtitleSnapshotReader.Status.IO_FAILED, result.getStatus());
        assertNull(result.getTimeline());
    }

    @Test
    public void thePayloadIsAlwaysReleased() {
        boolean[] closed = {false};
        SubtitleSnapshotReader reader = new SubtitleSnapshotReader();
        SubtitleSnapshotFetcher fetcher = new SubtitleSnapshotFetcher(reader, source -> new ByteArrayInputStream(VTT.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public void close() throws java.io.IOException {
                closed[0] = true;
                super.close();
            }
        });

        SubtitleSnapshotFetcher.Result result = fetcher.fetch(source(), vttFormat(), 2_000_000, () -> false);

        assertTrue(result.isUsable());
        assertTrue("the stream must be released after the fetch", closed[0]);
    }
}
