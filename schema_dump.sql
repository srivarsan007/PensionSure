-- PensionSure SQLite Live Database Schema Dump
-- Extracted from live database: pensionsure.db

CREATE TABLE household (
    id   INTEGER PRIMARY KEY AUTOINCREMENT,
    head_of_household_name TEXT NOT NULL
);

CREATE TABLE pensioner (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    household_id INTEGER NOT NULL,
    name         TEXT    NOT NULL,
    dob          TEXT    NOT NULL,
    pension_type TEXT    NOT NULL,
    ppo_number   TEXT,
    FOREIGN KEY (household_id) REFERENCES household(id)
);

CREATE TABLE pda_record (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    pensioner_id INTEGER NOT NULL,
    name_on_file TEXT    NOT NULL,
    dob_on_file  TEXT    NOT NULL,
    address_on_file TEXT NOT NULL,
    pda_name     TEXT,
    FOREIGN KEY (pensioner_id) REFERENCES pensioner(id)
);

CREATE TABLE submission (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    pensioner_id     INTEGER NOT NULL,
    submitted_name   TEXT,
    submitted_dob    TEXT,
    submitted_address TEXT,
    submission_date  TEXT,
    deadline_year    INTEGER NOT NULL,
    FOREIGN KEY (pensioner_id) REFERENCES pensioner(id)
);

CREATE TABLE mismatch_flag (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    submission_id    INTEGER NOT NULL,
    field_name       TEXT    NOT NULL,
    confidence_score REAL    NOT NULL,
    flagged          INTEGER NOT NULL,
    recommendation   TEXT,
    FOREIGN KEY (submission_id) REFERENCES submission(id)
);

CREATE TABLE reminder_log (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    pensioner_id     INTEGER NOT NULL,
    reminder_date    TEXT    NOT NULL,
    escalation_level TEXT    NOT NULL,
    sent             INTEGER NOT NULL,
    FOREIGN KEY (pensioner_id) REFERENCES pensioner(id)
);
