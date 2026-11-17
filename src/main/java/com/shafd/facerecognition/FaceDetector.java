package com.shafd.facerecognition;

import nu.pattern.OpenCV;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

/**
 * Finds faces in a frame using a Haar cascade.
 */
public class FaceDetector {

    private static final String CASCADE_PATH =
            "src/main/resources/haarcascade_frontalface_default.xml";

    private final CascadeClassifier classifier;

    public FaceDetector() {
        OpenCV.loadLocally();
        classifier = new CascadeClassifier(CASCADE_PATH);
        if (classifier.empty()) {
            throw new IllegalStateException("Could not load cascade file: " + CASCADE_PATH);
        }
    }

    /** Returns one Rect per face found. The array is empty if there are none. */
    public Rect[] detect(Mat frame) {
        if (frame == null || frame.empty()) {
            return new Rect[0];
        }
        // The cascade works on grayscale images.
        Mat gray = new Mat();
        Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);

        MatOfRect faces = new MatOfRect();
        classifier.detectMultiScale(gray, faces);
        return faces.toArray();
    }

    /** Returns a copy of the part of the frame inside the box. */
    public Mat crop(Mat frame, Rect r) {
        // clone() so the crop is not overwritten when the next frame arrives.
        return new Mat(frame, r).clone();
    }
}
