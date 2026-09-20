package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Composes what one subtitle frame shows from the original text and its translations.
 *
 * <p>This is pure state: it never decodes, normalizes or requests anything, so a translation
 * arriving or a display mode changing only recomposes the current frame. The original text is
 * processed exactly once, before it reaches this object (see {@link SubtitleManager#onCues}).
 *
 * <p>Mode behaviour (plan section 5):
 * <ul>
 *     <li>{@link #MODE_BILINGUAL}: original above, translation below; a missing translation shows
 *     the original alone, never a placeholder.</li>
 *     <li>{@link #MODE_TRANSLATION_ONLY}: the translation when it is available, otherwise the
 *     original, so the frame is never blank.</li>
 *     <li>{@link #MODE_ORIGINAL_ONLY}: the original.</li>
 * </ul>
 *
 * <p>When the source and target writing systems are known to be the same, every mode shows a single
 * original line: translating would only duplicate the text.
 */
public class SubtitleComposer {
    public static final int MODE_ORIGINAL_ONLY = 0;
    public static final int MODE_TRANSLATION_ONLY = 1;
    public static final int MODE_BILINGUAL = 2;

    private List<String> mOriginalLines = Collections.emptyList();
    private List<String> mTranslations = Collections.emptyList();
    private int mMode = MODE_ORIGINAL_ONLY;
    private boolean mSourceSameAsTarget;

    /** Replaces the original lines of the currently displayed frame. */
    public void setOriginalLines(List<String> originalLines) {
        mOriginalLines = copy(originalLines);
    }

    public List<String> getOriginalLines() {
        return mOriginalLines;
    }

    /** Translations aligned to the original lines; missing entries are null or absent. */
    public void setTranslations(List<String> translations) {
        mTranslations = copy(translations);
    }

    public void clearTranslations() {
        mTranslations = Collections.emptyList();
    }

    public int getMode() {
        return mMode;
    }

    public void setMode(int mode) {
        if (mode == MODE_ORIGINAL_ONLY || mode == MODE_TRANSLATION_ONLY || mode == MODE_BILINGUAL) {
            mMode = mode;
        }
    }

    /** True when the source and target writing systems are the same, so one original line is shown. */
    public void setSourceSameAsTarget(boolean sourceSameAsTarget) {
        mSourceSameAsTarget = sourceSameAsTarget;
    }

    public boolean hasTranslation(int slot) {
        return getTranslation(slot) != null;
    }

    /** The lines to display for the current frame; empty means the screen must be cleared. */
    public List<String> compose() {
        if (mOriginalLines.isEmpty()) {
            return Collections.emptyList();
        }

        if (mSourceSameAsTarget) {
            return new ArrayList<>(mOriginalLines);
        }

        List<String> result = new ArrayList<>(mOriginalLines.size());

        for (int slot = 0; slot < mOriginalLines.size(); slot++) {
            String original = mOriginalLines.get(slot);
            String translation = getTranslation(slot);

            switch (mMode) {
                case MODE_TRANSLATION_ONLY:
                    result.add(translation != null ? translation : original);
                    break;
                case MODE_BILINGUAL:
                    result.add(translation != null ? original + "\n" + translation : original);
                    break;
                default:
                    result.add(original);
                    break;
            }
        }

        return result;
    }

    @Override
    public String toString() {
        return "SubtitleComposer{mode=" + mMode + ", original=" + mOriginalLines.size()
                + ", translations=" + mTranslations.size() + ", sameScript=" + mSourceSameAsTarget + "}";
    }

    private String getTranslation(int slot) {
        if (slot < 0 || slot >= mTranslations.size()) {
            return null;
        }

        String translation = mTranslations.get(slot);

        // A blank translation is a failed entry, not content: fall back to the original.
        return translation == null || translation.trim().isEmpty() ? null : translation;
    }

    private static List<String> copy(List<String> lines) {
        return lines == null ? Collections.<String>emptyList() : Collections.unmodifiableList(new ArrayList<>(lines));
    }
}
