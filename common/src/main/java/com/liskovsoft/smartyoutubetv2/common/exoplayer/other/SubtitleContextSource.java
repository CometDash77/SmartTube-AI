package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import java.util.List;

/**
 * Supplies the session context material a translation request may carry (plan 4.2). The planner asks
 * for the examples that belong to the current position and for the frozen summary of the session.
 */
public interface SubtitleContextSource {
    /** Verified examples that played before the position, in time order, inside the code-point bound. */
    List<SubtitleTextPair> examplesBefore(long positionUs, int maxCodePoints);

    /** The frozen summary of this session, or null when there is none. */
    SubtitleSummary getSummary();
}
