package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.SubtitleDecoder;
import com.google.android.exoplayer2.text.SubtitleDecoderException;
import com.google.android.exoplayer2.text.SubtitleDecoderFactory;
import com.google.android.exoplayer2.text.SubtitleInputBuffer;
import com.google.android.exoplayer2.text.SubtitleOutputBuffer;
import com.google.android.exoplayer2.util.MimeTypes;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * T03 acceptance for the background snapshot: the decoder path, its boundaries and its bounded
 * refusals. Robolectric is required because the native VTT decoder uses android.text.
 */
@RunWith(RobolectricTestRunner.class)
public class SubtitleSnapshotReaderTest {
    private static final String VTT = "WEBVTT\n\n"
            + "00:00:00.000 --> 00:00:02.000\nHello\n\n"
            + "00:00:02.000 --> 00:00:04.000\nWorld\n";

    private static Format vttFormat() {
        return Format.createTextSampleFormat("en", MimeTypes.TEXT_VTT, 0, "English");
    }

    private static InputStream payload(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void decodesTheSelectedSourceIntoItsNativeTimeline() {
        SubtitleSnapshotReader.Snapshot snapshot =
                new SubtitleSnapshotReader().read(vttFormat(), payload(VTT), 4_000_000, () -> false);

        assertEquals("expected a usable snapshot", SubtitleSnapshotReader.Status.OK, snapshot.getStatus());
        assertTrue(snapshot.getStatus().name(), snapshot.isUsable());
        assertNotNull(snapshot.getTimeline());
        assertEquals(4, snapshot.getEventCount()); // the VTT decoder reports 4 event times for 2 cues
        assertEquals(Arrays.asList("Hello"), snapshot.getTimeline().frameAt(0).getTexts());
        assertEquals(Arrays.asList("Hello"), snapshot.getTimeline().frameAt(1_999_999).getTexts());
        assertEquals(Arrays.asList("World"), snapshot.getTimeline().frameAt(2_000_000).getTexts());
        assertEquals(Arrays.asList("World"), snapshot.getTimeline().frameAt(3_999_999).getTexts());
        assertEquals(3, snapshot.getTimeline().size());
        assertEquals(VTT.getBytes(StandardCharsets.UTF_8).length, snapshot.getBytes());
    }

    @Test
    public void snapshotBoundariesMatchTheNativeDecoderAtEveryBoundaryProbe() throws Exception {
        SubtitleTimeline timeline = new SubtitleSnapshotReader()
                .read(vttFormat(), payload(VTT), 4_000_000, () -> false).getTimeline();

        long[] probes = {0, 1, 999_999, 2_000_000, 2_000_001, 3_999_999};

        for (long positionUs : probes) {
            assertEquals("position " + positionUs, nativeCuesAt(positionUs), timeline.frameAt(positionUs).getTexts());
        }
    }

    @Test
    public void oversizePayloadIsRefusedInsteadOfTruncated() {
        byte[] tooBig = new byte[(int) SubtitleSnapshotReader.MAX_RAW_BYTES + 1];
        Arrays.fill(tooBig, (byte) 'A');

        SubtitleSnapshotReader.Snapshot snapshot = new SubtitleSnapshotReader()
                .read(vttFormat(), new ByteArrayInputStream(tooBig), C.TIME_UNSET, () -> false);

        assertEquals(SubtitleSnapshotReader.Status.OVERSIZE, snapshot.getStatus());
        assertFalse(snapshot.isUsable());
    }

    @Test
    public void cancelledSnapshotStopsBeforeDecoding() {
        SubtitleSnapshotReader.Snapshot snapshot =
                new SubtitleSnapshotReader().read(vttFormat(), payload(VTT), 4_000_000, () -> true);

        assertEquals(SubtitleSnapshotReader.Status.CANCELLED, snapshot.getStatus());
        assertFalse(snapshot.isUsable());
    }

    @Test
    public void unsupportedFormatIsRefusedWithoutFetching() {
        Format videoFormat = Format.createVideoSampleFormat("v", null, "avc1", -1, -1, 1920, 1080, 30, null, null);

        SubtitleSnapshotReader.Snapshot snapshot =
                new SubtitleSnapshotReader().read(videoFormat, payload(VTT), 4_000_000, () -> false);

        assertEquals(SubtitleSnapshotReader.Status.UNSUPPORTED_FORMAT, snapshot.getStatus());
    }

    /** Independent native path: decode the same bytes and ask the decoder for the cues directly. */
    private static List<String> nativeCuesAt(long positionUs) throws Exception {
        Format format = vttFormat();
        byte[] data = VTT.getBytes(StandardCharsets.UTF_8);
        SubtitleDecoder decoder = SubtitleDecoderFactory.DEFAULT.createDecoder(format);
        List<String> result = Collections.emptyList();

        try {
            decoder.setPositionUs(0);
            SubtitleInputBuffer input = decoder.dequeueInputBuffer();
            input.ensureSpaceForWrite(data.length);
            input.data.put(data);
            input.timeUs = 0;
            input.subsampleOffsetUs = format.subsampleOffsetUs;
            input.flip();
            decoder.queueInputBuffer(input);

            SubtitleInputBuffer endOfStream = decoder.dequeueInputBuffer();
            endOfStream.setFlags(C.BUFFER_FLAG_END_OF_STREAM);
            decoder.queueInputBuffer(endOfStream);

            long deadlineMs = System.nanoTime() / 1_000_000 + 5_000;

            while (true) {
                SubtitleOutputBuffer output = decoder.dequeueOutputBuffer();

                if (output == null) {
                    if (System.nanoTime() / 1_000_000 > deadlineMs) {
                        break; // the decode thread never finished within the grace period
                    }

                    Thread.sleep(2);
                    continue;
                }

                boolean end = output.isEndOfStream();

                if (!end && output.timeUs <= positionUs) {
                    List<String> texts = new ArrayList<>();

                    for (Cue cue : output.getCues(positionUs)) {
                        texts.add(cue.text.toString());
                    }

                    result = texts;
                }

                output.release();

                if (end) {
                    break;
                }
            }
        } finally {
            decoder.release();
        }

        return result;
    }
}
