package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.dash.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.Period;
import com.google.android.exoplayer2.source.dash.manifest.Representation;
import com.google.android.exoplayer2.source.dash.manifest.SingleSegmentBase;
import com.google.android.exoplayer2.util.MimeTypes;
import com.liskovsoft.mediaserviceinterfaces.data.MediaSubtitle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * T02 manifest fixture: the adapter must hand the binder the very Format instances the parser put
 * into the manifest, and only the subtitle representations.
 *
 * <p>Robolectric is required because building a DASH representation parses its base URL with
 * android.net.Uri.
 */
@RunWith(RobolectricTestRunner.class)
public class SubtitleManifestAdapterTest {
    private static final String ORIGIN_URL = "https://example.com/api/timedtext?v=abc&lang=en&fmt=srv3";
    private static final String TRANSLATED_URL = ORIGIN_URL + "&tlang=zh-Hans";

    private static Format textFormat(String vssId, String name) {
        return Format.createTextSampleFormat(vssId, MimeTypes.APPLICATION_MP4VTT, 0, name);
    }

    private static Representation representation(Format format, String url) {
        return Representation.newInstance(Representation.REVISION_ID_DEFAULT, format, url, new SingleSegmentBase(), null);
    }

    private static DashManifest manifest(List<Representation> representations) {
        AdaptationSet adaptationSet = new AdaptationSet(0, C.TRACK_TYPE_TEXT, representations);
        Period period = new Period(null, 0, Collections.singletonList(adaptationSet));

        return new DashManifest(0, C.TIME_UNSET, 0, false, C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET,
                C.TIME_UNSET, null, null, null, Collections.singletonList(period));
    }

    private static MediaSubtitle subtitle(String vssId, String languageCode, String name, String url) {
        return new MediaSubtitle() {
            @Override
            public String getBaseUrl() {
                return url;
            }

            @Override
            public boolean isTranslatable() {
                return true;
            }

            @Override
            public String getLanguageCode() {
                return languageCode;
            }

            @Override
            public String getVssId() {
                return vssId;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getMimeType() {
                return MimeTypes.APPLICATION_MP4VTT;
            }

            @Override
            public String getCodecs() {
                return null;
            }

            @Override
            public String getType() {
                return null;
            }
        };
    }

    @Test
    public void collectsOnlySubtitleRepresentationsAndKeepsTheirFormatInstances() {
        Format originFormat = textFormat("en", "English");
        Format translatedFormat = textFormat("en", "Chinese (Simplified)*");
        Format videoFormat = Format.createVideoSampleFormat("v", null, "avc1", -1, -1,
                1920, 1080, 30, null, null);
        DashManifest manifest = manifest(Arrays.asList(representation(originFormat, ORIGIN_URL),
                representation(translatedFormat, TRANSLATED_URL),
                representation(videoFormat, "https://example.com/video.mp4")));

        List<SubtitleFormatCandidate> candidates = SubtitleManifestAdapter.fromDashManifest(manifest);

        assertEquals(2, candidates.size());
        assertSame(originFormat, candidates.get(0).getFormat());
        assertEquals(ORIGIN_URL, candidates.get(0).getBaseUrl());
        assertSame(translatedFormat, candidates.get(1).getFormat());
        assertEquals(TRANSLATED_URL, candidates.get(1).getBaseUrl());
    }

    @Test
    public void bindsTheSelectedManifestFormatToItsOwnSource() {
        Format originFormat = textFormat("en", "English");
        Format translatedFormat = textFormat("en", "Chinese (Simplified)*");
        DashManifest manifest = manifest(Arrays.asList(representation(originFormat, ORIGIN_URL),
                representation(translatedFormat, TRANSLATED_URL)));
        List<SubtitleFormatCandidate> candidates = SubtitleManifestAdapter.fromDashManifest(manifest);
        List<MediaSubtitle> subtitles = Arrays.asList(subtitle("en", "en", "English", ORIGIN_URL),
                subtitle("en", "zh-Hans", "Chinese (Simplified)*", TRANSLATED_URL));
        SubtitleSourceBinder binder = new SubtitleSourceBinder();

        binder.bind(subtitles, candidates);

        SelectedSubtitleSource translated = binder.resolve(translatedFormat);
        assertNotNull(translated);
        assertEquals(TRANSLATED_URL, translated.getBaseUrl());
        assertEquals("zh-Hans", translated.getLanguageCode());
        assertEquals(SubtitleSourceBinder.Status.BOUND, binder.getStatus());

        // An unrelated Format instance with the same values must not be accepted by identity.
        assertNull(binder.resolve(textFormat("en", "English")));
        assertEquals(SubtitleSourceBinder.Status.SOURCE_UNKNOWN, binder.getStatus());
    }

    @Test
    public void emptyManifestYieldsNoCandidates() {
        List<SubtitleFormatCandidate> candidates = SubtitleManifestAdapter.fromDashManifest(manifest(new ArrayList<>()));

        assertEquals(0, candidates.size());
    }
}
