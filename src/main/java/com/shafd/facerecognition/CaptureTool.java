package com.shafd.facerecognition;

import java.io.File;
import java.util.Scanner;
import nu.pattern.OpenCV;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.highgui.HighGui;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

/**
 * Collects face samples for one person.
 *
 * Run:  mvn compile exec:java -Dexec.mainClass="com.shafd.facerecognition.CaptureTool"
 * Type the person's name, then press S to save a face and Q to quit.
 * Samples go to src/main/resources/faces/<name>/ (git ignores that folder).
 *
 * Tip: save 15 to 20 per person and vary them (turn your head a little, move
 * closer and farther, change the lighting).
 */
public class CaptureTool {

    public static void main(String[] args) {
        OpenCV.loadLocally();

        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter the person's name: ");
        String name = scanner.nextLine().trim();
        if (name.isEmpty() || name.contains("/") || name.contains("\\") || name.contains("..")) {
            System.out.println("Please use a plain name (no slashes or dots).");
            return;
        }

        File folder = new File(App.FACES_DIR, name);
        folder.mkdirs();

        // Continue after the highest existing number so nothing is overwritten.
        int next = 0;
        while (new File(folder, "face_" + next + ".jpg").exists()) {
            next++;
        }
        int savedThisRun = 0;

        CameraCapture camera = new CameraCapture();
        FaceDetector detector = new FaceDetector();
        if (!camera.open()) {
            System.out.println("Error: Could not open webcam.");
            return;
        }
        System.out.println("S = save a face, Q = quit. Saving to " + folder.getPath());

        while (true) {
            Mat frame = camera.readFrame();
            if (frame.empty()) {
                continue;
            }

            // Find the biggest face (small ones are usually false alarms).
            Rect[] faces = detector.detect(frame);
            Rect biggest = null;
            for (Rect face : faces) {
                if (biggest == null || face.area() > biggest.area()) {
                    biggest = face;
                }
            }

            // Crop BEFORE drawing, so the saved picture has no green box line in it.
            Mat clean = biggest == null ? null : detector.crop(frame, biggest);

            for (Rect face : faces) {
                Imgproc.rectangle(frame, new Point(face.x, face.y),
                        new Point(face.x + face.width, face.y + face.height), new Scalar(0, 255, 0), 2);
            }
            Imgproc.putText(frame, "Saved: " + savedThisRun + "  (S save, Q quit)", new Point(10, 25),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, new Scalar(255, 255, 255), 2);
            HighGui.imshow("Capture", frame);

            int key = HighGui.waitKey(1) & 0xFF; // -1 (no key) becomes 255, which matches nothing below
            if (key == 's' || key == 'S') {
                if (clean == null) {
                    System.out.println("No face found, try again.");
                } else {
                    Imgcodecs.imwrite(new File(folder, "face_" + next + ".jpg").getPath(), clean);
                    System.out.println("Saved face_" + next + ".jpg");
                    next++;
                    savedThisRun++;
                }
            }
            frame.release();
            if (clean != null) {
                clean.release();
            }
            if (key == 'q' || key == 'Q') {
                break;
            }
        }

        camera.release();
        HighGui.destroyAllWindows();
        System.exit(0);
    }
}
