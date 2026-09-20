package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

/**
 * Glues the periodic clock to the AI subtitle pipeline.
 *
 * <p>Each tick feeds the player position once. A tick that started a translation call does nothing
 * else; an idle tick repaints the current frame from the session cache, which is how an already
 * translated frame appears as soon as it is on screen without issuing a new request. Starting and
 * stopping only control the clock, so nothing here can leave a timer behind.
 */
public class SubtitlePrefetchLoop {
    /** The pipeline the loop drives; {@link AiSubtitleSessionBinder} is the production implementation. */
    public interface Pipeline {
        boolean onTick(long positionMs);

        boolean applyCurrentFrame();
    }

    public interface PositionSource {
        long getPositionMs();
    }

    private final SubtitlePrefetchTicker mTicker;
    private final Pipeline mPipeline;
    private final PositionSource mPositionSource;

    public SubtitlePrefetchLoop(SubtitlePrefetchTicker.Scheduler scheduler, Pipeline pipeline, PositionSource positionSource) {
        mPipeline = pipeline;
        mPositionSource = positionSource;
        mTicker = new SubtitlePrefetchTicker(scheduler, this::tick);
    }

    public void start() {
        mTicker.start();
    }

    public void stop() {
        mTicker.stop();
    }

    public boolean isRunning() {
        return mTicker.isRunning();
    }

    /** One check: feed the position; repaint from the cache only when no call was started. */
    boolean tick() {
        if (mPipeline == null || mPositionSource == null) {
            return false;
        }

        boolean started = mPipeline.onTick(mPositionSource.getPositionMs());

        if (!started) {
            mPipeline.applyCurrentFrame();
        }

        return started;
    }
}
