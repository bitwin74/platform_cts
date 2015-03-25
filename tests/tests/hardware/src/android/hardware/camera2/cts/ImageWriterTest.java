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

package android.hardware.camera2.cts;

import static android.hardware.camera2.cts.CameraTestUtils.*;

import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.camera2.cts.CameraTestUtils.SimpleCaptureCallback;
import android.hardware.camera2.cts.testcases.Camera2AndroidTestCase;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageWriter;
import android.os.ConditionVariable;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * <p>
 * Basic test for ImageWriter APIs. ImageWriter takes the images produced by
 * camera (via ImageReader), then the data is consumed by either camera input
 * interface or ImageReader.
 * </p>
 */
public class ImageWriterTest extends Camera2AndroidTestCase {
    private static final String TAG = "ImageWriterTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    // Max number of images can be accessed simultaneously from ImageReader.
    private static final int MAX_NUM_IMAGES = 5;
    private static final int CAMERA_OPAQUE_FORMAT = PixelFormat.OPAQUE;
    private ImageReader mReaderForWriter;
    private ImageWriter mWriter;

    @Override
    protected void tearDown() throws Exception {
        try {
            closeImageReader(mReaderForWriter);
        } finally {
            mReaderForWriter = null;
            if (mWriter != null) {
                mWriter.close();
                mWriter = null;
            }
        }

        super.tearDown();
    }

    /**
     * `
     * <p>
     * Basic YUV420_888 format ImageWriter ImageReader test that checks the
     * images produced by camera can be passed correctly by ImageWriter.
     * </p>
     * <p>
     * {@link ImageReader} reads the images produced by {@link CameraDevice}.
     * The images are then passed to ImageWriter, which produces new images that
     * are consumed by the second image reader. The images from first
     * ImageReader should be identical with the images from the second
     * ImageReader. This validates the basic image input interface of the
     * ImageWriter. Below is the data path tested:
     * <li>Explicit data copy: Dequeue an image from ImageWriter, copy the image
     * data from first ImageReader into this image, then queue this image back
     * to ImageWriter. This validates the ImageWriter explicit buffer copy
     * interface.</li>
     * </p>
     */
    public void testYuvImageWriterReaderOperation() throws Exception {
        for (String id : mCameraIds) {
            try {
                Log.i(TAG, "Testing Camera " + id);
                openDevice(id);
                readerWriterFormatTestByCamera(ImageFormat.YUV_420_888);
            } finally {
                closeDevice(id);
            }
        }
    }

    /**
     * <p>
     * Basic Opaque format ImageWriter ImageReader test that checks the images
     * produced by camera can be passed correctly by ImageWriter.
     * </p>
     * <p>
     * {@link ImageReader} reads the images produced by {@link CameraDevice}.
     * The images are then passed to ImageWriter, which produces new images that
     * are consumed by the second image reader. The images from first
     * ImageReader should be identical with the images from the second
     * ImageReader. This validates the basic image input interface of the
     * ImageWriter. Because opaque image is inaccessible by client, this test
     * only covers below path, and only the image info is validated.
     * <li>Direct image input to ImageWriter. The image from first ImageReader
     * is directly injected into ImageWriter without needing to dequeue an input
     * image. ImageWriter will migrate this opaque image into the destination
     * surface without any data copy.</li>
     * </p>
     */
    public void testOpaqueImageWriterReaderOperation() throws Exception {
        for (String id : mCameraIds) {
            try {
                Log.i(TAG, "Testing Camera " + id);
                openDevice(id);
                readerWriterFormatTestByCamera(CAMERA_OPAQUE_FORMAT);
            } finally {
                closeDevice(id);
            }
        }
    }

    private final class SimpleImageWriterListener implements ImageWriter.ImageListener {
        private final ConditionVariable imageReleased = new ConditionVariable();
        @Override
        public void onInputImageReleased(ImageWriter writer) {
            if (writer != mWriter) {
                return;
            }

            if (VERBOSE) Log.v(TAG, "Input image is released");
            imageReleased.open();
        }

        public void waitForImageReleassed(long timeoutMs) {
            if (imageReleased.block(timeoutMs)) {
                imageReleased.close();
            } else {
                fail("wait for image available timed out after " + timeoutMs + "ms");
            }
        }
    }

    private void readerWriterFormatTestByCamera(int format)  throws Exception {
        List<Size> sizes = getSortedSizesForFormat(mCamera.getId(), mCameraManager, format, null);
        Size maxSize = sizes.get(0);
        if (VERBOSE) {
            Log.v(TAG, "Testing size " + maxSize);
        }

        // Create ImageReader for camera output.
        SimpleImageReaderListener listenerForCamera  = new SimpleImageReaderListener();
        createDefaultImageReader(maxSize, format, MAX_NUM_IMAGES, listenerForCamera);

        // Create ImageReader for ImageWriter output
        SimpleImageReaderListener listenerForWriter  = new SimpleImageReaderListener();
        mReaderForWriter = createImageReader(maxSize, format, MAX_NUM_IMAGES, listenerForWriter);

        // Create ImageWriter
        Surface surface = mReaderForWriter.getSurface();
        assertNotNull("Surface from ImageReader shouldn't be null", surface);
        mWriter = ImageWriter.newInstance(surface, MAX_NUM_IMAGES);
        SimpleImageWriterListener writerImageListener = new SimpleImageWriterListener();
        mWriter.setImageListener(writerImageListener, mHandler);

        // Start capture: capture 2 images.
        List<Surface> outputSurfaces = new ArrayList<Surface>();
        outputSurfaces.add(mReader.getSurface());
        CaptureRequest.Builder requestBuilder = prepareCaptureRequestForSurfaces(outputSurfaces,
                CameraDevice.TEMPLATE_PREVIEW);
        SimpleCaptureCallback captureListener = new SimpleCaptureCallback();
        // Capture 1st image.
        startCapture(requestBuilder.build(), /*repeating*/false, captureListener, mHandler);
        // Capture 2nd image.
        startCapture(requestBuilder.build(), /*repeating*/false, captureListener, mHandler);

        // Image from the first ImageReader.
        Image image1 = null;
        // ImageWriter input image.
        Image inputImage = null;
        // Image from the second ImageReader.
        Image outputImage2 = null;
        if (format == CAMERA_OPAQUE_FORMAT) {
            assertTrue("First ImageReader should be opaque",
                    mReader.isOpaque());
            assertTrue("Second ImageReader should be opaque",
                    mReaderForWriter.isOpaque());
            assertTrue("Format of first ImageReader should be opaque",
                    mReader.getImageFormat() == CAMERA_OPAQUE_FORMAT);
            assertTrue(" Format of second ImageReader should be opaque",
                    mReaderForWriter.getImageFormat() == CAMERA_OPAQUE_FORMAT);
        } else {
            // Test case 1: Explicit data copy, only applicable for explicit formats.

            // Get 1st image from first ImageReader, and copy the data to ImageWrtier input image
            image1 = listenerForCamera.getImage(CAPTURE_IMAGE_TIMEOUT_MS);
            inputImage = mWriter.dequeueInputImage();
            inputImage.setTimestamp(image1.getTimestamp());
            ImageCopy(image1, inputImage);
            mCollector.expectTrue(
                    "ImageWriter 1st input image should match camera 1st output image",
                    isImageStronglyEqual(inputImage, image1));
            mWriter.queueInputImage(inputImage);
            outputImage2 = listenerForWriter.getImage(CAPTURE_IMAGE_TIMEOUT_MS);

            mCollector.expectTrue("ImageWriter 1st output image should match 1st input image",
                    isImageStronglyEqual(image1, outputImage2));
            if (DEBUG) {
                String img1FileName = DEBUG_FILE_NAME_BASE + "/" + maxSize + "_image1_copy.yuv";
                String outputImg1FileName = DEBUG_FILE_NAME_BASE + "/" + maxSize
                        + "_outputImage2_copy.yuv";
                dumpFile(img1FileName, getDataFromImage(image1));
                dumpFile(outputImg1FileName, getDataFromImage(outputImage2));
            }
            // No need to close inputImage, as it is sent to the surface after queueInputImage;
            image1.close();
            outputImage2.close();

            // Make sure ImageWriter listener callback is fired.
            writerImageListener.waitForImageReleassed(CAPTURE_IMAGE_TIMEOUT_MS);
        }

        // Test case 2: Directly inject the image into ImageWriter: works for all formats.

        // Get 2nd image and queue it directly to ImageWrier
        image1 = listenerForCamera.getImage(CAPTURE_IMAGE_TIMEOUT_MS);
        // make a copy of image1 data, as it will be closed after queueInputImage;
        byte[] img1Data = getDataFromImage(image1);
        if (DEBUG) {
            String img2FileName = DEBUG_FILE_NAME_BASE + "/" + maxSize + "_image2.yuv";
            dumpFile(img2FileName, img1Data);
        }
        mWriter.queueInputImage(image1);
        outputImage2 = listenerForWriter.getImage(CAPTURE_IMAGE_TIMEOUT_MS);
        byte[] outputImage2Data = getDataFromImage(outputImage2);

        mCollector.expectTrue("ImageWriter 2nd output image should match camera 2nd output image",
                Arrays.equals(img1Data, outputImage2Data));

        if (DEBUG) {
            String outputImg2FileName = DEBUG_FILE_NAME_BASE + "/" + maxSize + "_outputImage2.yuv";
            dumpFile(outputImg2FileName, outputImage2Data);
        }
        // No need to close inputImage, as it is sent to the surface after queueInputImage;
        outputImage2.close();

        // Make sure ImageWriter listener callback is fired.
        writerImageListener.waitForImageReleassed(CAPTURE_IMAGE_TIMEOUT_MS);

        stopCapture(/*fast*/false);
    }
}