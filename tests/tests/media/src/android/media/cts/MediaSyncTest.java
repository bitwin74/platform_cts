/*
 * Copyright 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.media.cts;

import com.android.cts.media.R;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.cts.util.MediaUtils;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaSync;
import android.media.MediaTimestamp;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.io.IOException;
import java.lang.Long;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import java.util.LinkedList;

/**
 * Tests for the MediaSync API and local video/audio playback.
 *
 * <p>The file in res/raw used by all tests are (c) copyright 2008,
 * Blender Foundation / www.bigbuckbunny.org, and are licensed under the Creative Commons
 * Attribution 3.0 License at http://creativecommons.org/licenses/by/3.0/us/.
 */
public class MediaSyncTest extends ActivityInstrumentationTestCase2<MediaStubActivity> {
    private static final String LOG_TAG = "MediaSyncTest";

    private final long NO_TIMESTAMP = -1;
    private final int PLAYBACK_RATE_TOLERANCE_PERCENT = 2;
    private final long TIME_MEASUREMENT_TOLERANCE_US = 20000;
    final int INPUT_RESOURCE_ID =
            R.raw.video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_192kbps_44100hz;
    private final int APPLICATION_AUDIO_PERIOD_MS = 200;
    private final int TEST_MAX_SPEED = 2;

    private Context mContext;
    private Resources mResources;

    private MediaStubActivity mActivity;

    private MediaSync mMediaSync = null;
    private Surface mSurface = null;
    private AudioTrack mAudioTrack = null;

    private Decoder mDecoderVideo = null;
    private Decoder mDecoderAudio = null;
    private boolean mHasAudio = false;
    private boolean mHasVideo = false;
    private boolean mEosAudio = false;
    private boolean mEosVideo = false;
    private final Object mConditionEos = new Object();
    private final Object mConditionEosAudio = new Object();

    private int mNumBuffersReturned = 0;

    public MediaSyncTest() {
        super(MediaStubActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mActivity = getActivity();
        getInstrumentation().waitForIdleSync();
        try {
            runTestOnUiThread(new Runnable() {
                public void run() {
                    mMediaSync = new MediaSync();
                }
            });
        } catch (Throwable e) {
            e.printStackTrace();
            fail();
        }
        mContext = getInstrumentation().getTargetContext();
        mResources = mContext.getResources();
        mDecoderVideo = new Decoder(this, mMediaSync, false);
        mDecoderAudio = new Decoder(this, mMediaSync, true);
    }

    @Override
    protected void tearDown() throws Exception {
        if (mMediaSync != null) {
            mMediaSync.release();
            mMediaSync = null;
        }
        if (mDecoderAudio != null) {
            mDecoderAudio.release();
            mDecoderAudio = null;
        }
        if (mDecoderVideo != null) {
            mDecoderVideo.release();
            mDecoderVideo = null;
        }
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
        mActivity = null;
        super.tearDown();
    }

    private boolean reachedEos_l() {
        return ((!mHasVideo || mEosVideo) && (!mHasAudio || mEosAudio));
    }

    public void onEos(Decoder decoder) {
        synchronized(mConditionEosAudio) {
            if (decoder == mDecoderAudio) {
                mEosAudio = true;
                mConditionEosAudio.notify();
            }
        }

        synchronized(mConditionEos) {
            if (decoder == mDecoderVideo) {
                mEosVideo = true;
            }
            if (reachedEos_l()) {
                mConditionEos.notify();
            }
        }
    }

    /**
     * Tests setPlaybackRate is handled correctly for wrong rate.
     */
    public void testSetPlaybackRateFail() throws InterruptedException {
        final float rate = -1.0f;
        try {
            mMediaSync.setPlaybackRate(rate, MediaSync.PLAYBACK_RATE_AUDIO_MODE_RESAMPLE);
            fail("playback rate " + rate + " is not handled correctly");
        } catch (IllegalArgumentException e) {
        }

        assertTrue("The stream in test file can not be decoded",
                mDecoderAudio.setup(INPUT_RESOURCE_ID, null, Long.MAX_VALUE));

        // get audio track.
        mAudioTrack = mDecoderAudio.getAudioTrack();

        mMediaSync.setAudioTrack(mAudioTrack);

        try {
            mMediaSync.setPlaybackRate(rate, MediaSync.PLAYBACK_RATE_AUDIO_MODE_RESAMPLE);
            fail("With audio track set, playback rate " + rate
                    + " is not handled correctly");
        } catch (IllegalArgumentException e) {
        }
    }

    /**
     * Tests setPlaybackRate is handled correctly for good rate without audio track set.
     * The case for good rate with audio track set is tested in testPlaybackRate*.
     */
    public void testSetPlaybackRateSucceed() throws InterruptedException {
        final float rate = (float)TEST_MAX_SPEED;
        try {
            mMediaSync.setPlaybackRate(rate, MediaSync.PLAYBACK_RATE_AUDIO_MODE_RESAMPLE);
        } catch (IllegalArgumentException e) {
            fail("playback rate " + rate + " is not handled correctly");
        }
    }

    /**
     * Tests returning audio buffers correctly.
     */
    public void testAudioBufferReturn() throws InterruptedException {
        final int timeOutMs = 10000;
        boolean completed = runCheckAudioBuffer(INPUT_RESOURCE_ID, timeOutMs);
        if (!completed) {
            throw new RuntimeException("timed out waiting for audio buffer return");
        }
    }

    private boolean runCheckAudioBuffer(int inputResourceId, int timeOutMs) {
        final int NUM_LOOPS = 10;
        final Object condition = new Object();

        mHasAudio = true;
        if (mDecoderAudio.setup(inputResourceId, null, Long.MAX_VALUE) == false) {
            return true;
        }

        // get audio track.
        mAudioTrack = mDecoderAudio.getAudioTrack();

        mMediaSync.setAudioTrack(mAudioTrack);

        mMediaSync.setCallback(new MediaSync.Callback() {
            @Override
            public void onAudioBufferConsumed(
                    MediaSync sync, ByteBuffer byteBuffer, int bufferIndex) {
                Decoder decoderAudio = mDecoderAudio;
                if (decoderAudio != null) {
                    decoderAudio.checkReturnedAudioBuffer(byteBuffer, bufferIndex);
                    decoderAudio.releaseOutputBuffer(bufferIndex, NO_TIMESTAMP);
                    synchronized (condition) {
                        ++mNumBuffersReturned;
                        if (mNumBuffersReturned >= NUM_LOOPS) {
                            condition.notify();
                        }
                    }
                }
            }
        }, null);

        mMediaSync.setPlaybackRate(1.0f, MediaSync.PLAYBACK_RATE_AUDIO_MODE_RESAMPLE);

        synchronized (condition) {
            mDecoderAudio.start();

            try {
                condition.wait(timeOutMs);
            } catch (InterruptedException e) {
            }
            return (mNumBuffersReturned >= NUM_LOOPS);
        }
    }

    /**
     * Tests flush.
     */
    public void testFlush() throws InterruptedException {
        final int timeOutMs = 5000;
        boolean completed = runFlush(INPUT_RESOURCE_ID, timeOutMs);
        if (!completed) {
            throw new RuntimeException("timed out waiting for flush");
        }
    }

    private boolean runFlush(int inputResourceId, int timeOutMs) {
        final int INDEX_BEFORE_FLUSH = 1;
        final int INDEX_AFTER_FLUSH = 2;
        final int BUFFER_SIZE = 1024;
        final int[] returnedIndex = new int[1];
        final Object condition = new Object();

        returnedIndex[0] = -1;

        mHasAudio = true;
        if (mDecoderAudio.setup(inputResourceId, null, Long.MAX_VALUE) == false) {
            return true;
        }

        // get audio track.
        mAudioTrack = mDecoderAudio.getAudioTrack();

        mMediaSync.setAudioTrack(mAudioTrack);

        mMediaSync.setCallback(new MediaSync.Callback() {
            @Override
            public void onAudioBufferConsumed(
                    MediaSync sync, ByteBuffer byteBuffer, int bufferIndex) {
                synchronized (condition) {
                    if (returnedIndex[0] == -1) {
                        returnedIndex[0] = bufferIndex;
                        condition.notify();
                    }
                }
            }
        }, null);

        mMediaSync.setOnErrorListener(new MediaSync.OnErrorListener() {
            @Override
            public void onError(MediaSync sync, int what, int extra) {
                fail("got error from media sync (" + what + ", " + extra + ")");
            }
        }, null);

        mMediaSync.setPlaybackRate(0.0f, MediaSync.PLAYBACK_RATE_AUDIO_MODE_DEFAULT);

        ByteBuffer buffer1 = ByteBuffer.allocate(BUFFER_SIZE);
        ByteBuffer buffer2 = ByteBuffer.allocate(BUFFER_SIZE);
        mMediaSync.queueAudio(buffer1, INDEX_BEFORE_FLUSH, 0 /* presentationTimeUs */);
        mMediaSync.flush();
        mMediaSync.queueAudio(buffer2, INDEX_AFTER_FLUSH, 0 /* presentationTimeUs */);

        synchronized (condition) {
            mMediaSync.setPlaybackRate(1.0f, MediaSync.PLAYBACK_RATE_AUDIO_MODE_DEFAULT);

            try {
                condition.wait(timeOutMs);
            } catch (InterruptedException e) {
            }
            return (returnedIndex[0] == INDEX_AFTER_FLUSH);
        }
    }

    /**
     * Tests playing back audio successfully.
     */
    public void testPlayVideo() throws InterruptedException {
        playAV(INPUT_RESOURCE_ID, 5000 /* lastBufferTimestampMs */,
               false /* audio */, true /* video */, 10000 /* timeOutMs */);
    }

    /**
     * Tests playing back video successfully.
     */
    public void testPlayAudio() throws InterruptedException {
        playAV(INPUT_RESOURCE_ID, 5000 /* lastBufferTimestampMs */,
               true /* audio */, false /* video */, 10000 /* timeOutMs */);
    }

    /**
     * Tests playing back audio and video successfully.
     */
    public void testPlayAudioAndVideo() throws InterruptedException {
        playAV(INPUT_RESOURCE_ID, 5000 /* lastBufferTimestampMs */,
               true /* audio */, true /* video */, 10000 /* timeOutMs */);
    }

    /**
     * Tests playing at specified playback rate successfully.
     */
    public void testPlaybackRateQuarter() throws InterruptedException {
        playAV(INPUT_RESOURCE_ID, 2000 /* lastBufferTimestampMs */,
               true /* audio */, true /* video */, 10000 /* timeOutMs */,
               0.25f /* playbackRate */);
    }
    public void testPlaybackRateHalf() throws InterruptedException {
        playAV(INPUT_RESOURCE_ID, 4000 /* lastBufferTimestampMs */,
               true /* audio */, true /* video */, 10000 /* timeOutMs */,
               0.5f /* playbackRate */);
    }
    public void testPlaybackRateDouble() throws InterruptedException {
        playAV(INPUT_RESOURCE_ID, 8000 /* lastBufferTimestampMs */,
               true /* audio */, true /* video */, 10000 /* timeOutMs */,
               (float)TEST_MAX_SPEED /* playbackRate */);
    }

    private void playAV(
            final int inputResourceId,
            final long lastBufferTimestampMs,
            final boolean audio,
            final boolean video,
            int timeOutMs) throws InterruptedException {
        playAV(inputResourceId, lastBufferTimestampMs, audio, video, timeOutMs, 1.0f);
    }

    private void playAV(
            final int inputResourceId,
            final long lastBufferTimestampMs,
            final boolean audio,
            final boolean video,
            int timeOutMs,
            final float playbackRate) throws InterruptedException {
        final AtomicBoolean completed = new AtomicBoolean();
        Thread decodingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                completed.set(runPlayAV(inputResourceId, lastBufferTimestampMs * 1000,
                        audio, video, playbackRate));
            }
        });
        decodingThread.start();
        decodingThread.join(timeOutMs);
        if (!completed.get()) {
            throw new RuntimeException("timed out decoding to end-of-stream");
        }
    }

    private boolean runPlayAV(
            int inputResourceId,
            long lastBufferTimestampUs,
            boolean audio,
            boolean video,
            float playbackRate) {
        // allow 250ms for playback to get to stable state.
        final int PLAYBACK_RAMP_UP_TIME_MS = 250;

        final Object conditionFirstAudioBuffer = new Object();

        if (video) {
            mMediaSync.setSurface(mActivity.getSurfaceHolder().getSurface());
            mSurface = mMediaSync.createInputSurface();

            if (mDecoderVideo.setup(
                    inputResourceId, mSurface, lastBufferTimestampUs) == false) {
                return true;
            }
            mHasVideo = true;
        }

        if (audio) {
            if (mDecoderAudio.setup(inputResourceId, null, lastBufferTimestampUs) == false) {
                return true;
            }

            // get audio track.
            mAudioTrack = mDecoderAudio.getAudioTrack();

            mMediaSync.setAudioTrack(mAudioTrack);

            mMediaSync.setCallback(new MediaSync.Callback() {
                @Override
                public void onAudioBufferConsumed(
                        MediaSync sync, ByteBuffer byteBuffer, int bufferIndex) {
                    Decoder decoderAudio = mDecoderAudio;
                    if (decoderAudio != null) {
                        decoderAudio.releaseOutputBuffer(bufferIndex, NO_TIMESTAMP);
                    }
                    synchronized (conditionFirstAudioBuffer) {
                        conditionFirstAudioBuffer.notify();
                    }
                }
            }, null);

            mHasAudio = true;
        }

        mMediaSync.setPlaybackRate(playbackRate, MediaSync.PLAYBACK_RATE_AUDIO_MODE_RESAMPLE);

        synchronized (conditionFirstAudioBuffer) {
            if (video) {
                mDecoderVideo.start();
            }
            if (audio) {
                mDecoderAudio.start();

                // wait for the first audio output buffer returned by media sync.
                try {
                    conditionFirstAudioBuffer.wait();
                } catch (InterruptedException e) {
                    Log.i(LOG_TAG, "worker thread is interrupted.");
                    return true;
                }
            }
        }

        if (audio) {
            try {
                Thread.sleep(PLAYBACK_RAMP_UP_TIME_MS);
            } catch (InterruptedException e) {
                Log.i(LOG_TAG, "worker thread is interrupted during sleeping.");
                return true;
            }

            MediaTimestamp mediaTimestamp = mMediaSync.getTimestamp();
            assertTrue("No timestamp available for starting", mediaTimestamp != null);
            long checkStartTimeRealUs = System.nanoTime() / 1000;
            long checkStartTimeMediaUs = mediaTimestamp.mediaTimeUs;

            synchronized (mConditionEosAudio) {
                if (!mEosAudio) {
                    try {
                        mConditionEosAudio.wait();
                    } catch (InterruptedException e) {
                        Log.i(LOG_TAG, "worker thread is interrupted when waiting for audio EOS.");
                        return true;
                    }
                }
            }
            mediaTimestamp = mMediaSync.getTimestamp();
            assertTrue("No timestamp available for ending", mediaTimestamp != null);
            long playTimeUs = System.nanoTime() / 1000 - checkStartTimeRealUs;
            long mediaDurationUs = mediaTimestamp.mediaTimeUs - checkStartTimeMediaUs;
            assertEquals("Mediasync had error in playback rate " + playbackRate
                    + ", play time is " + playTimeUs + " vs expected " + mediaDurationUs,
                    mediaDurationUs,
                    playTimeUs * playbackRate,
                    mediaDurationUs * PLAYBACK_RATE_TOLERANCE_PERCENT / 100
                            + TIME_MEASUREMENT_TOLERANCE_US);
        }

        boolean completed = false;
        synchronized (mConditionEos) {
            if (!reachedEos_l()) {
                try {
                    mConditionEos.wait();
                } catch (InterruptedException e) {
                }
            }
            completed = reachedEos_l();
        }
        return completed;
    }

    private class Decoder extends MediaCodec.Callback {
        private final int NO_SAMPLE_RATE = -1;
        private final int NO_BUFFER_INDEX = -1;

        private MediaSyncTest mMediaSyncTest = null;
        private MediaSync mMediaSync = null;
        private boolean mIsAudio = false;
        private long mLastBufferTimestampUs = 0;

        private Surface mSurface = null;

        private AudioTrack mAudioTrack = null;

        private final Object mConditionCallback = new Object();
        private MediaExtractor mExtractor = null;
        private MediaCodec mDecoder = null;

        private final Object mAudioBufferLock = new Object();
        private List<AudioBuffer> mAudioBuffers = new LinkedList<AudioBuffer>();

        // accessed only on callback thread.
        private boolean mEos = false;
        private boolean mSignaledEos = false;

        private class AudioBuffer {
            public ByteBuffer mByteBuffer;
            public int mBufferIndex;

            public AudioBuffer(ByteBuffer byteBuffer, int bufferIndex) {
                mByteBuffer = byteBuffer;
                mBufferIndex = bufferIndex;
            }
        }

        Decoder(MediaSyncTest test, MediaSync sync, boolean isAudio) {
            mMediaSyncTest = test;
            mMediaSync = sync;
            mIsAudio = isAudio;
        }

        public boolean setup(int inputResourceId, Surface surface, long lastBufferTimestampUs) {
            if (!mIsAudio) {
                mSurface = surface;
            }
            mLastBufferTimestampUs = lastBufferTimestampUs;
            try {
                // get extrator.
                String type = mIsAudio ? "audio/" : "video/";
                mExtractor = MediaUtils.createMediaExtractorForMimeType(
                        mContext, inputResourceId, type);

                // get decoder.
                MediaFormat mediaFormat =
                    mExtractor.getTrackFormat(mExtractor.getSampleTrackIndex());
                String mimeType = mediaFormat.getString(MediaFormat.KEY_MIME);
                if (!MediaUtils.hasDecoder(mimeType)) {
                    Log.i(LOG_TAG, "No decoder found for mimeType= " + mimeType);
                    return false;
                }
                mDecoder = MediaCodec.createDecoderByType(mimeType);
                mDecoder.configure(mediaFormat, mSurface, null, 0);
                mDecoder.setCallback(this);

                return true;
            } catch (IOException e) {
                throw new RuntimeException("error reading input resource", e);
            }
        }

        public void start() {
            if (mDecoder != null) {
                mDecoder.start();
            }
        }

        public void release() {
            synchronized (mConditionCallback) {
                if (mDecoder != null) {
                    try {
                        mDecoder.stop();
                    } catch (IllegalStateException e) {
                    }
                    mDecoder.release();
                    mDecoder = null;
                }
                if (mExtractor != null) {
                    mExtractor.release();
                    mExtractor = null;
                }
            }

            if (mAudioTrack != null) {
                mAudioTrack.release();
                mAudioTrack = null;
            }
        }

        public AudioTrack getAudioTrack() {
            if (!mIsAudio) {
                throw new RuntimeException("can not create audio track for video");
            }

            if (mExtractor == null) {
                throw new RuntimeException("extrator is null");
            }

            if (mAudioTrack == null) {
                MediaFormat mediaFormat =
                    mExtractor.getTrackFormat(mExtractor.getSampleTrackIndex());
                int sampleRateInHz = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                int channelConfig = (mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == 1 ?
                        AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO);
                int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
                int minBufferSizeInBytes = AudioTrack.getMinBufferSize(
                        sampleRateInHz,
                        channelConfig,
                        audioFormat);
                final int frameCount = APPLICATION_AUDIO_PERIOD_MS * sampleRateInHz / 1000;
                final int frameSizeInBytes = Integer.bitCount(channelConfig)
                        * AudioFormat.getBytesPerSample(audioFormat);
                // ensure we consider application requirements for writing audio data
                minBufferSizeInBytes = TEST_MAX_SPEED /* speed influences buffer size */
                        * Math.max(minBufferSizeInBytes, frameCount * frameSizeInBytes);
                mAudioTrack = new AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        sampleRateInHz,
                        channelConfig,
                        audioFormat,
                        minBufferSizeInBytes,
                        AudioTrack.MODE_STREAM);
            }

            return mAudioTrack;
        }

        public void releaseOutputBuffer(int bufferIndex, long renderTimestampNs) {
            synchronized (mConditionCallback) {
                if (mDecoder != null) {
                    if (renderTimestampNs == NO_TIMESTAMP) {
                        mDecoder.releaseOutputBuffer(bufferIndex, false /* render */);
                    } else {
                        mDecoder.releaseOutputBuffer(bufferIndex, renderTimestampNs);
                    }
                }
            }
        }

        @Override
        public void onError(MediaCodec codec, MediaCodec.CodecException e) {
        }

        @Override
        public void onInputBufferAvailable(MediaCodec codec, int index) {
            synchronized (mConditionCallback) {
                if (mExtractor == null || mExtractor.getSampleTrackIndex() == -1
                        || mSignaledEos || mDecoder != codec) {
                    return;
                }

                ByteBuffer buffer = codec.getInputBuffer(index);
                int size = mExtractor.readSampleData(buffer, 0);
                long timestampUs = mExtractor.getSampleTime();
                mExtractor.advance();
                mSignaledEos = mExtractor.getSampleTrackIndex() == -1
                        || timestampUs >= mLastBufferTimestampUs;
                codec.queueInputBuffer(
                        index,
                        0,
                        size,
                        timestampUs,
                        mSignaledEos ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
            }
        }

        @Override
        public void onOutputBufferAvailable(
                MediaCodec codec, int index, MediaCodec.BufferInfo info) {
            synchronized (mConditionCallback) {
                if (mEos || mDecoder != codec) {
                    return;
                }

                mEos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;

                if (info.size > 0) {
                    if (mIsAudio) {
                        ByteBuffer outputByteBuffer = codec.getOutputBuffer(index);
                        synchronized(mAudioBufferLock) {
                            mAudioBuffers.add(new AudioBuffer(outputByteBuffer, index));
                        }
                        mMediaSync.queueAudio(
                                outputByteBuffer,
                                index,
                                info.presentationTimeUs);
                    } else {
                        codec.releaseOutputBuffer(index, info.presentationTimeUs * 1000);
                    }
                } else {
                    codec.releaseOutputBuffer(index, false);
                }
            }

            if (mEos) {
                mMediaSyncTest.onEos(this);
            }
        }

        @Override
        public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
        }

        public void checkReturnedAudioBuffer(ByteBuffer byteBuffer, int bufferIndex) {
            synchronized(mAudioBufferLock) {
                AudioBuffer audioBuffer = mAudioBuffers.get(0);
                if (audioBuffer.mByteBuffer != byteBuffer
                        || audioBuffer.mBufferIndex != bufferIndex) {
                    fail("returned buffer doesn't match what's sent");
                }
                mAudioBuffers.remove(0);
            }
        }
    }
}