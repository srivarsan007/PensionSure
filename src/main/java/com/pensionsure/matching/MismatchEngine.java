package com.pensionsure.matching;

import com.pensionsure.exception.IncompleteRecordException;
import com.pensionsure.model.MismatchFlag;
import com.pensionsure.model.PDARecord;
import com.pensionsure.model.Submission;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates field-by-field comparison between a PDARecord and a Submission,
 * returning a {@link MatchReport} containing per-field confidence scores and flags.
 *
 * ═══════════════════════════════════════════════════════════════════
 * DESIGN CONSTRAINTS (intentional, not accidental):
 * ───────────────────────────────────────────────────────────────────
 * 1. NO UI CODE, NO DATABASE CODE in this class.
 *    MismatchEngine operates on plain Java objects and produces plain Java objects.
 *    This makes it fully unit-testable with zero mocking — a test can instantiate
 *    this class, pass in PDARecord and Submission objects, and assert on the report.
 *    If this class depended on JDBC or JavaFX, every test would need a live DB and
 *    a running FX application, making the test suite brittle and slow.
 *
 * 2. FIELD ORDERING is deterministic: name → dob → address.
 *    The report preserves this order so the UI always displays fields consistently.
 *
 * 3. EACH STRATEGY is instantiated once and reused.
 *    Strategies are stateless (they hold no per-call state), so sharing instances
 *    is safe and avoids repeated JaroWinklerSimilarity object construction.
 *
 * 4. VALIDATION BEFORE MATCHING.
 *    If a required submitted field is blank, IncompleteRecordException is thrown
 *    before any similarity computation begins. This models the real constraint:
 *    a DLC submission with a blank name/DOB/address cannot be processed, period.
 *    We don't produce a "confidence = 0" flag for missing fields — missing is a
 *    different category from mismatched.
 * ═══════════════════════════════════════════════════════════════════
 */
public class MismatchEngine {

    // Each strategy is stateless and safe to reuse across calls.
    private final NameMatchStrategy    nameStrategy    = new NameMatchStrategy();
    private final DateMatchStrategy    dateStrategy    = new DateMatchStrategy();
    private final AddressMatchStrategy addressStrategy = new AddressMatchStrategy();

    /**
     * Runs the full matching pipeline for one submission against its PDA record.
     *
     * @param record     the PDA's authoritative data for this pensioner
     * @param submission the draft DLC submission to evaluate
     * @return a MatchReport with one MismatchFlag per compared field
     * @throws IncompleteRecordException if any required submitted field is blank
     */
    public MatchReport analyse(PDARecord record, Submission submission)
            throws IncompleteRecordException {

        // ── Step 1: Validate submitted fields ────────────────────────────────
        // Guard clauses: fail fast on missing data so we don't produce a misleadingly
        // low similarity score for a blank field when the field is simply absent.
        validateField("submittedName",    submission.getSubmittedName());
        validateField("submittedDob",     submission.getSubmittedDob());
        validateField("submittedAddress", submission.getSubmittedAddress());

        // ── Step 2: Compare each field with its appropriate strategy ──────────
        List<MismatchFlag> flags = new ArrayList<>();

        // --- Name field -------------------------------------------------------
        double nameScore = nameStrategy.computeSimilarity(
            record.getNameOnFile(), submission.getSubmittedName()
        );
        boolean nameFlagged = nameStrategy.isMismatch(
            record.getNameOnFile(), submission.getSubmittedName()
        );
        flags.add(new MismatchFlag(
            submission.getId(),
            "name",
            nameScore,
            nameFlagged,
            buildNameRecommendation(nameScore, nameFlagged)
        ));

        // --- Date-of-birth field ----------------------------------------------
        double dobScore = dateStrategy.computeSimilarity(
            record.getDobOnFile(), submission.getSubmittedDob()
        );
        boolean dobFlagged = dateStrategy.isMismatch(
            record.getDobOnFile(), submission.getSubmittedDob()
        );
        // Provide richer context: format-only vs identity mismatch
        boolean isFormatOnly = dateStrategy.isFormatMismatchOnly(
            record.getDobOnFile(), submission.getSubmittedDob()
        );
        flags.add(new MismatchFlag(
            submission.getId(),
            "dob",
            dobScore,
            dobFlagged,
            buildDobRecommendation(dobScore, dobFlagged, isFormatOnly)
        ));

        // --- Address field ----------------------------------------------------
        double addrScore = addressStrategy.computeSimilarity(
            record.getAddressOnFile(), submission.getSubmittedAddress()
        );
        boolean addrFlagged = addressStrategy.isMismatch(
            record.getAddressOnFile(), submission.getSubmittedAddress()
        );
        flags.add(new MismatchFlag(
            submission.getId(),
            "address",
            addrScore,
            addrFlagged,
            buildAddressRecommendation(addrScore, addrFlagged)
        ));

        return new MatchReport(flags);
    }

    // ── Private helpers: validation ──────────────────────────────────────────

    private void validateField(String fieldName, String value) throws IncompleteRecordException {
        if (value == null || value.isBlank()) {
            throw new IncompleteRecordException(fieldName,
                "DLC submission field '" + fieldName + "' must not be empty before matching.");
        }
    }

    // ── Private helpers: recommendation text ─────────────────────────────────
    // These generate human-readable guidance rather than raw scores.
    // The text is intentionally non-technical so a pensioner's family member
    // helping with the submission can act on it without IT knowledge.

    private String buildNameRecommendation(double score, boolean flagged) {
        if (!flagged) return "Name matches PDA record. No action required.";
        if (score >= 0.75) {
            return String.format(
                "Minor name spelling difference detected (%.0f%% match). " +
                "Verify that the name in your submission exactly matches your PPO letter or passbook.",
                score * 100);
        }
        return String.format(
            "Significant name mismatch (%.0f%% match). " +
            "Contact your PDA branch to verify the name on file before submitting.",
            score * 100);
    }

    private String buildDobRecommendation(double score, boolean flagged, boolean isFormatOnly) {
        if (!flagged) {
            return "Date of birth matches PDA record. No action required.";
        }
        if (isFormatOnly) {
            return "Date format mismatch detected (same date in different notation). System normalises to ISO-8601, but verify format with PDA if required.";
        }
        return "Date of birth MISMATCH — the date entered differs from the PDA record. " +
               "This will likely cause DLC rejection. Contact your PDA immediately to verify your registered DOB.";
    }

    private String buildAddressRecommendation(double score, boolean flagged) {
        if (!flagged) return "Address is sufficiently similar to PDA record. No action required.";
        if (score >= 0.55) {
            return String.format(
                "Address differs moderately from PDA record (%.0f%% match). " +
                "Check for abbreviations or outdated address formats on file.",
                score * 100);
        }
        return String.format(
            "Address mismatch is significant (%.0f%% match). " +
            "If you have changed address, ensure PDA records are updated before DLC submission.",
            score * 100);
    }
}
