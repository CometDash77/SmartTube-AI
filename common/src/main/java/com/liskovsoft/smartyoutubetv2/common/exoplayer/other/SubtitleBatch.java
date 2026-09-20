package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One ordered, budget-checked unit of work for the translation service.
 *
 * <p>Beside the items it carries the reference material of plan 4.2 (neighbouring lines, verified
 * examples and the optional frozen summary) and the frame start of every item, so a verified success
 * can later become a translation example with its real position.
 */
public final class SubtitleBatch {
    /** Frame start of an item the caller did not supply timing for (legacy and test callers). */
    public static final long TIME_UNKNOWN = Long.MIN_VALUE + 1;

    private final List<SubtitleItem> mItems;
    private final List<Long> mItemStartUs;
    private final SubtitleContext mContext;
    private final int mCodePoints;

    /** Compatibility constructor: neighbouring lines without examples or a summary. */
    public SubtitleBatch(List<SubtitleItem> items, List<String> contextBefore, List<String> contextAfter) {
        this(items, null, new SubtitleContext(contextBefore, contextAfter, null, null));
    }

    /**
     * Builds a batch with its frame timing and reference material.
     *
     * <p>A named factory rather than a second constructor: the legacy three-argument constructor is
     * still used by callers without context, and one overload taking {@code (List, List, Object)} would
     * make every existing {@code new SubtitleBatch(items, null, null)} ambiguous.
     *
     * @param itemStartUs frame start of each item (may be null or shorter than the item list)
     * @param context     reference material of this request; null means none
     */
    public static SubtitleBatch withContext(List<SubtitleItem> items, List<Long> itemStartUs,
                                            SubtitleContext context) {
        return new SubtitleBatch(items, itemStartUs, context);
    }

    private SubtitleBatch(List<SubtitleItem> items, List<Long> itemStartUs, SubtitleContext context) {
        mItems = items == null ? Collections.<SubtitleItem>emptyList() : Collections.unmodifiableList(new ArrayList<>(items));
        mItemStartUs = itemStartUs == null
                ? Collections.<Long>emptyList() : Collections.unmodifiableList(new ArrayList<>(itemStartUs));
        mContext = context == null ? SubtitleContext.EMPTY : context;

        int codePoints = 0;

        for (SubtitleItem item : mItems) {
            codePoints += codePoints(item.getText());
        }

        mCodePoints = codePoints + mContext.codePoints();
    }

    public List<SubtitleItem> getItems() {
        return mItems;
    }

    public List<String> getContextBefore() {
        return mContext.getBefore();
    }

    public List<String> getContextAfter() {
        return mContext.getAfter();
    }

    /** All reference material of this request (plan 4.2). */
    public SubtitleContext getContext() {
        return mContext;
    }

    /** Frame start of the item at the index, or {@link #TIME_UNKNOWN} when the caller had no timing. */
    public long getItemStartUs(int index) {
        return index >= 0 && index < mItemStartUs.size() ? mItemStartUs.get(index) : TIME_UNKNOWN;
    }

    /** Code points of items plus all context: the plan's 6,000 character budget. */
    public int getCodePoints() {
        return mCodePoints;
    }

    public List<String> getItemIds() {
        List<String> ids = new ArrayList<>(mItems.size());

        for (SubtitleItem item : mItems) {
            ids.add(item.getItemId());
        }

        return ids;
    }

    public boolean isEmpty() {
        return mItems.isEmpty();
    }

    static int codePoints(String text) {
        return text == null ? 0 : text.codePointCount(0, text.length());
    }

    @Override
    public String toString() {
        return "SubtitleBatch{items=" + mItems.size() + ", codePoints=" + mCodePoints + "}";
    }
}
