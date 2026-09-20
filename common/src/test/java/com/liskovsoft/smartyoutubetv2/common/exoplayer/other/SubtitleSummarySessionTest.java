package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Plan 4.2: at most one automatic analysis attempt per source and analysis configuration. */
public class SubtitleSummarySessionTest {
    @Test
    public void oneAttemptPerKey() {
        SubtitleSummarySession session = new SubtitleSummarySession();

        assertTrue(session.beginAttempt("source|config"));
        assertFalse("a pending attempt may not be restarted", session.beginAttempt("source|config"));

        session.finishAttempt();

        assertFalse("a failed or successful attempt is never repeated", session.beginAttempt("source|config"));
        assertTrue(session.hasAttempted());
    }

    @Test
    public void aNewSourceOrAnalysisConfigurationStartsAFreshAttempt() {
        SubtitleSummarySession session = new SubtitleSummarySession();
        session.beginAttempt("source|config");
        session.finishAttempt();

        assertTrue("another analysis configuration must be analysed once", session.beginAttempt("source|config2"));
        session.finishAttempt();

        assertTrue(session.beginAttempt("source2|config"));
    }

    @Test
    public void aRefusedAttemptDoesNotSpendTheOneTry() {
        SubtitleSummarySession session = new SubtitleSummarySession();

        assertTrue(session.beginAttempt("source|config"));
        session.releaseAttempt(); // the shared AI slot was busy

        assertTrue("a later event may still analyse", session.beginAttempt("source|config"));
    }

    @Test
    public void resetForgetsEverything() {
        SubtitleSummarySession session = new SubtitleSummarySession();
        session.beginAttempt("source|config");
        session.finishAttempt();

        session.reset();

        assertTrue(session.beginAttempt("source|config"));
        assertFalse(session.hasAttempted());
    }
}
