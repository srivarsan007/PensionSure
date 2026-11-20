package com.pensionsure.service;

import com.pensionsure.exception.DuplicatePensionerException;
import com.pensionsure.exception.IncompleteRecordException;
import com.pensionsure.model.Household;
import com.pensionsure.model.Pensioner;
import com.pensionsure.model.Pensioner.PensionType;
import com.pensionsure.model.ReminderLog;
import com.pensionsure.model.ReminderLog.EscalationLevel;
import com.pensionsure.model.Submission;
import com.pensionsure.repository.PensionerRepository;
import com.pensionsure.repository.ReminderRepository;
import com.pensionsure.repository.SubmissionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration & aggregation unit tests for Module 4 (Household Aggregation)
 * and Module 5 (Reminder Escalation Logging).
 *
 * Uses an isolated, in-memory SQLite database (`jdbc:sqlite::memory:`) to ensure
 * fast, hermetic, and deterministic test execution without touching local disk files.
 */
class HouseholdAggregationTest {

    private Connection conn;
    private PensionerService pensionerService;
    private ReminderService reminderService;
    private SubmissionRepository submissionRepo;

    @BeforeEach
    void setUp() throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys=ON;");
            st.execute("CREATE TABLE household (id INTEGER PRIMARY KEY AUTOINCREMENT, head_of_household_name TEXT NOT NULL)");
            st.execute("CREATE TABLE pensioner (id INTEGER PRIMARY KEY AUTOINCREMENT, household_id INTEGER NOT NULL, name TEXT NOT NULL, dob TEXT NOT NULL, pension_type TEXT NOT NULL, ppo_number TEXT, FOREIGN KEY(household_id) REFERENCES household(id))");
            st.execute("CREATE TABLE pda_record (id INTEGER PRIMARY KEY AUTOINCREMENT, pensioner_id INTEGER NOT NULL, name_on_file TEXT NOT NULL, dob_on_file TEXT NOT NULL, address_on_file TEXT NOT NULL, pda_name TEXT, FOREIGN KEY(pensioner_id) REFERENCES pensioner(id))");
            st.execute("CREATE TABLE submission (id INTEGER PRIMARY KEY AUTOINCREMENT, pensioner_id INTEGER NOT NULL, submitted_name TEXT, submitted_dob TEXT, submitted_address TEXT, submission_date TEXT, deadline_year INTEGER NOT NULL, FOREIGN KEY(pensioner_id) REFERENCES pensioner(id))");
            st.execute("CREATE TABLE mismatch_flag (id INTEGER PRIMARY KEY AUTOINCREMENT, submission_id INTEGER NOT NULL, field_name TEXT NOT NULL, confidence_score REAL NOT NULL, flagged INTEGER NOT NULL, recommendation TEXT, FOREIGN KEY(submission_id) REFERENCES submission(id))");
            st.execute("CREATE TABLE reminder_log (id INTEGER PRIMARY KEY AUTOINCREMENT, pensioner_id INTEGER NOT NULL, reminder_date TEXT NOT NULL, escalation_level TEXT NOT NULL, sent INTEGER NOT NULL, FOREIGN KEY(pensioner_id) REFERENCES pensioner(id))");
        }

        pensionerService = new PensionerService(conn);
        reminderService  = new ReminderService(conn);
        submissionRepo   = new SubmissionRepository(conn);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }

    @Test
    @DisplayName("Household query correctly aggregates only its member pensioners")
    void testHouseholdAggregation_returnsCorrectMembers() throws SQLException, IncompleteRecordException, DuplicatePensionerException {
        // Create Household 1
        Household h1 = pensionerService.createHousehold("Sharma Family");
        // Create Household 2
        Household h2 = pensionerService.createHousehold("Verma Family");

        // Add 2 pensioners to Household 1
        Pensioner p1 = new Pensioner(0, h1.getId(), "Ramesh Sharma", "1955-03-15", PensionType.CENTRAL, "PPO-101");
        Pensioner p2 = new Pensioner(0, h1.getId(), "Sunita Sharma", "1958-06-20", PensionType.STATE, "PPO-102");
        pensionerService.registerPensioner(p1);
        pensionerService.registerPensioner(p2);

        // Add 1 pensioner to Household 2
        Pensioner p3 = new Pensioner(0, h2.getId(), "Anil Verma", "1960-01-10", PensionType.DEFENCE, "PPO-201");
        pensionerService.registerPensioner(p3);

        // Query Household 1 members
        List<Pensioner> h1Members = pensionerService.getPensionersByHousehold(h1.getId());
        assertEquals(2, h1Members.size(), "Household 1 should contain exactly 2 pensioners");
        assertTrue(h1Members.stream().anyMatch(p -> p.getName().equals("Ramesh Sharma")));
        assertTrue(h1Members.stream().anyMatch(p -> p.getName().equals("Sunita Sharma")));

        // Query Household 2 members
        List<Pensioner> h2Members = pensionerService.getPensionersByHousehold(h2.getId());
        assertEquals(1, h2Members.size(), "Household 2 should contain exactly 1 pensioner");
        assertEquals("Anil Verma", h2Members.get(0).getName());
    }

    @Test
    @DisplayName("Household submission status and reminder logging correctly identifies pending members")
    void testHouseholdReadiness_andReminderEscalation() throws SQLException, IncompleteRecordException, DuplicatePensionerException {
        // Create Household with 2 pensioners
        Household h = pensionerService.createHousehold("Patel Household");
        Pensioner p1 = new Pensioner(0, h.getId(), "Kirit Patel", "1952-11-04", PensionType.CENTRAL, "PPO-PATEL-1");
        Pensioner p2 = new Pensioner(0, h.getId(), "Geeta Patel", "1956-08-12", PensionType.STATE, "PPO-PATEL-2");
        pensionerService.registerPensioner(p1);
        pensionerService.registerPensioner(p2);

        // Add submission for p1 for deadline year 2024
        Submission sub = new Submission(0, p1.getId(), "Kirit Patel", "1952-11-04", "10 Patel Street", "2024-10-15", 2024);
        submissionRepo.save(sub);

        // Run reminder check on 2024-11-20 (10 days remaining -> WARNING level)
        LocalDate evalDate = LocalDate.of(2024, 11, 20);
        List<ReminderLog> logs = reminderService.checkAndLogReminders(2024, evalDate);

        // Only p2 should have a reminder logged because p1 has already submitted for 2024
        assertEquals(1, logs.size(), "Only unsubmitted pensioner (p2) should be logged for reminder");
        ReminderLog log = logs.get(0);
        assertEquals(p2.getId(), log.getPensionerId(), "Reminder log must target Geeta Patel");
        assertEquals(EscalationLevel.WARNING, log.getEscalationLevel(), "10 days remaining should evaluate to WARNING");
    }
}
