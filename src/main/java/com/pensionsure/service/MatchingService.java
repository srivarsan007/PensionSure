package com.pensionsure.service;

import com.pensionsure.exception.IncompleteRecordException;
import com.pensionsure.matching.MatchReport;
import com.pensionsure.matching.MismatchEngine;
import com.pensionsure.model.MismatchFlag;
import com.pensionsure.model.PDARecord;
import com.pensionsure.model.Submission;
import com.pensionsure.repository.PDARecordRepository;
import com.pensionsure.repository.SubmissionRepository;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates the full DLC matching workflow:
 *  1. Validate and persist the Submission.
 *  2. Load the corresponding PDARecord.
 *  3. Run MismatchEngine (pure logic, no I/O).
 *  4. Persist each MismatchFlag from the report.
 *  5. Return the MatchReport to the caller (UI controller).
 *
 * Keeping this orchestration in a service class (rather than the UI controller)
 * means the matching flow can be tested, replayed, or called from the scheduler
 * (Phase 2) without any JavaFX dependency.
 */
public class MatchingService {

    private final MismatchEngine engine = new MismatchEngine();
    private final SubmissionRepository submissionRepo;
    private final PDARecordRepository pdaRecordRepo;

    public MatchingService(Connection conn) {
        this.submissionRepo = new SubmissionRepository(conn);
        this.pdaRecordRepo  = new PDARecordRepository(conn);
    }

    /**
     * Persists a submission draft and immediately runs the matching engine against
     * the pensioner's PDA record.
     *
     * @param submission the submission draft to save and analyse
     * @return the MatchReport with per-field flags
     * @throws IllegalStateException     if no PDA record exists for the pensioner
     * @throws IncompleteRecordException if the submission has blank required fields
     * @throws SQLException              for database errors
     */
    public MatchReport submitAndAnalyse(Submission submission)
            throws SQLException, IncompleteRecordException {

        // Set submission date to today if not already supplied
        if (submission.getSubmissionDate() == null || submission.getSubmissionDate().isBlank()) {
            submission.setSubmissionDate(LocalDate.now().toString());
        }

        // Persist the submission first so MismatchFlags can reference its ID via FK
        submissionRepo.save(submission);

        // Load the PDA record — required to exist before analysis can proceed
        Optional<PDARecord> pdaOpt = pdaRecordRepo.findByPensionerId(submission.getPensionerId());
        if (pdaOpt.isEmpty()) {
            throw new IllegalStateException(
                "No PDA record found for pensioner ID " + submission.getPensionerId() +
                ". Please enter the PDA record before running the match.");
        }

        PDARecord record = pdaOpt.get();

        // Run the engine — pure logic, no I/O, fully testable in isolation
        MatchReport report = engine.analyse(record, submission);

        // Persist each flag so the match history is available for review
        for (MismatchFlag flag : report.getFlags()) {
            flag.setSubmissionId(submission.getId());
            submissionRepo.saveMismatchFlag(flag);
        }

        return report;
    }

    /**
     * Loads previously computed flags for a given submission (for the history view).
     */
    public List<MismatchFlag> loadFlagsForSubmission(int submissionId) throws SQLException {
        return submissionRepo.findFlagsBySubmissionId(submissionId);
    }

    /**
     * Returns all submissions for a given pensioner, newest first.
     */
    public List<Submission> getSubmissionsForPensioner(int pensionerId) throws SQLException {
        return submissionRepo.findAllByPensionerId(pensionerId);
    }
}
