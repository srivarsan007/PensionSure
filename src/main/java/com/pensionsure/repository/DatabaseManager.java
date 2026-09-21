package com.pensionsure.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Singleton that owns the single SQLite connection for the lifetime of the application.
 *
 * WHY SQLite instead of MySQL?
 * The blueprint says "adapt types if using SQLite" — for a desktop demo application,
 * SQLite is zero-install, self-contained, and ships as a single file. MySQL would
 * require the reviewer to have a running server, which is unnecessary friction. The
 * schema is otherwise identical to the blueprint's MySQL DDL.
 *
 * WHY a singleton connection?
 * SQLite's WAL mode handles concurrent reads well, but the matcher and UI both run
 * on a single thread from a DB perspective. A shared connection avoids the overhead
 * of opening/closing for every query, and SQLite's connection itself is cheap to hold.
 * Phase 2's scheduler runs on a background thread but only writes to reminder_log,
 * so serialised access through this singleton remains safe.
 */
public class DatabaseManager {

    // The DB file is created in the user's home directory so the app survives re-launches.
    private static final String DB_URL = "jdbc:sqlite:pensionsure.db";

    private static DatabaseManager instance;
    private Connection connection;

    private DatabaseManager() throws SQLException {
        connection = DriverManager.getConnection(DB_URL);
        // WAL mode improves concurrent read performance and reduces write-lock contention.
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL;");
            // Enforce foreign-key constraints — SQLite disables them by default.
            st.execute("PRAGMA foreign_keys=ON;");
        }
        initialiseSchema();
    }

    /** Thread-safe lazy initialisation (acceptable for a single-user desktop app). */
    public static synchronized DatabaseManager getInstance() throws SQLException {
        if (instance == null || instance.connection.isClosed()) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    public Connection getConnection() { return connection; }

    /**
     * Creates all tables if they don't already exist.
     * Using CREATE TABLE IF NOT EXISTS means this is safe to call on every startup —
     * existing data is never wiped.
     *
     * SQLite type notes vs. blueprint MySQL schema:
     *   INT / INTEGER   → INTEGER (SQLite is typeless; INTEGER maps to long for auto-inc PKs)
     *   VARCHAR(n)      → TEXT    (SQLite ignores length constraints anyway)
     *   DATE            → TEXT    (stored as "yyyy-MM-dd" strings — portable, sortable)
     *   DECIMAL(4,3)    → REAL    (SQLite uses 8-byte IEEE 754)
     *   BOOLEAN         → INTEGER (0 = false, 1 = true — SQLite has no native boolean)
     *   ENUM(...)       → TEXT    (constraint enforced in the Java enum, not the DB)
     */
    private void initialiseSchema() throws SQLException {
        try (Statement st = connection.createStatement()) {

            st.execute("""
                CREATE TABLE IF NOT EXISTS household (
                    id   INTEGER PRIMARY KEY AUTOINCREMENT,
                    head_of_household_name TEXT NOT NULL
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS pensioner (
                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                    household_id INTEGER NOT NULL,
                    name         TEXT    NOT NULL,
                    dob          TEXT    NOT NULL,
                    pension_type TEXT    NOT NULL,
                    ppo_number   TEXT,
                    FOREIGN KEY (household_id) REFERENCES household(id)
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS pda_record (
                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                    pensioner_id INTEGER NOT NULL,
                    name_on_file TEXT    NOT NULL,
                    dob_on_file  TEXT    NOT NULL,
                    address_on_file TEXT NOT NULL,
                    pda_name     TEXT,
                    FOREIGN KEY (pensioner_id) REFERENCES pensioner(id)
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS submission (
                    id               INTEGER PRIMARY KEY AUTOINCREMENT,
                    pensioner_id     INTEGER NOT NULL,
                    submitted_name   TEXT,
                    submitted_dob    TEXT,
                    submitted_address TEXT,
                    submission_date  TEXT,
                    deadline_year    INTEGER NOT NULL,
                    FOREIGN KEY (pensioner_id) REFERENCES pensioner(id)
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS mismatch_flag (
                    id               INTEGER PRIMARY KEY AUTOINCREMENT,
                    submission_id    INTEGER NOT NULL,
                    field_name       TEXT    NOT NULL,
                    confidence_score REAL    NOT NULL,
                    flagged          INTEGER NOT NULL,
                    recommendation   TEXT,
                    FOREIGN KEY (submission_id) REFERENCES submission(id)
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS reminder_log (
                    id               INTEGER PRIMARY KEY AUTOINCREMENT,
                    pensioner_id     INTEGER NOT NULL,
                    reminder_date    TEXT    NOT NULL,
                    escalation_level TEXT    NOT NULL,
                    sent             INTEGER NOT NULL,
                    FOREIGN KEY (pensioner_id) REFERENCES pensioner(id)
                )""");
        }
    }

    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}
