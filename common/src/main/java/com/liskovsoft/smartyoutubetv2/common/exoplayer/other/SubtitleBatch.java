package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One ordered, budget-checked unit of work for the translation service. */
public final class SubtitleBatch {
    private final List<SubtitleItem> mItems;
    private final List<String> mContextBefore;
    private final List<String> mContextAfter;
    private final int mCodePoints;

    public SubtitleBatch(List<SubtitleItem> items, List<String> contextBefore, List<String> contextAfter) {
        mItems = items == null ? Collections.<SubtitleItem>emptyList() : Collections.unmodifiableList(new ArrayList<>(items));
        mContextBefore = contextBefore == null ? Collections.<String>emptyList() : Collections.unmodifiableList(new ArrayList<>(contextBefore));
        mContextAfter = contextAfter == null ? Collections.<String>emptyList() : Collections.unmodifiableList(new ArrayList<>(contextAfter));

        int codePoints = 0;

        for (SubtitleItem item : mItems) {
            codePoints += codePoints(item.getText());
        }

        for (String context : mContextBefore) {
            codePoints += codePoints(context);
        }

        for (String context : mContextAfter) {
            codePoints += codePoints(context);
        }

        mCodePoints = codePoints;
    }

    public List<SubtitleItem> getItems() {
        return mItems;
    }

    public List<String> getContextBefore() {
        return mContextBefore;
    }

    public List<String> getContextAfter() {
        return mContextAfter;
    }

    /** Code points of items plus context: the plan's 6,000 character budget. */
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
