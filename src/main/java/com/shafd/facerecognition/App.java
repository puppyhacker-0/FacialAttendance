package com.shafd.facerecognition;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nu.pattern.OpenCV;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

/**
 * Connects every piece: camera -> detector -> recognizer -> tracker -> window.
 *
 * The camera loop runs on its own thread so the window never freezes. That thread
 * is the only one that touches the tracker, because the tracker is not thread-safe.
 *
 * Before running, collect samples with CaptureTool (one folder per person under
 * src/main/resources/faces/).
 */
public class App {

    /** One sub-folder per person lives here, named after the person. */
    static final String FACES_DIR = "src/main/resources/faces";

    private static final String REPORT_PATH = "out/attendance_report.pdf";

    /** A face must be recognized this many frames in a row before it counts. */
    private static final int CONFIRM_FRAMES = 3;

    private final CameraCapture camera = new CameraCapture();
    private final FaceDetector detector = new FaceDetector();
    private final FaceRecognizer recognizer = new FaceRecognizer();
    {
        // Optional: run with -Dthreshold=80 to loosen recognition without editing code.
        String t = System.getProperty("threshold");
        if (t != null) {
            recognizer.setThreshold(Double.parseDouble(t));
        }
    }
    private final AttendanceUI ui = new AttendanceUI();
    private int debugCounter = 0;
    private final List<Person> roster;

    private volatile boolean running = false;
    private Thread worker;

    public App() {
        roster = loadRoster();
        recognizer.train(roster);
        int samples = 0;
        for (Person p : roster) {
            samples += p.getSamples().size();
        }
        System.out.println("Loaded " + roster.size() + " people (" + samples + " samples).");
        if (roster.isEmpty()) {
            System.out.println("No faces found in " + FACES_DIR + ". Run CaptureTool first.");
        }
    }

    /** Builds the roster from the sub-folders of FACES_DIR. */
    static List<Person> loadRoster() {
        List<Person> people = new ArrayList<>();
        File[] folders = new File(FACES_DIR).listFiles(File::isDirectory);
        if (folders == null) {
            return people;
        }
        Arrays.sort(folders);
        int id = 1;
        for (File folder : folders) {
            List<SampleImage> samples = new ArrayList<>();
            File[] images = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".jpg"));
            if (images != null) {
                Arrays.sort(images);
                for (File image : images) {
                    // The image itself is null on purpose: the recognizer reads it from the path.
                    samples.add(new SampleImage(image.getPath(), null));
                }
            }
            if (!samples.isEmpty()) {
                people.add(new Person(id++, folder.getName(), samples));
            }
        }
        return people;
    }

    /** Called by the Start button (on the window thread), so it must return quickly. */
    private synchronized void start() {
        if (worker != null && worker.isAlive()) {
            System.out.println("Still finishing the last session, try again in a moment.");
            return;
        }
        running = true;
        worker = new Thread(this::runSession, "attendance-loop");
        worker.setDaemon(true);
        worker.start();
    }

    /** Called by the Stop button. The loop notices, finishes and writes the report. */
    private void stop() {
        running = false;
    }

    private void runSession() {
        if (!camera.open()) {
            System.out.println("Could not open the webcam.");
            running = false;
            return;
        }
        AttendanceTracker tracker = new AttendanceTracker(roster);
        Map<Person, Integer> streaks = new HashMap<>();
        ui.updateStatus(tracker.getPresent(), tracker.getAbsent());
        int shownPresent = 0;

        while (running) {
            Mat frame = camera.readFrame();
            if (frame.empty()) {
                frame.release();
                pause(10); // camera not ready yet
                continue;
            }

            // Recognize every face first, then draw (so boxes never end up inside a crop).
            Rect[] faces = detector.detect(frame);
            Person[] who = new Person[faces.length];
            Set<Person> seen = new HashSet<>();
            for (int i = 0; i < faces.length; i++) {
                Mat crop = detector.crop(frame, faces[i]);
                who[i] = recognizer.predict(crop);
                crop.release();
                if (++debugCounter % 15 == 0) { // prints about twice a second, to help tune the threshold
                    System.out.printf("closest distance %.1f -> %s%n", recognizer.getLastDistance(),
                            who[i] == null ? "Unknown" : who[i].getName());
                }
                if (who[i] != null) {
                    seen.add(who[i]);
                    if (streaks.merge(who[i], 1, Integer::sum) >= CONFIRM_FRAMES) {
                        tracker.markPresent(who[i]);
                    }
                }
            }
            streaks.keySet().retainAll(seen); // a miss resets that person's streak

            for (int i = 0; i < faces.length; i++) {
                drawLabel(frame, faces[i], who[i]);
            }

            List<Person> present = tracker.getPresent();
            if (present.size() != shownPresent) {
                shownPresent = present.size();
                ui.updateStatus(present, tracker.getAbsent());
            }
            ui.updateFrame(frame); // converts the picture right away, so it is safe to release after
            frame.release();
        }

        camera.release();
        writeReport(tracker);
    }

    private static void drawLabel(Mat frame, Rect face, Person who) {
        Scalar color = who == null ? new Scalar(0, 0, 255) : new Scalar(0, 255, 0); // BGR: red or green
        Imgproc.rectangle(frame, new Point(face.x, face.y),
                new Point(face.x + face.width, face.y + face.height), color, 2);
        String label = who == null ? "Unknown" : who.getName();
        Imgproc.putText(frame, label, new Point(face.x, Math.max(face.y - 8, 15)),
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, color, 2);
    }

    /** Prints the result and saves it as a PDF (Rand's ReportGenerator). */
    private void writeReport(AttendanceTracker tracker) {
        System.out.println("--- Attendance result ---");
        for (AttendanceRecord record : tracker.getRecords()) {
            System.out.println(record);
        }
        try {
            new ReportGenerator().generatePdf(tracker.getRecords(), REPORT_PATH);
            System.out.println("Report saved to " + REPORT_PATH);
        } catch (RuntimeException e) {
            System.out.println("Could not save the PDF report: " + e.getMessage());
        }
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        OpenCV.loadLocally();
        App app = new App();
        app.ui.setOnStart(app::start);
        app.ui.setOnStop(app::stop);
        // Closing the window exits the program, so free the camera on the way out.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            app.running = false;
            app.camera.release();
        }));
        app.ui.show();
    }
}
