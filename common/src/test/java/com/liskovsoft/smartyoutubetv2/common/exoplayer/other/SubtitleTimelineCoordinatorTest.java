package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.util.MimeTypes;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * N1 regression: the timeline fetch orchestration - request identity, same-source dedup, late-answer
 * isolation and terminal-state release (plan 17.3).
 *
 * <p>The worker and the main thread are queues owned by the test, so the completion order of several
 * attempts is deterministic: a superseded attempt can be settled strictly after the newer one.
 */
@RunWith(RobolectricTestRunner.class)
public class SubtitleTimelineCoordinatorTest {
    private static final String VTT = "WEBVTT\n\n00:00:00.000 --> 00:00:02.000\nHello\n";

    private final Deque<Runnable> mWorkerQueue = new ArrayDeque<>();
    private final Deque<Runnable> mMainQueue = new ArrayDeque<>();
    private final FakeHost mHost = new FakeHost();
    private final FakeFetcher mFetcher = new FakeFetcher();
    /** Every settled attempt, including the ones that were discarded as stale. */
    private final List<String> mStatuses = new ArrayList<>();
    /** Only the attempts that were allowed to become the current state. */
    private final List<String> mAcceptedStatuses = new ArrayList<>();
    private final List<Boolean> mAccepted = new ArrayList<>();
    private final List<Boolean> mInstalled = new ArrayList<>();

    private SubtitleTimelineCoordinator coordinator() {
        return new SubtitleTimelineCoordinator(mHost, mWorkerQueue::add, mMainQueue::add, mFetcher,
                (status, accepted, installed) -> {
                    mStatuses.add(status);
                    mAccepted.add(accepted);
                    mInstalled.add(installed);

                    if (accepted) {
                        mAcceptedStatuses.add(status);
                    }
                });
    }

    private void runWorker() {
        assertFalse("no worker task was queued", mWorkerQueue.isEmpty());
        mWorkerQueue.poll().run();
    }

    private void runMain() {
        assertFalse("no main-thread task was queued", mMainQueue.isEmpty());
        mMainQueue.poll().run();
    }

    @Test
    public void repeatedEventsOfTheSameSourceFetchExactlyOnce() {
        SubtitleTimelineCoordinator coordinator = coordinator();
        mHost.select("key-a", "https://example.com/a");

        assertTrue(coordinator.request());
        assertFalse("the same source must reuse its in-flight read", coordinator.request());

        runWorker();
        runMain();

        assertFalse("an installed timeline must not be fetched again", coordinator.request());
        assertEquals(1, mFetcher.calls);
        assertEquals(1, mHost.installs);
        assertEquals("OK", mStatuses.get(0));
        assertTrue(mAccepted.get(0));
    }

    @Test
    public void aSupersededAttemptCannotInstallItsLateTimeline() {
        SubtitleTimelineCoordinator coordinator = coordinator();
        mHost.select("key-a", "https://example.com/a");
        mFetcher.playback = new SubtitleSnapshotFetcher.Result(SubtitleSnapshotReader.Status.OK, timeline("A"));

        coordinator.request();
        runWorker(); // the old source's read finishes first, its result is posted

        // A new video/media source arrives before the old answer is applied.
        coordinator.invalidateSourceContext();
        assertTrue("the abandoned attempt must be cancelled", mFetcher.cancellations.get(0).isCancelled());

        mHost.select("key-b", "https://example.com/b");
        mFetcher.playback = new SubtitleSnapshotFetcher.Result(SubtitleSnapshotReader.Status.OK, timeline("B"));
        assertTrue(coordinator.request());
        runWorker();

        // The abandoned answer settles first, exactly like a late callback; then the live one.
        runMain();

        assertEquals("a stale answer must not install a timeline", 0, mHost.installs);
        assertEquals("a stale answer must not become the current state", 0, mAcceptedStatuses.size());

        runMain();

        assertEquals(1, mHost.installs);
        assertEquals("B", mHost.installed.frameAt(0).getTexts().get(0));
        assertEquals(Arrays.asList("OK"), mAcceptedStatuses);
        assertNotNull(mHost.installed);
    }

    @Test
    public void aLateFailureDoesNotFreeTheLiveAttemptsSlot() {
        SubtitleTimelineCoordinator coordinator = coordinator();
        mHost.select("key-a", "https://example.com/a");
        mFetcher.playback = new SubtitleSnapshotFetcher.Result(SubtitleSnapshotReader.Status.IO_FAILED, null);

        coordinator.request();
        runWorker();

        coordinator.invalidateSourceContext();
        mHost.select("key-b", "https://example.com/b");
        mFetcher.playback = new SubtitleSnapshotFetcher.Result(SubtitleSnapshotReader.Status.OK, timeline("B"));
        assertTrue(coordinator.request());
        runWorker();

        runMain(); // the failed attempt of the abandoned source settles now

        assertTrue(coordinator.isAttemptInFlight());
        assertFalse("the live attempt must not be restarted", coordinator.request());
        assertEquals(2, mFetcher.calls);

        runMain(); // the live attempt settles afterwards

        assertEquals(1, mHost.installs);
        assertEquals("B", mHost.installed.frameAt(0).getTexts().get(0));
        assertEquals(Arrays.asList("OK"), mAcceptedStatuses);
        assertEquals("both attempts settled, only one counted", 2, mStatuses.size());
    }

    @Test
    public void aFailedAttemptReleasesItsSlotAndAnExplicitEventRetries() {
        SubtitleTimelineCoordinator coordinator = coordinator();
        mHost.select("key-a", "https://example.com/a");
        mFetcher.playback = new SubtitleSnapshotFetcher.Result(SubtitleSnapshotReader.Status.TIMEOUT, null);

        assertTrue(coordinator.request());
        runWorker();
        runMain();

        assertEquals("TIMEOUT", mStatuses.get(0));
        assertTrue(mAccepted.get(0));
        assertFalse(mInstalled.get(0));
        assertEquals(0, mHost.installs);

        mFetcher.playback = new SubtitleSnapshotFetcher.Result(SubtitleSnapshotReader.Status.OK, timeline("A"));
        assertTrue("an explicit later event may retry", coordinator.request());
        runWorker();
        runMain();

        assertEquals(2, mFetcher.calls);
        assertEquals("the retry installs its timeline", 1, mHost.installs);
        assertEquals(Arrays.asList("TIMEOUT", "OK"), mAcceptedStatuses);
    }

    @Test
    public void anAttemptOfAReleasedEngineInstallsNothing() {
        SubtitleTimelineCoordinator coordinator = coordinator();
        mHost.select("key-a", "https://example.com/a");
        mFetcher.playback = new SubtitleSnapshotFetcher.Result(SubtitleSnapshotReader.Status.OK, timeline("A"));

        coordinator.request();
        runWorker();

        coordinator.cancel(); // engine release
        runMain();

        assertEquals(0, mHost.installs);
        assertFalse(mAccepted.get(0));
        assertTrue(mFetcher.cancellations.get(0).isCancelled());
    }

    @Test
    public void subtitlesOffCancelsTheAttemptInFlight() {
        SubtitleTimelineCoordinator coordinator = coordinator();
        mHost.select("key-a", "https://example.com/a");

        coordinator.request();
        runWorker(); // the read is in flight; the recorded cancellation must reflect the later event
        mHost.select(null, null);

        assertFalse(coordinator.request());

        assertFalse("no attempt may outlive subtitles-off", coordinator.isAttemptInFlight());
        assertTrue(mFetcher.cancellations.get(0).isCancelled());
    }

    @Test
    public void anotherGenerationOfTheSamePayloadReusesTheDecodedTimeline() {
        SubtitleTimelineCoordinator coordinator = coordinator();
        mHost.select("key-a", "https://example.com/a");
        mFetcher.playback = new SubtitleSnapshotFetcher.Result(SubtitleSnapshotReader.Status.OK, timeline("A"));

        coordinator.request();
        runWorker();
        runMain();

        assertEquals(1, mHost.installs);

        // A rebuilt manifest re-resolves the same payload under a new generation.
        coordinator.invalidateSourceContext();
        mHost.select("key-a2", "https://example.com/a");

        assertFalse("the same payload must not be downloaded again", coordinator.request());
        assertEquals("the decoded timeline is re-attributed", 2, mHost.installs);
        assertEquals(1, mFetcher.calls);
        assertEquals(SubtitleTimelineCoordinator.STATUS_REUSED, mStatuses.get(1));
        assertEquals("key-a2", mHost.installedKey);
    }

    @Test
    public void aTimelineOfAnotherSourceIsRefusedAtSettleTime() {
        SubtitleTimelineCoordinator coordinator = coordinator();
        mHost.select("key-a", "https://example.com/a");
        mFetcher.playback = new SubtitleSnapshotFetcher.Result(SubtitleSnapshotReader.Status.OK, timeline("A"));

        coordinator.request();
        runWorker();

        mHost.currentKey = "key-other"; // the identity moved on without a new attempt
        runMain();

        assertEquals(0, mHost.installs);
        assertFalse(mAccepted.get(0));
    }

    @Test
    public void theProductionFetchPathInstallsTheDecodedTimeline() {
        SubtitleTimelineCoordinator coordinator = new SubtitleTimelineCoordinator(mHost, Runnable::run,
                Runnable::run, SubtitleTimelineCoordinator.systemFetcher(), (status, accepted, installed) -> {
                    mStatuses.add(status);
                    mAccepted.add(accepted);
                    mInstalled.add(installed);
                });
        mHost.select("key-a", "https://example.com/a");
        mHost.factory = source -> new ByteArrayInputStream(VTT.getBytes(StandardCharsets.UTF_8));

        assertTrue(coordinator.request());
        assertFalse("the real fetch installed its timeline", coordinator.request());

        assertEquals(1, mHost.installs);
        assertEquals("OK", mStatuses.get(0));
        assertEquals("Hello", mHost.installed.frameAt(0).getTexts().get(0));
    }

    private static SubtitleTimeline timeline(String text) {
        List<Cue> cues = new ArrayList<>();
        cues.add(new Cue(text));
        List<SubtitleEvent> events = new ArrayList<>();
        events.add(new SubtitleEvent(0, cues));

        return new SubtitleTimelineBuilder().build(events, 2_000_000);
    }

    /** Player surface with just enough state to reproduce the real one. */
    private static final class FakeHost implements SubtitleTimelineCoordinator.Host {
        Format format = Format.createTextSampleFormat("en", MimeTypes.TEXT_VTT, 0, "English");
        SubtitleSnapshotFetcher.PayloadFactory factory = source -> new ByteArrayInputStream(new byte[0]);
        SelectedSubtitleSource source;
        String currentKey;
        SubtitleTimeline installed;
        String installedKey;
        int installs;

        void select(String key, String baseUrl) {
            currentKey = key;
            source = baseUrl == null ? null
                    : new SelectedSubtitleSource(1, baseUrl, "en", "en", "English", MimeTypes.TEXT_VTT, null, null, true);
        }

        @Override
        public SelectedSubtitleSource getSelectedSource() {
            return source;
        }

        @Override
        public Format getSelectedFormat() {
            return format;
        }

        @Override
        public SubtitleSnapshotFetcher.PayloadFactory createPayloadFactory() {
            return factory;
        }

        @Override
        public String getCurrentSourceKey() {
            return currentKey;
        }

        @Override
        public SubtitleTimeline getTimelineOfCurrentSource() {
            return installed != null && installedKey != null && installedKey.equals(currentKey) ? installed : null;
        }

        @Override
        public SubtitleTimeline getInstalledTimeline() {
            return installed;
        }

        @Override
        public void installTimeline(SubtitleTimeline timeline) {
            installed = timeline;
            installedKey = currentKey;
            installs++;
        }
    }

    /** Fetcher whose answer and completion order the test chooses. */
    private static final class FakeFetcher implements SubtitleTimelineCoordinator.Fetcher {
        SubtitleSnapshotFetcher.Result playback =
                new SubtitleSnapshotFetcher.Result(SubtitleSnapshotReader.Status.OK, timeline("default"));
        final List<SubtitleSnapshotReader.Cancellation> cancellations = new ArrayList<>();
        int calls;

        @Override
        public SubtitleSnapshotFetcher.Result fetch(SelectedSubtitleSource source, Format format,
                                                    SubtitleSnapshotFetcher.PayloadFactory payloadFactory,
                                                    SubtitleSnapshotReader.Cancellation cancellation) {
            calls++;
            cancellations.add(cancellation);

            return playback;
        }
    }
}
