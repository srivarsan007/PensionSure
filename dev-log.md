# PensionSure — Developer Log

A running record of what was built in each module and the non-obvious decisions behind each choice. Intended as a learning reference for the student and a reviewer-facing explanation of the codebase.

---

## Module 1 — Foundation (models, exceptions, database, repositories)

**Built:** All six model entities (`Household`, `Pensioner`, `PDARecord`, `Submission`, `MismatchFlag`, `ReminderLog`), three custom exceptions, `DatabaseManager` (SQLite singleton), and three JDBC repositories (`PensionerRepository`, `PDARecordRepository`, `SubmissionRepository`).

**Non-obvious decisions:**

- **SQLite over MySQL.** The blueprint says "adapt types if using SQLite". SQLite was chosen because it is embedded and zero-install — a reviewer can run the JAR on any machine without setting up a database server. The schema is otherwise identical in structure. `PRAGMA foreign_keys=ON` is set explicitly because SQLite disables FK checks by default (unlike MySQL).
- **DOB stored as ISO-8601 Text, not `java.sql.Date`.** Mixing date types across the model and DB layer is a common source of timezone bugs. Keeping everything as `"YYYY-MM-DD"` strings and doing all conversion in `DateFormatUtils` keeps the model layer framework-agnostic and testable without a SQL environment.
- **`PDARecord` has no account number field.** The blueprint explicitly says "do not store full bank account numbers — last 4 digits only, if at all." Rather than adding the field with a comment, the field is simply absent at the model layer — harder to accidentally expose.
- **`SubmissionRepository` handles both `Submission` and `MismatchFlag`.** These two entities are always loaded and saved together (flags are created at the same time as the submission they describe). Co-locating them avoids the overhead of a separate `MismatchFlagRepository` and the risk of inconsistency between them.

---

## Module 2 — Submission Entry (service layer, form UI)

**Built:** `PensionerService`, `MatchingService`, `RecordEntryController`, three-tab FXML form.

**Non-obvious decisions:**

- **Three tabs in one controller, not three separate forms.** The full data entry (pensioner → PDA record → DLC draft) is one user workflow. Splitting it into three separate modal windows would require the parent window to pass state between them and would confuse users about where to go next. A `TabPane` keeps the flow linear.
- **`RecordEntryController` in two modes (new pensioner vs. submission-only).** When a returning pensioner submits a new year's DLC, tabs 1 and 2 are already filled. Jumping to tab 3 directly (via `setSubmissionMode(true)`) avoids forcing the user to re-enter data that hasn't changed.
- **Custom exceptions are caught at the controller layer, not silently swallowed.** `IncompleteRecordException` surfaces as a red validation label, not a generic error dialog. This is the blueprint's "real invalid-input path" — the exception's `getMissingField()` tells the UI exactly what to highlight.

---

## Module 3 — Matching Engine + Results Screen

**Built:** `MatchStrategy` interface, `NameMatchStrategy` (Jaro-Winkler), `DateMatchStrategy` (ISO normalisation + format vs. identity distinction), `AddressMatchStrategy` (normalised Levenshtein), `MismatchEngine`, `MatchReport`, `MatchReportController`, JUnit 5 test suite.

**Non-obvious decisions:**

- **Why Jaro-Winkler for names, not Levenshtein.** Levenshtein gives a raw edit count; for names you need to normalise by length, which creates a different threshold for short names ("Mohan" / 5 chars) vs. long names ("Subramaniam" / 11 chars). Jaro-Winkler already incorporates a length-aware score and adds a prefix bonus — relevant because Indian name transcription errors are almost always mid- or end-of-word, not at the prefix.
- **Why normalised Levenshtein for addresses, not Jaro-Winkler.** Addresses start the same way more often than names (house number + street). Jaro-Winkler's prefix bonus would give "12, MG Road" and "14, MG Road" a misleadingly high score because they share a long common suffix. Normalised Levenshtein correctly penalises the differing digit.
- **Why two severity levels for date mismatches.** "15/03/1955" vs "1955-03-15" is a formatting difference, not a real discrepancy — flagging it the same way as a wrong date would flood the user with false alarms and erode trust in the tool. `DateMatchStrategy.isFormatMismatchOnly()` provides this semantic distinction so the recommendation text can say "formatting difference only — auto-correctable" vs. "different dates — contact PDA".
- **`MismatchEngine` has zero UI or DB imports.** This is enforced deliberately: the class can be instantiated and tested with plain Java objects and no I/O. The JUnit tests prove this — they run without a database or JavaFX runtime. This is what the blueprint means by "something you can actually unit-test meaningfully."
- **Recommendations are generated at match-time, not in the UI.** The controller just displays data. The recommendation text is part of `MismatchFlag`, persisted to the DB, and can be re-read without re-running the engine. This keeps the UI fully display-only.

---

## Testing

All four blueprint TC01–TC04 test cases are real, runnable JUnit 5 tests in `MismatchEngineTest`. Running `mvn test` executes 8 unit tests in total.

### Real Test Execution Results (`mvn test`):
```
[INFO] Running com.pensionsure.matching.MismatchEngineTest
TC04 Exception message: Required field 'submittedName' is missing: DLC submission field 'submittedName' must not be empty before matching.
Address abbreviation score: 0.6471 (threshold: 0.70)
TC03 Date score: 0.9000 (FORMAT_MISMATCH_SCORE=0.90, Recommendation: Date format mismatch detected (same date in different notation). System normalises to ISO-8601, but verify format with PDA if required.)
TC02 Name similarity score: 0.9667 (Flagged: true)
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.031 s
[INFO] BUILD SUCCESS
```

| Test Case | Scenario | Expected | Actual Output | Status |
|---|---|---|---|---|
| **TC01** | Identical records ("Ramesh Kumar", "1955-03-15", "12 MG Road...") | No fields flagged, confidence 1.0 | 0 mismatches, all scores = 1.000 | **PASS** |
| **TC02** | Minor name spelling ("Mohan" vs "Mohann") | Name flagged, score < 1.0 (high similarity) | Score: 0.9667, Flagged: `true`, Rec: "Minor name spelling difference..." | **PASS** |
| **TC03** | Date format difference ("15/03/1955" vs "03/15/1955") | Format mismatch (0.90), not identity mismatch (0.0) | Score: 0.9000, Flagged: `true`, Rec: "Date format mismatch detected..." | **PASS** |
| **TC04** | Missing/blank required field (`submittedName = ""`) | `IncompleteRecordException` thrown | `IncompleteRecordException` thrown: "Required field 'submittedName' is missing" | **PASS** |

---

---

## Module 4 — Household Dashboard & History

**Built:** `HouseholdDashboardController`, `household_dashboard.fxml`, `HouseholdRepository`/`PensionerRepository` query extensions, table row models, and navigation integrations.

**Non-obvious decisions:**

- **Extending existing `household` table schema.** The `household` table (`id`, `head_of_household_name`) and foreign key on `pensioner.household_id` created in Module 1 were reused and extended without any destructive schema migration.
- **Aggregated status mapping.** For each pensioner in a household, the dashboard queries their PDA status, latest DLC submission, previous fuzzy match outcome (reusing Phase 1 `MismatchEngine` records), and Module 5 escalation level.
- **Top-level Household Summary Metrics.** Computes total pensioners, submission progress ratio (e.g., "1 / 2 submitted"), household-level worst-case urgency, and days remaining until the November 30 cutoff.

---

## Module 5 — Deadline Reminder & Escalation Scheduler

**Built:** `ReminderRepository`, `ReminderService`, `ReminderScheduler` (background `ScheduledExecutorService`), and UI escalation badges.

**Non-obvious decisions:**

- **Pure logic escalation calculation.** `ReminderService.calculateEscalation(referenceDate, deadlineYear)` is a pure function taking a date and returning an `EscalationLevel` (INFO >14d, WARNING 3–14d, URGENT <3d). This makes it 100% unit-testable without spinning up threads or mocking clocks.
- **ScheduledExecutorService with daemon threads.** Background thread pool is set to daemon mode with graceful shutdown on application exit (`Main.stop()`), ensuring no hanging background worker prevents the JVM from exiting when the user closes the window.
- **Startup + interval demo schedule.** Runs an immediate evaluation pass on application start (`initialDelay = 0`) and repeats periodically (every 2 minutes). As explicitly documented in code comments, this is an intentional demo-friendly replacement for a 24-hour cron.
- **Audit logging in `reminder_log`.** Every evaluation pass writes non-blocking audit rows to `reminder_log` for pensioners without a confirmed submission for the active deadline year.

---

## Testing & Verification

Running `mvn test` executes **18 unit and integration tests** across the matching engine, deadline calculation rules, and household aggregation queries.

### Real Test Execution Results (`mvn test`):
```
[INFO] Running com.pensionsure.matching.MismatchEngineTest
TC04 Exception message: Required field 'submittedName' is missing: DLC submission field 'submittedName' must not be empty before matching.
Address abbreviation score: 0.6471 (threshold: 0.70)
TC03 Date score: 0.9000 (FORMAT_MISMATCH_SCORE=0.90, Recommendation: Date format mismatch detected (same date in different notation). System normalises to ISO-8601, but verify format with PDA if required.)
TC02 Name similarity score: 0.9667 (Flagged: true)
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.033 s -- in com.pensionsure.matching.MismatchEngineTest
[INFO] Running com.pensionsure.service.HouseholdAggregationTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.551 s -- in com.pensionsure.service.HouseholdAggregationTest
[INFO] Running com.pensionsure.service.ReminderServiceTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.028 s -- in com.pensionsure.service.ReminderServiceTest
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 18, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

### Full Test Suite Matrix:
| Test Class | Test Case / Scenario | Expected Outcome | Actual Output | Status |
|---|---|---|---|:---:|
| `MismatchEngineTest` | **TC01**: Identical records | No fields flagged, 1.0 confidence | 0 mismatches, all scores = 1.000 | **PASS** |
| `MismatchEngineTest` | **TC02**: Minor name spelling ("Mohan" vs "Mohann") | Name flagged, score < 1.0 | Score: 0.9667, Flagged: `true` | **PASS** |
| `MismatchEngineTest` | **TC03**: Date format difference (DD/MM vs MM/DD) | Format mismatch (0.90), not identity mismatch | Score: 0.9000, Flagged: `true` | **PASS** |
| `MismatchEngineTest` | **TC04**: Missing required field (`submittedName = ""`) | `IncompleteRecordException` thrown | `IncompleteRecordException` thrown | **PASS** |
| `MismatchEngineTest` | Additional Name/Address/Date/Confidence tests (4 tests) | Threshold compliance | All asserted scores match | **PASS** |
| `ReminderServiceTest` | **Escalation INFO**: > 14 days remaining (e.g. 29d, 15d) | `EscalationLevel.INFO` | `INFO` returned | **PASS** |
| `ReminderServiceTest` | **Escalation WARNING**: 3 to 14 days remaining (14d, 10d, 3d) | `EscalationLevel.WARNING` | `WARNING` returned | **PASS** |
| `ReminderServiceTest` | **Escalation URGENT**: < 3 days, deadline day, overdue | `EscalationLevel.URGENT` | `URGENT` returned | **PASS** |
| `ReminderServiceTest` | Parameterized Days Remaining calculation (5 cases) | Exact day count | All day deltas exact | **PASS** |
| `HouseholdAggregationTest` | Multi-pensioner query by household ID | Returns exact household members | 2 members returned for H1, 1 for H2 | **PASS** |
| `HouseholdAggregationTest` | Household readiness & reminder logging | Evaluates unsubmitted members | Only unsubmitted member logged with WARNING | **PASS** |
