package com.shafd.facerecognition;

import java.util.ArrayList;
import java.util.List;
import nu.pattern.OpenCV;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

/**
 * Recognizes faces with a hand-written LBPH (Local Binary Pattern Histogram).
 *
 * Why not OpenCV's own LBPHFaceRecognizer? The org.openpnp:opencv jar is built
 * without opencv_contrib, so org.opencv.face does not exist. This class does the
 * same idea in plain Java and only uses OpenCV for image handling.
 *
 * How it works:
 *  1. Every face is turned into a 100 x 100 grayscale picture with even lighting.
 *  2. Each pixel is compared with its 8 neighbors: a neighbor that is as bright as
 *     the pixel (or brighter) gives a 1, a darker one gives a 0. The 8 digits form
 *     one number from 0 to 255 for that pixel.
 *  3. The picture is cut into an 8 x 8 grid. For each cell we count how often each
 *     number appears. All the counts together are the face's "fingerprint".
 *  4. To recognize a face we compare its fingerprint with the saved ones and take
 *     the closest. If even the closest is too far away, the face is unknown.
 */
public class FaceRecognizer {

    static final int SIZE = 100;   // faces are resized to SIZE x SIZE
    static final int GRID = 8;     // GRID x GRID cells
    static final int BINS = 256;   // one bin per possible 8-digit binary number

    /** Neighbor offsets {dy, dx}, clockwise from the top-left corner. */
    private static final int[][] NEIGHBORS = {
        {-1, -1}, {-1, 0}, {-1, 1}, {0, 1}, {1, 1}, {1, 0}, {1, -1}, {0, -1}
    };

    /** One saved fingerprint and the person it belongs to. */
    private static final class Sample {
        final Person person;
        final float[] fingerprint;

        Sample(Person person, float[] fingerprint) {
            this.person = person;
            this.fingerprint = fingerprint;
        }
    }

    // Faces farther than this from every saved fingerprint count as unknown.
    // This number is a starting guess. Tune it with real faces (see getLastDistance).
    private double threshold = 55.0;
    private double lastDistance = Double.NaN;
    private volatile List<Sample> samples = new ArrayList<>();

    public FaceRecognizer() {
        OpenCV.loadLocally();
    }

    /** Learns every person's sample images. Calling it again replaces what was learned. */
    public void train(List<Person> people) {
        List<Sample> learned = new ArrayList<>();
        if (people != null) {
            for (Person person : people) {
                if (person == null) {
                    continue;
                }
                for (SampleImage sample : person.getSamples()) {
                    if (sample == null) {
                        continue;
                    }
                    Mat image = sample.getImage();
                    if (image == null || image.empty()) {
                        // No picture in memory, so read it from disk.
                        image = Imgcodecs.imread(sample.getPath());
                    }
                    if (image == null || image.empty()) {
                        continue; // unreadable sample: skip it
                    }
                    learned.add(new Sample(person, describe(toGrayBytes(image), SIZE, SIZE)));
                }
            }
        }
        samples = learned;
    }

    /** Returns the matching Person, or null for an unknown face. */
    public Person predict(Mat face) {
        if (face == null || face.empty() || samples.isEmpty()) {
            lastDistance = Double.NaN;
            return null;
        }
        return classify(describe(toGrayBytes(face), SIZE, SIZE));
    }

    /** Faces whose best match is farther than this are "unknown". Lower = stricter. */
    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    /** Distance to the closest saved face in the last predict (NaN if none). Use it to tune the threshold. */
    public double getLastDistance() {
        return lastDistance;
    }

    // ---- the algorithm (kept free of OpenCV types so it can be tested alone) ----

    /** Finds the closest saved fingerprint. Returns its person, or null if too far. */
    Person classify(float[] fingerprint) {
        List<Sample> saved = samples;
        Person best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Sample s : saved) {
            double d = distance(fingerprint, s.fingerprint);
            if (d < bestDistance) {
                bestDistance = d;
                best = s.person;
            }
        }
        lastDistance = saved.isEmpty() ? Double.NaN : bestDistance;
        return bestDistance <= threshold ? best : null;
    }

    /** Test helper: add a fingerprint without needing an image. */
    void addSample(Person person, float[] fingerprint) {
        List<Sample> copy = new ArrayList<>(samples);
        copy.add(new Sample(person, fingerprint));
        samples = copy;
    }

    /**
     * Builds the fingerprint of a w x h grayscale picture (one byte per pixel,
     * row by row). The result has GRID * GRID * BINS numbers.
     */
    static float[] describe(byte[] gray, int w, int h) {
        if (gray == null || gray.length != w * h || w < 3 || h < 3) {
            throw new IllegalArgumentException("Expected a grayscale picture of w*h bytes");
        }
        float[] hist = new float[GRID * GRID * BINS];
        int[] pixelsInCell = new int[GRID * GRID];

        // Border pixels have no full ring of neighbors, so start one pixel inside.
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int center = gray[y * w + x] & 0xFF;
                int code = 0;
                for (int[] n : NEIGHBORS) {
                    int neighbor = gray[(y + n[0]) * w + (x + n[1])] & 0xFF;
                    code = (code << 1) | (neighbor >= center ? 1 : 0);
                }
                int cell = (y * GRID / h) * GRID + (x * GRID / w);
                hist[cell * BINS + code]++;
                pixelsInCell[cell]++;
            }
        }
        // Turn counts into fractions so every cell counts equally.
        for (int cell = 0; cell < GRID * GRID; cell++) {
            if (pixelsInCell[cell] == 0) {
                continue;
            }
            for (int b = 0; b < BINS; b++) {
                hist[cell * BINS + b] /= pixelsInCell[cell];
            }
        }
        return hist;
    }

    /** Chi-square distance: 0 means identical, bigger means more different. */
    static double distance(float[] a, float[] b) {
        double total = 0;
        for (int i = 0; i < a.length; i++) {
            double sum = a[i] + b[i];
            if (sum > 0) {
                double diff = a[i] - b[i];
                total += diff * diff / sum;
            }
        }
        return total;
    }

    // ---- OpenCV image preparation ----

    /** Grayscale, SIZE x SIZE, with evened-out lighting, as raw bytes. */
    private static byte[] toGrayBytes(Mat image) {
        Mat gray = new Mat();
        if (image.channels() == 1) {
            gray = image.clone();
        } else if (image.channels() == 4) {
            Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGRA2GRAY);
        } else {
            Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);
        }
        Mat resized = new Mat();
        Imgproc.resize(gray, resized, new Size(SIZE, SIZE));
        Mat even = new Mat();
        Imgproc.equalizeHist(resized, even); // same lighting for every face

        byte[] data = new byte[SIZE * SIZE];
        even.get(0, 0, data);
        gray.release();
        resized.release();
        even.release();
        return data;
    }
}
