package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.net.Uri;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;

import java.io.IOException;
import java.io.InputStream;

/**
 * Adapts an ExoPlayer {@link DataSource} to an {@link InputStream}, so the snapshot reader can consume
 * the same source type, headers and cookie handling the player already uses for this subtitle track.
 *
 * <p>The data source is opened lazily on the first read and closed exactly once by {@link #close()},
 * which is what the reader's single fetch needs. Nothing here changes the request: it downloads the
 * exact URL of the selected representation.
 */
public class SubtitleDataSourceInputStream extends InputStream {
    private final DataSource mDataSource;
    private final String mUrl;
    private boolean mOpened;
    private boolean mClosed;
    private boolean mEndOfStream;

    public SubtitleDataSourceInputStream(DataSource dataSource, String url) {
        mDataSource = dataSource;
        mUrl = url;
    }

    @Override
    public int read() throws IOException {
        byte[] single = new byte[1];
        int read = read(single, 0, 1);

        return read == -1 ? -1 : single[0] & 0xFF;
    }

    @Override
    public int read(byte[] target, int offset, int length) throws IOException {
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }

        if (mClosed) {
            throw new IOException("stream is closed");
        }

        if (mEndOfStream || length == 0) {
            return mEndOfStream ? -1 : 0;
        }

        if (mDataSource == null) {
            mEndOfStream = true;

            return -1;
        }

        openIfNeeded();

        int read = mDataSource.read(target, offset, length);

        if (read == -1) {
            mEndOfStream = true;
        }

        return read;
    }

    @Override
    public int available() {
        return mEndOfStream ? 0 : 1;
    }

    @Override
    public void close() throws IOException {
        if (mClosed) {
            return;
        }

        mClosed = true;

        if (mDataSource != null) {
            try {
                mDataSource.close();
            } catch (IOException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new IOException(e);
            }
        }
    }

    private void openIfNeeded() throws IOException {
        if (mOpened) {
            return;
        }

        mOpened = true;

        try {
            mDataSource.open(new DataSpec(Uri.parse(mUrl)));
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException(e);
        }
    }
}
