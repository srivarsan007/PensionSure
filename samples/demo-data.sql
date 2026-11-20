-- PensionSure SQLite Demo Schema & Seed Data
-- Provides realistic sample records for offline evaluation

PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS household (
    id                     INTEGER PRIMARY KEY AUTOINCREMENT,
    head_of_household_name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS pensioner (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    household_id INTEGER NOT NULL,
    name         TEXT    NOT NULL,
    dob          TEXT    NOT NULL,
    pension_type TEXT    NOT NULL,
    ppo_number   TEXT,
    FOREIGN KEY (household_id) REFERENCES household(id)
);

CREATE TABLE IF NOT EXISTS pda_record (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    pensioner_id    INTEGER NOT NULL,
    name_on_file    TEXT    NOT NULL,
    dob_on_file     TEXT    NOT NULL,
    address_on_file TEXT    NOT NULL,
    pda_name        TEXT,
    FOREIGN KEY (pensioner_id) REFERENCES pensioner(id)
);

CREATE TABLE IF NOT EXISTS submission (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    pensioner_id      INTEGER NOT NULL,
    submitted_name    TEXT,
    submitted_dob     TEXT,
    submitted_address TEXT,
    submission_date   TEXT,
    deadline_year     INTEGER NOT NULL,
    FOREIGN KEY (pensioner_id) REFERENCES pensioner(id)
);

CREATE TABLE IF NOT EXISTS mismatch_flag (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    submission_id    INTEGER NOT NULL,
    field_name       TEXT    NOT NULL,
    confidence_score REAL    NOT NULL,
    flagged          INTEGER NOT NULL,
    recommendation   TEXT,
    FOREIGN KEY (submission_id) REFERENCES submission(id)
);

CREATE TABLE IF NOT EXISTS reminder_log (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    pensioner_id     INTEGER NOT NULL,
    reminder_date    TEXT    NOT NULL,
    escalation_level TEXT    NOT NULL,
    sent             INTEGER NOT NULL,
    FOREIGN KEY (pensioner_id) REFERENCES pensioner(id)
);

-- Seed Households
INSERT INTO household (id, head_of_household_name) VALUES (1, 'Sharma Household');
INSERT INTO household (id, head_of_household_name) VALUES (2, 'Verma Household');

-- Seed Pensioners
INSERT INTO pensioner (id, household_id, name, dob, pension_type, ppo_number) VALUES
(1, 1, 'Ramesh Kumar Sharma', '1955-03-15', 'Central Civil', 'PPO/2020/00142'),
(2, 1, 'Sunita Sharma', '1958-08-22', 'Family Pension', 'PPO/2021/00871'),
(3, 2, 'Mohan Kumar', '1952-11-05', 'Defence', 'PPO/2018/09124');

-- Seed Pension Disbursing Agency (PDA) Records on file
INSERT INTO pda_record (id, pensioner_id, name_on_file, dob_on_file, address_on_file, pda_name) VALUES
(1, 1, 'Ramesh Kumar Sharma', '1955-03-15', 'Flat 4B, Sector 12, RK Puram, New Delhi 110022', 'State Bank of India CPPC'),
(2, 2, 'Sunita Sharma', '1958-08-22', 'Flat 4B, Sector 12, RK Puram, New Delhi 110022', 'State Bank of India CPPC'),
(3, 3, 'Mohan Kumar', '1952-11-05', 'House 14, Mall Road, Civil Lines, Jaipur 302006', 'Punjab National Bank CPPC');
