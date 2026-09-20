package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.content.Context;

import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.ui.SubtitleView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * T04 acceptance for the single write entry: the original text is processed once, translations and
 * display modes only recompose the current frame, and an empty frame clears the view.
 */
@RunWith(RobolectricTestRunner.class)
public class SubtitleManagerTest {
    private RecordingSink mSink;
    private SubtitleManager mManager;

    private static class RecordingSink implements SubtitleManager.CueSink {
        private final List<List<String>> writes = new ArrayList<>();

        @Override
        public void setCues(List<Cue> cues) {
            List<String> texts = new ArrayList<>(cues.size());

            for (Cue cue : cues) {
                texts.add(cue.text != null ? cue.text.toString() : null);
            }

            writes.add(texts);
        }

        List<String> lastWrite() {
            return writes.isEmpty() ? null : writes.get(writes.size() - 1);
        }

        int writeCount() {
            return writes.size();
        }
    }

    @Test
    public void derivedSentenceLinesOwnTheScreenAndNativeCuesStayBuffered() {
        mManager.onCues(cues("native one"));

        mManager.setDerivedOriginalLines(Collections.singletonList("Merged sentence."));

        assertEquals(Collections.singletonList("Merged sentence."), mSink.lastWrite());

        // A native cue that arrives while the derived sentence is on screen must not replace it.
        mManager.onCues(cues("native two"));

        assertEquals("the merged sentence stays on screen",
                Collections.singletonList("Merged sentence."), mSink.lastWrite());

        mManager.setDerivedOriginalLines(null);

        assertEquals("the newest native text appears immediately",
                Collections.singletonList("native two"), mSink.lastWrite());
    }

    @Test
    public void anEmptyDerivedFrameClearsTheSentence() {
        mManager.setDerivedOriginalLines(Collections.singletonList("Merged sentence."));
        mManager.setDerivedOriginalLines(Collections.<String>emptyList());

        assertEquals(Collections.<String>emptyList(), mSink.lastWrite());
    }

    private static List<Cue> cues(String... texts) {
        List<Cue> result = new ArrayList<>();

        for (String text : texts) {
            result.add(new Cue(text));
        }

        return result;
    }

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.application;
        mSink = new RecordingSink();
        mManager = new SubtitleManager(new SubtitleView(context), mSink);
    }

    @Test
    public void writesTheNormalizedOriginalOnEveryCueUpdate() {
        mManager.onCues(cues("Hello"));
        assertEquals(Arrays.asList("Hello"), mSink.lastWrite());

        mManager.onCues(cues("Hello world"));
        assertEquals(Arrays.asList(" world"), mSink.lastWrite());
        // Stored state is the processed display text, so composition can never re-normalize it.
        assertEquals(Arrays.asList(" world"), texts(mManager.getCurrentOriginalTexts()));
    }

    @Test
    public void translationsRecomposeWithoutRenormalizingTheOriginal() {
        mManager.onCues(cues("Hello"));
        int writesAfterCues = mSink.writeCount();

        mManager.setAiDisplayMode(SubtitleComposer.MODE_BILINGUAL);
        mManager.setTranslations(Arrays.asList("\u4f60\u597d"));

        assertEquals(writesAfterCues + 2, mSink.writeCount());
        assertEquals(Arrays.asList("Hello\n\u4f60\u597d"), mSink.lastWrite());
        assertEquals(Arrays.asList("Hello"), texts(mManager.getCurrentOriginalTexts())); // composition never re-enters the original state
    }

    @Test
    public void emptyCueUpdateClearsTheView() {
        mManager.onCues(cues("Hello"));
        mManager.setAiDisplayMode(SubtitleComposer.MODE_BILINGUAL);
        mManager.setTranslations(Arrays.asList("\u4f60\u597d"));

        mManager.onCues(Collections.<Cue>emptyList());

        assertEquals(Collections.emptyList(), mSink.lastWrite());
        assertEquals(0, mManager.getCurrentOriginalTexts().size());
    }

    @Test
    public void modeSwitchOnlyRecomposesTheCurrentFrame() {
        mManager.onCues(cues("Hello"));
        mManager.setTranslations(Arrays.asList("\u4f60\u597d"));

        mManager.setAiDisplayMode(SubtitleComposer.MODE_TRANSLATION_ONLY);
        assertEquals(Arrays.asList("\u4f60\u597d"), mSink.lastWrite());

        mManager.setAiDisplayMode(SubtitleComposer.MODE_ORIGINAL_ONLY);
        assertEquals(Arrays.asList("Hello"), mSink.lastWrite());

        mManager.setAiDisplayMode(SubtitleComposer.MODE_BILINGUAL);
        assertEquals(Arrays.asList("Hello\n\u4f60\u597d"), mSink.lastWrite());
        assertEquals(SubtitleComposer.MODE_BILINGUAL, mManager.getAiDisplayMode());
    }

    @Test
    public void clearingTranslationsRestoresTheOriginal() {
        mManager.onCues(cues("Hello"));
        mManager.setAiDisplayMode(SubtitleComposer.MODE_BILINGUAL);
        mManager.setTranslations(Arrays.asList("\u4f60\u597d"));

        mManager.clearTranslations();

        assertEquals(Arrays.asList("Hello"), mSink.lastWrite());
    }

    @Test
    public void sameWritingSystemShowsOneOriginalLine() {
        mManager.onCues(cues("Hello"));
        mManager.setAiDisplayMode(SubtitleComposer.MODE_BILINGUAL);
        mManager.setTranslations(Arrays.asList("Hello"));

        mManager.setSourceSameAsTarget(true);

        assertEquals(Arrays.asList("Hello"), mSink.lastWrite());
    }

    @Test
    public void resetDropsTheCarriedScrollingText() {
        mManager.onCues(cues("Line one"));
        assertEquals(Arrays.asList("Line one"), mSink.lastWrite());

        mManager.resetOriginalCueState();
        mManager.onCues(cues("Line one again"));

        assertEquals(Arrays.asList("Line one again"), mSink.lastWrite());
    }

    @Test
    public void withoutResetTheCarriedTextIsStillStripped() {
        // Pins the pre-existing behaviour the reset above is meant to fix on seek/track change.
        mManager.onCues(cues("Line one"));
        mManager.onCues(cues("Line one again"));

        assertEquals(Arrays.asList(" again"), mSink.lastWrite());
    }

    @Test
    public void multipleCuesProduceOneComposedLineEach() {
        mManager.onCues(cues("First", "Second"));
        mManager.setAiDisplayMode(SubtitleComposer.MODE_BILINGUAL);
        mManager.setTranslations(Arrays.asList("\u4e00", "\u4e8c"));

        assertEquals(Arrays.asList("First\n\u4e00", "Second\n\u4e8c"), mSink.lastWrite());
    }

    @Test
    public void managerStartsWithTheOriginalOnlyMode() {
        assertNotNull(mManager.getSubtitleComposer());
        assertEquals(SubtitleComposer.MODE_ORIGINAL_ONLY, mManager.getAiDisplayMode());
    }

    private static List<String> texts(List<CharSequence> texts) {
        List<String> result = new ArrayList<>(texts.size());

        for (CharSequence text : texts) {
            result.add(text != null ? text.toString() : null);
        }

        return result;
    }
}
