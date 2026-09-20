package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.text.Cue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Turns decoded event boundaries into an immutable {@link SubtitleTimeline}.
 *
 * <p>Rules taken from the plan:
 * <ul>
 *     <li>Frames are formed from event boundaries in time order; the caller must not assume that
 *     cue end times are monotonic.</li>
 *     <li>Several decoder events can share one microsecond (the VTT decoder reports the end of the
 *     previous cue and the start of the next cue at the same time). They are one display frame:
 *     {@code TextRenderer} advances past every event at or before the position, and the cue list of
 *     an instant is time based, so collapsing equal times matches playback instead of producing
 *     zero-length frames and double-applying the incremental text rule.</li>
 *     <li>Empty frames are kept: they are the clearing boundaries.</li>
 *     <li>The same display text in the same cue slot keeps its item id across consecutive frames;
 *     text after a gap, or changed text, gets a new id.</li>
 *     <li>Text is normalized with the same {@link OriginalSubtitleNormalizer} rule the screen uses,
 *     applied over the events in order exactly as playback would.</li>
 * </ul>
 *
 * <p>Normalization is therefore order dependent: this builder describes a fresh, in-order snapshot
 * from the start of the file, which is also why the plan resets the player's buffer on seek.
 */
public class SubtitleTimelineBuilder {
    public SubtitleTimeline build(List<SubtitleEvent> events, long mediaEndUs) {
        if (events == null || events.isEmpty()) {
            return new SubtitleTimeline(Collections.<SubtitleFrame>emptyList(), fingerprint(Collections.<SubtitleEvent>emptyList()));
        }

        List<SubtitleEvent> sorted = collapseEqualTimes(events);
        List<List<String>> textsPerFrame = new ArrayList<>(sorted.size());
        OriginalSubtitleNormalizer normalizer = new OriginalSubtitleNormalizer();

        for (SubtitleEvent event : sorted) {
            textsPerFrame.add(toTexts(normalizer.normalize(event.getCues())));
        }

        String contentFingerprint = fingerprint(sorted, textsPerFrame);
        List<SubtitleFrame> frames = new ArrayList<>(sorted.size());
        List<SubtitleItem> previousItems = Collections.emptyList();

        for (int i = 0; i < sorted.size(); i++) {
            long startUs = sorted.get(i).getTimeUs();
            long endUs = i + 1 < sorted.size() ? sorted.get(i + 1).getTimeUs()
                    : (mediaEndUs > startUs ? mediaEndUs : C.TIME_UNSET);
            List<String> texts = textsPerFrame.get(i);
            List<SubtitleItem> items = new ArrayList<>(texts.size());

            for (int slot = 0; slot < texts.size(); slot++) {
                String text = texts.get(slot);
                String itemId;

                if (slot < previousItems.size() && previousItems.get(slot).getText().equals(text)) {
                    itemId = previousItems.get(slot).getItemId(); // still the same, still valid
                } else {
                    itemId = contentFingerprint + "-" + startUs + "-" + slot;
                }

                items.add(new SubtitleItem(itemId, text));
            }

            frames.add(new SubtitleFrame(startUs, endUs, items));
            previousItems = items;
        }

        return new SubtitleTimeline(frames, contentFingerprint);
    }

    /**
     * Sorts the events and keeps a single event per microsecond. The kept event is the last one at
     * that instant, which is what {@code TextRenderer} ends up displaying after advancing its event
     * index past every event at or before the playback position.
     */
    private static List<SubtitleEvent> collapseEqualTimes(List<SubtitleEvent> events) {
        List<SubtitleEvent> sorted = new ArrayList<>(events);
        // API 17 safe: Comparator.comparingLong and Long.compare are API 24/19 respectively.
        Collections.sort(sorted, new Comparator<SubtitleEvent>() {
            @Override
            public int compare(SubtitleEvent first, SubtitleEvent second) {
                long difference = first.getTimeUs() - second.getTimeUs();

                return difference == 0 ? 0 : (difference < 0 ? -1 : 1);
            }
        });
        List<SubtitleEvent> result = new ArrayList<>(sorted.size());

        for (SubtitleEvent event : sorted) {
            if (!result.isEmpty() && result.get(result.size() - 1).getTimeUs() == event.getTimeUs()) {
                result.set(result.size() - 1, event);
            } else {
                result.add(event);
            }
        }

        return result;
    }

    private static List<String> toTexts(List<Cue> cues) {
        List<String> texts = new ArrayList<>(cues.size());

        for (Cue cue : cues) {
            texts.add(cue != null && cue.text != null ? cue.text.toString() : "");
        }

        return texts;
    }

    /** FNV-1a over event times and normalized texts; a session-local cache key, not a security hash. */
    private static String fingerprint(List<SubtitleEvent> events) {
        List<List<String>> texts = new ArrayList<>(events.size());

        for (SubtitleEvent event : events) {
            texts.add(event.getCueTexts());
        }

        return fingerprint(events, texts);
    }

    private static String fingerprint(List<SubtitleEvent> events, List<List<String>> textsPerFrame) {
        long hash = 0xcbf29ce484222325L;

        for (int i = 0; i < events.size(); i++) {
            hash = fnv(hash, Long.toString(events.get(i).getTimeUs()));

            for (String text : textsPerFrame.get(i)) {
                hash = fnv(hash, text);
            }
        }

        return Long.toHexString(hash);
    }

    private static long fnv(long hash, String value) {
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x100000001b3L;
        }

        return hash;
    }
}
