package com.pensionsure.service;

import com.pensionsure.model.Pensioner;
import com.pensionsure.model.ReminderLog;
import com.pensionsure.model.ReminderLog.EscalationLevel;
import com.pensionsure.model.Submission;
import com.pensionsure.repository.PensionerRepository;
import com.pensionsure.repository.ReminderRepository;
import com.pensionsure.repository.SubmissionRepository;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for Digital Life Certificate (DLC) deadline calculation, escalation rules,
 * and reminder auditing.
 *
 * ═══════════════════════════════════════════════════════════════════
 * DEADLINE ESCALATION RULES (Blueprint §6):
 * ───────────────────────────────────────────────────────────────────
 * Annual DLC submission deadline is November 30 of each calendar year.
 * For pensioners without a confirmed submission for the target deadline year:
 *
 *   > 14 days remaining  → EscalationLevel.INFO
 *   3–14 days remaining  → EscalationLevel.WARNING
 *   < 3 days remaining   → EscalationLevel.URGENT  (including deadline day & overdue)
 *
 * The pure calculation method {@link #calculateEscalation(LocalDate, int)} is
 * deliberately static and free of I/O so it can be thoroughly unit-tested
 * with deterministic dates.
 * ═══════════════════════════════════════════════════════════════════
 */
public class ReminderService {

    private final ReminderRepository reminderRepo;
    private final PensionerRepository pensionerRepo;
    private final SubmissionRepository submissionRepo;

    public ReminderService(Connection conn) {
        this.reminderRepo   = new ReminderRepository(conn);
        this.pensionerRepo  = new PensionerRepository(conn);
        this.submissionRepo = new SubmissionRepository(conn);
    }

    // ── Pure Logic Escalation Calculations (I/O Independent) ──────────────

    /**
     * Calculates the escalation level given a reference date and a deadline year.
     *
     * @param referenceDate the date on which the check is being performed
     * @param deadlineYear  the DLC year (e.g. 2024, targeting Nov 30, 2024)
     * @return the calculated {@link EscalationLevel}
     */
    public static EscalationLevel calculateEscalation(LocalDate referenceDate, int deadlineYear) {
        if (referenceDate == null) {
            referenceDate = LocalDate.now();
        }

        LocalDate deadlineDate = LocalDate.of(deadlineYear, Month.NOVEMBER, 30);
        long daysRemaining = ChronoUnit.DAYS.between(referenceDate, deadlineDate);

        if (daysRemaining > 14) {
            return EscalationLevel.INFO;
        } else if (daysRemaining >= 3) {
            return EscalationLevel.WARNING;
        } else {
            return EscalationLevel.URGENT;
        }
    }

    /**
     * Computes days remaining until the November 30 cutoff for the given year.
     *
     * @param referenceDate the evaluation date
     * @param deadlineYear  target year
     * @return signed count of days (negative if past deadline)
     */
    public static long getDaysRemaining(LocalDate referenceDate, int deadlineYear) {
        if (referenceDate == null) referenceDate = LocalDate.now();
        LocalDate deadlineDate = LocalDate.of(deadlineYear, Month.NOVEMBER, 30);
        return ChronoUnit.DAYS.between(referenceDate, deadlineDate);
    }

    // ── Persistence & Workflow Methods ────────────────────────────────────

    /**
     * Evaluates all pensioners, identifying those without a submission for the given
     * deadline year, computes their escalation level, and records a log entry in `reminder_log`.
     *
     * @param deadlineYear   target year (e.g. 2024)
     * @param evaluationDate date of evaluation
     * @return list of newly logged reminder entries
     */
    public List<ReminderLog> checkAndLogReminders(int deadlineYear, LocalDate evaluationDate) throws SQLException {
        List<ReminderLog> newLogs = new ArrayList<>();
        List<Pensioner> pensioners = pensionerRepo.findAll();

        for (Pensioner p : pensioners) {
            // Check if this pensioner has already submitted for this deadline year
            List<Submission> submissions = submissionRepo.findAllByPensionerId(p.getId());
            boolean hasSubmittedForYear = submissions.stream()
                .anyMatch(s -> s.getDeadlineYear() == deadlineYear);

            if (!hasSubmittedForYear) {
                EscalationLevel level = calculateEscalation(evaluationDate, deadlineYear);
                ReminderLog log = new ReminderLog(
                    p.getId(),
                    evaluationDate.toString(),
                    level,
                    true // simulated dispatch for demo
                );
                reminderRepo.save(log);
                newLogs.add(log);
            }
        }
        return newLogs;
    }

    /** Convenience overload using today's date. */
    public List<ReminderLog> checkAndLogReminders(int deadlineYear) throws SQLException {
        return checkAndLogReminders(deadlineYear, LocalDate.now());
    }

    /** Retrieves the latest reminder log recorded for a pensioner. */
    public Optional<ReminderLog> getLatestReminderForPensioner(int pensionerId) throws SQLException {
        return reminderRepo.findLatestByPensionerId(pensionerId);
    }

    /** Retrieves all reminder history. */
    public List<ReminderLog> getAllReminderLogs() throws SQLException {
        return reminderRepo.findAll();
    }
}
