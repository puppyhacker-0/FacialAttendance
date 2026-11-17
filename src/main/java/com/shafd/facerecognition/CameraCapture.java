package com.shafd.facerecognition;

import nu.pattern.OpenCV;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;

/**
 * Wraps the webcam so the rest of the app never touches VideoCapture directly.
 */
public class CameraCapture {

    private final VideoCapture camera;

    public CameraCapture() {
        // OpenCV's native library must be loaded before any OpenCV class is used.
        OpenCV.loadLocally();
        camera = new VideoCapture();
    }

    /** Opens the default webcam (index 0). Returns false if it could not be opened. */
    public boolean open() {
        return camera.open(0);
    }

    /**
     * Returns the next frame, mirrored left-to-right like a mirror.
     * Returns an empty Mat if no frame is ready yet.
     */
    public Mat readFrame() {
        Mat frame = new Mat();
        if (!camera.isOpened() || !camera.read(frame) || frame.empty()) {
            return new Mat();
        }
        // Flip here, once, so detection, drawing and saving all use the same image.
        Core.flip(frame, frame, 1);
        return frame;
    }

    /** Frees the camera so other programs can use it. */
    public void release() {
        camera.release();
    }
}
