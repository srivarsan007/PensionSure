# PensionSure — DLC Submission Readiness & Mismatch Assistant

**PensionSure** is a Java desktop application designed to prevent rejections of Digital Life Certificates (DLC / *Jeevan Pramaan*) for pensioners. It performs predictive pre-submission fuzzy matching against Pension Disbursing Agency (PDA) baseline records, aggregates multi-pensioner households, and orchestrates deadline escalation reminders before the annual November 30 cutoff.

---

## 1. Problem Context

In India, central, state, and defence pensioners must submit an annual Digital Life Certificate (DLC) every November to maintain continuous disbursement of their pension. Submissions frequently face rejection or delays not due to fraud, but because of:
* **Spelling Variants & Transliteration Discrepancies:** Minor variations between English transliteration on Aadhaar/submission drafts and historical bank/PDA records (e.g., *Mohan Lal* vs. *Mohann Lal*, *Srinivasan* vs. *Srinivasen*).
* **Date-of-Birth Notation Divergence:** Format variations (e.g., Indian `DD/MM/YYYY` vs. ISO `YYYY-MM-DD` vs. `MM/DD/YYYY`) causing false identity discrepancies in legacy bank software.
* **Address Variance:** Discrepancies in street abbreviations (*MG Road* vs. *Mahatma Gandhi Rd*, *Nagar* vs. *Ngr*) and PIN code formatting.
* **Biometric & Administrative Vulnerabilities:** In November 2025, parliamentary and ministerial reviews highlighted authentication failure rates among super-senior pensioners (aged 75+) due to biometric fading and record mismatches, necessitating pre-flight verification tools to validate data before submission attempts.

---

## 2. Core Architecture

The application is structured into 5 cohesive modules with strict separation between business logic, data persistence, and presentation:

```
com.pensionsure
├── model/          Plain entity beans (Household, Pensioner, PDARecord, Submission, MismatchFlag, ReminderLog)
├── matching/       Pure logic fuzzy matching engine (zero UI/DB dependencies):
│                   - NameMatchStrategy (Jaro-Winkler with prefix weighting)
│                   - DateMatchStrategy (ISO-8601 normalization & format vs identity distinction)
│                   - AddressMatchStrategy (Length-normalized Levenshtein)
│                   - MismatchEngine & MatchReport
├── repository/     JDBC DAOs with PreparedStatements (DatabaseManager, PensionerRepository,
│                   PDARecordRepository, SubmissionRepository, ReminderRepository)
├── service/        Business domain services (PensionerService, MatchingService, ReminderService)
├── scheduler/      Background daemon thread pool (ReminderScheduler using ScheduledExecutorService)
├── exception/      Checked domain exceptions (IncompleteRecordException, InvalidDateFormatException, DuplicatePensionerException)
├── ui/             JavaFX MVC controllers (MainDashboardController, RecordEntryController,
│                   MatchReportController, HouseholdDashboardController)
└── util/           DateFormatUtils (7-pattern date normalizer)
```

---

## 3. Technology Stack

* **Language:** Java 17+ (LTS)
* **GUI Framework:** JavaFX 21 (Controls & FXML)
* **Database:** SQLite 3 (via `sqlite-jdbc` with WAL mode & Foreign Keys enabled)
* **String Similarity:** Apache Commons Text 1.12 (`JaroWinklerSimilarity`, `LevenshteinDistance`)
* **Unit Testing:** JUnit 5 (Surefire plugin)
* **Build Tool:** Apache Maven

---

## 4. Quick Start & Execution

### Prerequisites
* Java 17 or higher (tested on OpenJDK 21)
* Apache Maven 3.8+

### Setup Environment
```bash
export PATH="/opt/homebrew/opt/openjdk@21/bin:/opt/homebrew/bin:$PATH"
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
```

### Run Unit & Integration Tests (18 tests)
```bash
cd PensionSure
mvn test
```

### Launch the Desktop Application
```bash
mvn javafx:run
```

Or run the shaded standalone JAR:
```bash
java -jar target/pensionsure-1.0-SNAPSHOT.jar
```

---

## 5. Verification & Test Results

The automated test suite covers all four specification benchmark cases (TC01–TC04) plus boundary testing for date normalisation, address abbreviations, deadline escalation, and household aggregation:

```
[INFO] Running com.pensionsure.matching.MismatchEngineTest
TC04 Exception message: Required field 'submittedName' is missing: DLC submission field 'submittedName' must not be empty before matching.
Address abbreviation score: 0.6471 (threshold: 0.70)
TC03 Date score: 0.9000 (FORMAT_MISMATCH_SCORE=0.90, Recommendation: Date format mismatch detected...)
TC02 Name similarity score: 0.9667 (Flagged: true)
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0 -- in com.pensionsure.matching.MismatchEngineTest
[INFO] Running com.pensionsure.service.HouseholdAggregationTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 -- in com.pensionsure.service.HouseholdAggregationTest
[INFO] Running com.pensionsure.service.ReminderServiceTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0 -- in com.pensionsure.service.ReminderServiceTest
[INFO] 
[INFO] Results: Tests run: 18, Failures: 0, Errors: 0, Skipped: 0 (BUILD SUCCESS)
```

| Suite | Focus Area | Result |
|---|---|:---:|
| `MismatchEngineTest` | TC01 (Identical), TC02 (Name typo), TC03 (Date format), TC04 (Blank field), Strategy unit tests | **8 / 8 PASS** |
| `ReminderServiceTest` | Escalation rules (>14d INFO, 14–3d WARNING, <3d URGENT, overdue), exact day arithmetic | **8 / 8 PASS** |
| `HouseholdAggregationTest` | In-memory DB multi-pensioner query, submission ratio aggregation, unsubmitted reminder logging | **2 / 2 PASS** |

---

## 6. Project Documentation
* [`dev-log.md`](dev-log.md) — Running design decisions, rationale, and module breakdown.
* [`docs/limitations.md`](docs/limitations.md) — Architectural boundaries, privacy guidelines, and out-of-scope capabilities.
* [`schema_dump.sql`](schema_dump.sql) — Complete SQLite relational schema DDL.
