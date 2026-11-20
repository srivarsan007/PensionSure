package com.pensionsure.matching;

import com.pensionsure.exception.IncompleteRecordException;
import com.pensionsure.model.MismatchFlag;
import com.pensionsure.model.PDARecord;
import com.pensionsure.model.Submission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that all benchmark sample cases in samples/match-cases.csv
 * execute through MismatchEngine and match expected scores and flags.
 */
public class SampleCasesTest {

    private final MismatchEngine engine = new MismatchEngine();

    public static class TestCaseRow {
        public String caseId;
        public String scenario;
        public String recordName;
        public String recordDob;
        public String recordAddress;
        public String submittedName;
        public String submittedDob;
        public String submittedAddress;
        public double expectedNameScore;
        public boolean expectedNameFlag;
        public double expectedDobScore;
        public boolean expectedDobFlag;
        public double expectedAddressScore;
        public boolean expectedAddressFlag;
        public boolean expectedOverallMismatch;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                values.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        values.add(sb.toString().trim());
        return values;
    }

    private List<TestCaseRow> loadCases() throws IOException {
        Path csvPath = Paths.get("samples", "match-cases.csv");
        if (!Files.exists(csvPath)) {
            // fallback if running from subfolder
            csvPath = Paths.get("..", "samples", "match-cases.csv");
        }
        assertTrue(Files.exists(csvPath), "samples/match-cases.csv must exist");

        List<TestCaseRow> rows = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            assertNotNull(headerLine, "CSV must have a header line");

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                List<String> cols = parseCsvLine(line);
                TestCaseRow row = new TestCaseRow();
                row.caseId = cols.get(0);
                row.scenario = cols.get(1);
                row.recordName = cols.get(2);
                row.recordDob = cols.get(3);
                row.recordAddress = cols.get(4);
                row.submittedName = cols.get(5);
                row.submittedDob = cols.get(6);
                row.submittedAddress = cols.get(7);
                row.expectedNameScore = Double.parseDouble(cols.get(8));
                row.expectedNameFlag = Boolean.parseBoolean(cols.get(9));
                row.expectedDobScore = Double.parseDouble(cols.get(10));
                row.expectedDobFlag = Boolean.parseBoolean(cols.get(11));
                row.expectedAddressScore = Double.parseDouble(cols.get(12));
                row.expectedAddressFlag = Boolean.parseBoolean(cols.get(13));
                row.expectedOverallMismatch = Boolean.parseBoolean(cols.get(14));
                rows.add(row);
            }
        }
        return rows;
    }

    private MismatchFlag findFlag(List<MismatchFlag> flags, String fieldName) {
        return flags.stream()
                .filter(f -> f.getFieldName().equalsIgnoreCase(fieldName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing flag for field: " + fieldName));
    }

    @Test
    @DisplayName("Assert all sample match cases against real MismatchEngine output")
    void testSampleCasesMatchEngineOutput() throws IOException, IncompleteRecordException {
        List<TestCaseRow> cases = loadCases();
        assertFalse(cases.isEmpty(), "At least one sample case must be loaded from CSV");

        for (TestCaseRow tc : cases) {
            PDARecord record = new PDARecord();
            record.setId(1);
            record.setPensionerId(1);
            record.setNameOnFile(tc.recordName);
            record.setDobOnFile(tc.recordDob);
            record.setAddressOnFile(tc.recordAddress);
            record.setPdaName("State Bank of India");

            Submission sub = new Submission();
            sub.setId(100);
            sub.setPensionerId(1);
            sub.setSubmittedName(tc.submittedName);
            sub.setSubmittedDob(tc.submittedDob);
            sub.setSubmittedAddress(tc.submittedAddress);
            sub.setDeadlineYear(2026);

            MatchReport report = engine.analyse(record, sub);
            List<MismatchFlag> flags = report.getFlags();

            MismatchFlag nameFlag = findFlag(flags, "name");
            MismatchFlag dobFlag = findFlag(flags, "dob");
            MismatchFlag addrFlag = findFlag(flags, "address");

            assertEquals(tc.expectedNameScore, nameFlag.getConfidenceScore(), 0.0001,
                    "[" + tc.caseId + "] Name score mismatch");
            assertEquals(tc.expectedNameFlag, nameFlag.isFlagged(),
                    "[" + tc.caseId + "] Name flag mismatch");

            assertEquals(tc.expectedDobScore, dobFlag.getConfidenceScore(), 0.0001,
                    "[" + tc.caseId + "] DOB score mismatch");
            assertEquals(tc.expectedDobFlag, dobFlag.isFlagged(),
                    "[" + tc.caseId + "] DOB flag mismatch");

            assertEquals(tc.expectedAddressScore, addrFlag.getConfidenceScore(), 0.0001,
                    "[" + tc.caseId + "] Address score mismatch");
            assertEquals(tc.expectedAddressFlag, addrFlag.isFlagged(),
                    "[" + tc.caseId + "] Address flag mismatch");

            assertEquals(tc.expectedOverallMismatch, report.hasAnyMismatch(),
                    "[" + tc.caseId + "] Overall mismatch flag mismatch");
        }
    }
}
