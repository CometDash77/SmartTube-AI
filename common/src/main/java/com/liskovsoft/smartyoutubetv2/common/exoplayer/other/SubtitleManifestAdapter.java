package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.dash.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.Period;
import com.google.android.exoplayer2.source.dash.manifest.Representation;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects the text representations of a manifest exactly as the player built it, so the binder can
 * key on the very {@link Format} instances that will reach the track selector.
 *
 * <p>DASH and SABR use separate manifest classes in this checkout but the same shape. Only
 * representations the parser marked with {@link C#ROLE_FLAG_SUBTITLE} are collected.
 */
public final class SubtitleManifestAdapter {
    private SubtitleManifestAdapter() {
    }

    public static List<SubtitleFormatCandidate> fromDashManifest(DashManifest manifest) {
        List<SubtitleFormatCandidate> result = new ArrayList<>();

        if (manifest == null) {
            return result;
        }

        for (int periodIndex = 0; periodIndex < manifest.getPeriodCount(); periodIndex++) {
            Period period = manifest.getPeriod(periodIndex);

            if (period == null || period.adaptationSets == null) {
                continue;
            }

            for (AdaptationSet adaptationSet : period.adaptationSets) {
                if (adaptationSet == null || adaptationSet.representations == null) {
                    continue;
                }

                for (Representation representation : adaptationSet.representations) {
                    add(result, representation != null ? representation.format : null,
                            representation != null ? representation.baseUrl : null);
                }
            }
        }

        return result;
    }

    public static List<SubtitleFormatCandidate> fromSabrManifest(com.google.android.exoplayer2.source.sabr.manifest.SabrManifest manifest) {
        List<SubtitleFormatCandidate> result = new ArrayList<>();

        if (manifest == null) {
            return result;
        }

        for (int periodIndex = 0; periodIndex < manifest.getPeriodCount(); periodIndex++) {
            com.google.android.exoplayer2.source.sabr.manifest.Period period = manifest.getPeriod(periodIndex);

            if (period == null || period.adaptationSets == null) {
                continue;
            }

            for (com.google.android.exoplayer2.source.sabr.manifest.AdaptationSet adaptationSet : period.adaptationSets) {
                if (adaptationSet == null || adaptationSet.representations == null) {
                    continue;
                }

                for (com.google.android.exoplayer2.source.sabr.manifest.Representation representation : adaptationSet.representations) {
                    add(result, representation != null ? representation.format : null,
                            representation != null ? representation.baseUrl : null);
                }
            }
        }

        return result;
    }

    private static void add(List<SubtitleFormatCandidate> result, Format format, String baseUrl) {
        if (format == null || !isSubtitle(format)) {
            return;
        }

        result.add(new SubtitleFormatCandidate(format, baseUrl));
    }

    private static boolean isSubtitle(Format format) {
        if ((format.roleFlags & C.ROLE_FLAG_SUBTITLE) != 0) {
            return true;
        }

        String mimeType = format.sampleMimeType;

        return mimeType != null && (mimeType.startsWith("text/") || mimeType.contains("ttml") || mimeType.contains("vtt"));
    }
}
