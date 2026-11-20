package com.pensionsure.matching;

import com.pensionsure.exception.IncompleteRecordException;
import com.pensionsure.model.MismatchFlag;
import com.pensionsure.model.PDARecord;
import com.pensionsure.model.Submission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MismatchEngine} and the three {@link MatchStrategy} implementations.
 *
 * These tests exercise PURE LOGIC — no database, no JavaFX, no I/O of any kind.
 * The strategies and engine are instantiated directly. This is the key reason the
 * blueprint mandates keeping MismatchEngine free of UI/DB code: if it had any
 * dependency on JDBC or JavaFX, these tests would need a running DB and FX runtime,
 * making them slow, brittle, and environment-dependent.
 *
 * Test cases correspond 1:1 with the four test cases specified in blueprint §7.
 */
class MismatchEngineTest {

    private MismatchEngine engine;
    private NameMatchStrategy    nameStrat;
    private DateMatchStrategy    dateStrat;
    private AddressMatchStrategy addrStrat;

    // Helper: build a minimal PDARecord
    private PDARecord pda(String name, String dob, String address) {
        PDARecord r = new PDARecord();
        r.setId(1);
        r.setPensionerId(1);
        r.setNameOnFile(name);
        r.setDobOnFile(dob);
        r.setAddressOnFile(address);
        r.setPdaName("Test Bank");
        return r;
    }

    // Helper: build a Submission with all three comparable fields
    private Submission sub(String name, String dob, String address) {
        Submission s = new Submission();
        s.setId(10);
        s.setPensionerId(1);
        s.setSubmittedName(name);
        s.setSubmittedDob(dob);
        s.setSubmittedAddress(address);
        s.setDeadlineYear(2024);
        return s;
    }

    @BeforeEach
    void setUp() {
        engine    = new MismatchEngine();
        nameStrat = new NameMatchStrategy();
        dateStrat = new DateMatchStrategy();
        addrStrat = new AddressMatchStrategy();
    }

    // ══════════════════════════════════════════════════════════════════════
    // TC01: Identical name, DOB, and address on both sides → no fields flagged
    // ══════════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("TC01: Identical records — no fields should be flagged")
    void tc01_identicalRecords_noFlagsRaised() throws IncompleteRecordException {
        PDARecord  record     = pda("Ramesh Kumar", "1955-03-15", "12 MG Road, Pune 411001");
        Submission submission = sub("Ramesh Kumar", "1955-03-15", "12 MG Road, Pune 411001");

        MatchReport report = engine.analyse(record, submission);

        assertFalse(report.hasAnyMismatch(),
            "Identical records should produce zero mismatches");
        assertEquals(0, report.getMismatchCount(),
            "Mismatch count should be 0 for identical records");
        assertEquals(3, report.getFlags().size(),
            "Engine should produce exactly 3 flags (one per field)");

        // All confidence scores should be 1.0 (perfect match)
        for (MismatchFlag flag : report.getFlags()) {
            assertFalse(flag.isFlagged(),
                "Field '" + flag.getFieldName() + "' should not be flagged");
            assertEquals(1.0, flag.getConfidenceScore(), 0.001,
                "Field '" + flag.getFieldName() + "' should have score 1.0");
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // TC02: Minor name typo ("Mohan" vs "Mohann") → name field flagged
    // ══════════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("TC02: Minor name spelling difference — name field should be flagged")
    void tc02_minorNameTypo_nameFlagged() throws IncompleteRecordException {
        PDARecord  record     = pda("Mohan",  "1960-07-20", "14 Station Road, Delhi");
        Submission submission = sub("Mohann", "1960-07-20", "14 Station Road, Delhi");

        MatchReport report = engine.analyse(record, submission);

        List<MismatchFlag> flags = report.getFlags();

        // Find each field's flag
        MismatchFlag nameFlag = findFlag(flags, "name");
        MismatchFlag dobFlag  = findFlag(flags, "dob");
        MismatchFlag addrFlag = findFlag(flags, "address");

        // Name should be flagged because "Mohan" != "Mohann"
        assertTrue(nameFlag.isFlagged(),
            "A one-character typo in name should trigger a flag for operator review");
        
        // Jaro-Winkler score is high (~0.967) showing high-confidence near-match
        assertEquals(0.9667, nameFlag.getConfidenceScore(), 0.01,
            "Jaro-Winkler score for 'Mohan' vs 'Mohann' should be ~0.967");
        assertTrue(nameFlag.getRecommendation().contains("Minor name spelling difference"),
            "Recommendation should guide user regarding minor spelling variant");

        // DOB and address should NOT be flagged (they're identical)
        assertFalse(dobFlag.isFlagged(),  "DOB is identical — should not be flagged");
        assertFalse(addrFlag.isFlagged(), "Address is identical — should not be flagged");

        // Print actual score for the presentation deck's "Actual Output" column
        System.out.printf("TC02 Name similarity score: %.4f (Flagged: %s)%n",
            nameFlag.getConfidenceScore(), nameFlag.isFlagged());
    }

    // ══════════════════════════════════════════════════════════════════════
    // TC03: Same date, different format → flagged as FORMAT mismatch, not identity mismatch
    // ══════════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("TC03: Same date, different format — must be format-mismatch, NOT identity-mismatch")
    void tc03_sameDateDifferentFormat_formatMismatchNotIdentity() throws IncompleteRecordException {
        // "15/03/1955" (DD/MM/YYYY, Indian format) and "03/15/1955" (MM/DD/YYYY, US format)
        // both represent March 15, 1955.
        String onFile    = "15/03/1955";
        String submitted = "03/15/1955";

        double score = dateStrat.computeSimilarity(onFile, submitted);

        // The score should be FORMAT_MISMATCH_SCORE (0.90), NOT 0.0
        // 0.0 would mean the engine thinks they're different dates — a false rejection.
        assertEquals(DateMatchStrategy.FORMAT_MISMATCH_SCORE, score, 0.001,
            "Same date in different formats should score " + DateMatchStrategy.FORMAT_MISMATCH_SCORE +
            ", not 0.0 (which would wrongly indicate different dates)");

        // isFormatMismatchOnly() should confirm the semantic: same date, different notation
        assertTrue(dateStrat.isFormatMismatchOnly(onFile, submitted),
            "isFormatMismatchOnly() should return true for same date in different format");

        // Test through MismatchEngine
        PDARecord  record     = pda("Ramesh Kumar", onFile,    "12 MG Road, Pune");
        Submission submission = sub("Ramesh Kumar", submitted, "12 MG Road, Pune");

        MatchReport report = engine.analyse(record, submission);
        MismatchFlag dobFlag = findFlag(report.getFlags(), "dob");

        assertTrue(dobFlag.isFlagged(), "Format discrepancy should be flagged for operator awareness");
        assertEquals(DateMatchStrategy.FORMAT_MISMATCH_SCORE, dobFlag.getConfidenceScore(), 0.001);
        assertTrue(dobFlag.getRecommendation().contains("format mismatch"),
            "Recommendation must specifically state format mismatch, not identity mismatch");

        System.out.printf("TC03 Date score: %.4f (FORMAT_MISMATCH_SCORE=%.2f, Recommendation: %s)%n",
            score, DateMatchStrategy.FORMAT_MISMATCH_SCORE, dobFlag.getRecommendation());
    }

    // ══════════════════════════════════════════════════════════════════════
    // TC04: Blank required field → IncompleteRecordException thrown, no match run
    // ══════════════════════════════════════════════════════════════════════
    @Test
    @DisplayName("TC04: Blank required field — IncompleteRecordException must be thrown")
    void tc04_blankRequiredField_incompleteRecordException() {
        // submittedName is blank — the engine should refuse to analyse
        PDARecord  record     = pda("Suresh Patel", "1950-01-10", "22 Nehru Nagar, Mumbai");
        Submission submission = sub("",              "1950-01-10", "22 Nehru Nagar, Mumbai");

        IncompleteRecordException ex = assertThrows(
            IncompleteRecordException.class,
            () -> engine.analyse(record, submission),
            "Engine should throw IncompleteRecordException when submittedName is blank"
        );

        assertEquals("submittedName", ex.getMissingField(),
            "Exception should report 'submittedName' as the missing field");

        System.out.println("TC04 Exception message: " + ex.getMessage());
    }

    // ── Additional targeted strategy tests ────────────────────────────────

    @Test
    @DisplayName("NameMatchStrategy: genuinely different names should score low")
    void nameStrategy_differentNames_flagged() {
        double score = nameStrat.computeSimilarity("Ramesh Kumar", "Pradeep Singh");
        assertTrue(score < NameMatchStrategy.DEFAULT_THRESHOLD,
            "Clearly different names should score below threshold (actual: " + score + ")");
    }

    @Test
    @DisplayName("AddressMatchStrategy: abbreviation difference within reasonable similarity")
    void addressStrategy_abbreviation_withinThreshold() {
        double score = addrStrat.computeSimilarity(
            "12, MG Road, Pune 411001",
            "12, Mahatma Gandhi Road, Pune 411001"
        );
        System.out.printf("Address abbreviation score: %.4f (threshold: %.2f)%n",
            score, AddressMatchStrategy.DEFAULT_THRESHOLD);
        assertTrue(score > 0.35,
            "Abbreviated address should have a non-trivial similarity score");
    }

    @Test
    @DisplayName("DateMatchStrategy: genuinely different dates score 0.0")
    void dateStrategy_differentDates_scoresZero() {
        double score = dateStrat.computeSimilarity("1955-03-15", "1957-07-20");
        assertEquals(0.0, score, 0.001,
            "Genuinely different dates should score 0.0");
        assertTrue(dateStrat.isMismatch("1955-03-15", "1957-07-20",
                DateMatchStrategy.DEFAULT_THRESHOLD),
            "Genuinely different dates should be flagged as a mismatch");
    }

    @Test
    @DisplayName("MismatchEngine: overall confidence is average of per-field scores")
    void engine_overallConfidence_isAverage() throws IncompleteRecordException {
        // All identical → all scores 1.0 → average 1.0
        PDARecord  record     = pda("Lakshmi Devi", "1945-06-01", "5 Temple Street, Chennai");
        Submission submission = sub("Lakshmi Devi", "1945-06-01", "5 Temple Street, Chennai");
        MatchReport report = engine.analyse(record, submission);
        assertEquals(1.0, report.getOverallConfidence(), 0.001,
            "All identical fields → overall confidence should be 1.0");
    }

    // ── Private helper ────────────────────────────────────────────────────

    private MismatchFlag findFlag(List<MismatchFlag> flags, String fieldName) {
        return flags.stream()
            .filter(f -> f.getFieldName().equals(fieldName))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No flag found for field: " + fieldName));
    }
}
