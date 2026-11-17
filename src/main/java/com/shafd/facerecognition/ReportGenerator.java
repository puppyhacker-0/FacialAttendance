package com.shafd.facerecognition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/*
    Class generates a PDF and stores it in the given path. 
    The PDF contains:
    -A summary containing the number of present, absent, and total students.
    -Section listing the present students by name, along with the time they were marked present.
    -Section listing the absent students by name.

    If the Attendance Record list is empty or null, the PDF will still be generated saying there are no records. 
 */
public class ReportGenerator {

    private static final float LEFT_MARGIN = 50;
    private static final float BOTTOM_MARGIN = 50;
    private static final float LINE_SPACING = 18;

    // Format for date and time
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Given a list of attendance records and a path to save the PDF to, 
     * generates a PDF file of the attendance report.
     */
    public void generatePdf(List<AttendanceRecord> records, String path) {

        Path outputPath = Path.of(path);

        try {
            // Create the output folder if it does not exist
            Path parent = outputPath.getParent();

            if (parent != null) {
                Files.createDirectories(parent);
            }

            // Create the PDF document
            try (PDDocument document = new PDDocument()) {

                PDType1Font font = new PDType1Font(
                        Standard14Fonts.FontName.TIMES_ROMAN
                );

                // Create the first page
                PDPage page = new PDPage(PDRectangle.LETTER);
                document.addPage(page);

                // Store the current page stream and vertical position
                PDPageContentStream[] stream = new PDPageContentStream[1];
                stream[0] = new PDPageContentStream(document, page);

                float[] y = {750};

                try {

                    // Count present and absent students
                    int presentCount = 0;
                    int absentCount = 0;

                    if (records != null) {
                        for (AttendanceRecord record : records) {
                            if (record.isPresent()) {
                                presentCount++;
                            } else {
                                absentCount++;
                            }
                        }
                    }

                    int totalCount = presentCount + absentCount;

                    // Write the report title and date
                    writeLine(document, stream, y, font,"Attendance Report");

                    writeLine(document, stream, y, font, "Generated: " + LocalDateTime.now().format(DATE_FORMAT));

                    writeLine(document, stream, y, font, "");

                    // Write attendance summary
                    writeLine(document, stream, y, font, "Present: " + presentCount + " Absent: " + absentCount +" Total: " + totalCount);

                    writeLine(document, stream, y, font, "");

                    // Write present students
                    writeLine(document, stream, y, font, "Present Students:");

                    boolean foundPresent = false;

                    if (records != null) {
                        for (AttendanceRecord record : records) {

                            if (record.isPresent()) {
                                foundPresent = true;

                                String name = record.getPerson().getName();

                                String time;

                                if (record.getTimestamp() == null) {
                                    time = "Unknown time";
                                } else {
                                    time = record.getTimestamp()
                                            .format(TIME_FORMAT);
                                }

                                writeLine(document, stream, y, font,
                                        name + " - " + time);
                            }
                        }
                    }

                    // If no present students were found, writes "None".
                    if (!foundPresent) {
                        writeLine(document, stream, y, font, "None");
                    }

                    writeLine(document, stream, y, font, "");

                    // Write absent students
                    writeLine(document, stream, y, font, "Absent Students:");

                    boolean foundAbsent = false;

                    if (records != null) {
                        for (AttendanceRecord record : records) {

                            if (!record.isPresent()) {
                                foundAbsent = true;

                                String name = record.getPerson().getName();

                                writeLine(document, stream, y, font, name);
                            }
                        }
                    }

                    // If no absent students were found, writes "None".
                    if (!foundAbsent) {
                        writeLine(document, stream, y, font, "None");
                    }

                } finally {
                    // Close the current page stream
                    if (stream[0] != null) {
                        stream[0].close();
                        stream[0] = null;
                    }
                }

                // Save the PDF
                document.save(outputPath.toFile());
            }

        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to generate attendance PDF", e
            );
        }
    }

    // Writes one line and creates a new page when needed
    private static void writeLine(
            PDDocument document,
            PDPageContentStream[] stream,
            float[] y,
            PDType1Font font,
            String text
    ) throws IOException {

        // Start a new page if there is not enough space
        if (y[0] < BOTTOM_MARGIN) {

            stream[0].close();

            PDPage newPage = new PDPage(PDRectangle.LETTER);
            document.addPage(newPage);

            stream[0] = new PDPageContentStream(document, newPage);

            y[0] = 750;
        }

        stream[0].beginText();
        stream[0].setFont(font, 12);
        stream[0].newLineAtOffset(LEFT_MARGIN, y[0]);
        stream[0].showText(text);
        stream[0].endText();

        // Lowers Y value for the next line
        y[0] -= LINE_SPACING;
    }

    // Method used for testing.
    /*
        Test 1: Generates a PDF with 3 present students and 2 absent students.
        Test 2: Generates a PDF with no attendance records.
        Test 3: Generates a PDF with 100 students to test page break.
    */
    public static void main(String[] args) {
        ReportGenerator testGenerator = new ReportGenerator();

        // Test 1
        List<AttendanceRecord> testRecords = new ArrayList<>();

        Person p1 = new Person(1, "Adam", new ArrayList<>());
        Person p2 = new Person(2, "Abraham", new ArrayList<>());
        Person p3 = new Person(3, "John", new ArrayList<>());
        Person p4 = new Person(4, "Jane", new ArrayList<>());
        Person p5 = new Person(5, "Jeff", new ArrayList<>());

        testRecords.add(new AttendanceRecord(p1, LocalDateTime.of(2026,10,24,10,0,0), true));
        testRecords.add(new AttendanceRecord(p2, LocalDateTime.of(2026,10,24,10,5,0), true));
        testRecords.add(new AttendanceRecord(p3, LocalDateTime.of(2026,10,24,10,10,0), true));
        testRecords.add(new AttendanceRecord(p4, null, false));
        testRecords.add(new AttendanceRecord(p5, null, false));

        testGenerator.generatePdf(testRecords, "out/test-1.pdf");

        // Test 2
        testGenerator.generatePdf(new ArrayList<>(), "out/test-2.pdf");

        // Test 3
        List<AttendanceRecord> testRecords3 = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            Person person = new Person(i, "Student " + i, new ArrayList<>());

            // Marks every 3rd student absent
            if (i % 3 == 0)
                testRecords3.add(new AttendanceRecord(person, null, false));
            else
                testRecords3.add(new AttendanceRecord(person, LocalDateTime.of(2026,10,24,10,0,0).plusMinutes(i), true));
        }

        testGenerator.generatePdf(testRecords3, "out/test-3.pdf");
    }

}
