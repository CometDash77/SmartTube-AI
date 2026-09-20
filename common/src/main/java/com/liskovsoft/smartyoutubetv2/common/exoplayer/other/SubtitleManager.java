package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build.VERSION;
import android.util.TypedValue;
import android.view.View;
import android.view.accessibility.CaptioningManager;
import android.view.accessibility.CaptioningManager.CaptionStyle;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import com.google.android.exoplayer2.text.CaptionStyleCompat;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.prefs.common.DataChangeBase.OnDataChange;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;

import java.util.ArrayList;
import java.util.List;

public class SubtitleManager implements TextOutput, OnDataChange, SubtitleDisplay {
    private static final String TAG = SubtitleManager.class.getSimpleName();
    private final SubtitleView mSubtitleView;
    private final Context mContext;
    private final List<SubtitleStyle> mSubtitleStyles = new ArrayList<>();
    private final AppPrefs mPrefs;
    private final PlayerData mPlayerData;
    private final OriginalSubtitleNormalizer mOriginalSubtitleNormalizer = new OriginalSubtitleNormalizer();
    private final SubtitleComposer mSubtitleComposer = new SubtitleComposer();
    private final CueSink mCueSink;
    private List<CharSequence> mCurrentOriginalTexts = new ArrayList<>();
    /** Derived sentence lines of the frame on screen, or null while the native cue path owns it. */
    private List<String> mDerivedOriginalLines;

    /** The only way this manager hands subtitles to a view; injectable for tests. */
    interface CueSink {
        void setCues(List<Cue> cues);
    }

    public static class SubtitleStyle {
        public final int nameResId;
        public final int subsColorResId;
        public final int backgroundColorResId;
        public final int captionStyle;

        public SubtitleStyle(int nameResId) {
            this(nameResId, -1, -1, -1);
        }

        public SubtitleStyle(int nameResId, int subsColorResId, int backgroundColorResId, int captionStyle) {
            this.nameResId = nameResId;
            this.subsColorResId = subsColorResId;
            this.backgroundColorResId = backgroundColorResId;
            this.captionStyle = captionStyle;
        }

        public boolean isSystem() {
            return subsColorResId == -1 && backgroundColorResId == -1 && captionStyle == -1;
        }
    }

    public SubtitleManager(SubtitleView subtitleView) {
        this(subtitleView, null);
    }

    SubtitleManager(SubtitleView subtitleView, CueSink cueSink) {
        mContext = subtitleView.getContext();
        mSubtitleView = subtitleView;
        mCueSink = cueSink != null ? cueSink : subtitleView::setCues;
        mPrefs = AppPrefs.instance(mContext);
        mPlayerData = PlayerData.instance(mContext);
        mPlayerData.setOnChange(this);
        configureSubtitleView();
    }

    @Override
    public void onDataChange() {
        configureSubtitleView();
    }

    /**
     * Always processes the original text first and exactly once. Translations and display-mode
     * changes recompose this stored frame; they never run the normalization again.
     */
    @Override
    public void onCues(List<Cue> cues) {
        List<Cue> processed = mOriginalSubtitleNormalizer.normalize(cues);
        List<CharSequence> texts = new ArrayList<>(processed.size());

        for (Cue cue : processed) {
            texts.add(cue != null && cue.text != null ? cue.text : "");
        }

        mCurrentOriginalTexts = texts;

        if (mDerivedOriginalLines == null) {
            mSubtitleComposer.setOriginalLines(toStrings(texts));
            renderCurrent();
        }
        // While a derived sentence frame is on screen the native text is only buffered: rendering it
        // would replace the merged sentence with a raw cue fragment (plan 4.3.6).
    }

    /**
     * Shows the derived (rule-segmented) sentence of the frame on screen, or hands the screen back to
     * the native cue path when null. The buffered native text is kept either way, so a fallback is
     * immediate and never shows an empty frame.
     */
    @Override
    public void setDerivedOriginalLines(List<String> lines) {
        mDerivedOriginalLines = lines == null ? null : new ArrayList<>(lines);
        mSubtitleComposer.setOriginalLines(mDerivedOriginalLines != null
                ? new ArrayList<>(mDerivedOriginalLines) : toStrings(mCurrentOriginalTexts));
        renderCurrent();
    }

    /** True while a derived sentence frame owns the screen (diagnostics and tests). */
    public boolean hasDerivedOriginalLines() {
        return mDerivedOriginalLines != null;
    }

    /** Translations aligned to the current frame's cue slots; missing entries are null. */
    public void setTranslations(List<String> translations) {
        mSubtitleComposer.setTranslations(translations);
        renderCurrent();
    }

    public void clearTranslations() {
        mSubtitleComposer.clearTranslations();
        renderCurrent();
    }

    public void setAiDisplayMode(int mode) {
        mSubtitleComposer.setMode(mode);
        renderCurrent();
    }

    public int getAiDisplayMode() {
        return mSubtitleComposer.getMode();
    }

    /** True when source and target writing systems are the same: every mode shows one original line. */
    public void setSourceSameAsTarget(boolean sourceSameAsTarget) {
        mSubtitleComposer.setSourceSameAsTarget(sourceSameAsTarget);
        renderCurrent();
    }

    public SubtitleComposer getSubtitleComposer() {
        return mSubtitleComposer;
    }

    /**
     * Drops the incremental original-text state. The player must call this on seek, track or video
     * change so a line buffered for one source cannot strip text from the next one.
     */
    public void resetOriginalCueState() {
        mOriginalSubtitleNormalizer.reset();
    }

    /** Recomposes the current frame without touching the original text state. */
    public void renderCurrent() {
        List<String> lines = mSubtitleComposer.compose();
        List<Cue> cues = new ArrayList<>(lines.size());

        for (String line : lines) {
            cues.add(new Cue(line));
        }

        if (mCueSink != null) {
            mCueSink.setCues(cues);
        }
    }

    /** The original texts of the currently displayed frame, after normalization. */
    public List<CharSequence> getCurrentOriginalTexts() {
        return mCurrentOriginalTexts;
    }

    private static List<String> toStrings(List<CharSequence> texts) {
        List<String> result = new ArrayList<>(texts.size());

        for (CharSequence text : texts) {
            result.add(text != null ? text.toString() : "");
        }

        return result;
    }

    public void show(boolean show) {
        if (mSubtitleView != null) {
            mSubtitleView.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private List<SubtitleStyle> getSubtitleStyles() {
        return mSubtitleStyles;
    }

    private SubtitleStyle getSubtitleStyle() {
        return mPlayerData.getSubtitleStyle();
    }

    private void setSubtitleStyle(SubtitleStyle subtitleStyle) {
        mPlayerData.setSubtitleStyle(subtitleStyle);
        configureSubtitleView();
    }

    private void configureSubtitleView() {
        if (mSubtitleView != null) {
            // disable default style
            mSubtitleView.setApplyEmbeddedStyles(false);

            SubtitleStyle subtitleStyle = getSubtitleStyle();

            if (subtitleStyle.isSystem()) {
                if (VERSION.SDK_INT >= 19) {
                    applySystemStyle();
                }
            } else {
                applyStyle(subtitleStyle);
            }

            mSubtitleView.setBottomPaddingFraction(mPlayerData.getSubtitlePosition());
        }
    }

    private void applyStyle(SubtitleStyle subtitleStyle) {
        int textColor = ContextCompat.getColor(mContext, subtitleStyle.subsColorResId);
        int outlineColor = ContextCompat.getColor(mContext, R.color.black);
        int backgroundColor = ContextCompat.getColor(mContext, subtitleStyle.backgroundColorResId);

        CaptionStyleCompat style =
                new CaptionStyleCompat(textColor,
                        backgroundColor, Color.TRANSPARENT,
                        subtitleStyle.captionStyle,
                        outlineColor, Typeface.DEFAULT_BOLD);
        mSubtitleView.setStyle(style);

        float textSize = getTextSizePx();
        mSubtitleView.setFixedTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
    }

    @RequiresApi(19)
    private void applySystemStyle() {
        CaptioningManager captioningManager =
                (CaptioningManager) mContext.getSystemService(Context.CAPTIONING_SERVICE);

        if (captioningManager != null) {
            CaptionStyle userStyle = captioningManager.getUserStyle();

            CaptionStyleCompat style =
                    new CaptionStyleCompat(userStyle.foregroundColor,
                            userStyle.backgroundColor, VERSION.SDK_INT >= 21 ? userStyle.windowColor : Color.TRANSPARENT,
                            userStyle.edgeType,
                            userStyle.edgeColor, userStyle.getTypeface());
            mSubtitleView.setStyle(style);

            float textSizePx = getTextSizePx();
            mSubtitleView.setFixedTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx * captioningManager.getFontScale());
        }
    }

    private float getTextSizePx() {
        float textSizePx = mSubtitleView.getContext().getResources().getDimension(R.dimen.subtitle_text_size);
        return textSizePx * mPlayerData.getSubtitleScale();
    }
}
