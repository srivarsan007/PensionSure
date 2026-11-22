# PensionSure — Architecture & Technical Design Notes

This document details the architectural decisions, algorithmic rationale, and engineering trade-offs behind PensionSure.

---

## 1. Storage & Relational Design

- **Embedded SQLite Persistence:** The application uses SQLite via `sqlite-jdbc` with Write-Ahead Logging (WAL) enabled and foreign key enforcement (`PRAGMA foreign_keys = ON`). SQLite provides zero-configuration local persistence without requiring an external database server process.
- **ISO-8601 Date Representation:** Dates of birth are stored as canonical `YYYY-MM-DD` strings rather than epoch timestamps or vendor-specific SQL date types. Normalization occurs through `DateFormatUtils`, keeping the data layer portable and eliminating timezone drift.
- **PII Minimization:** Baseline Pension Disbursing Agency (`pda_record`) records store demographic verification fields (name, date of birth, address). Sensitive financial credentials such as full account numbers are omitted at the entity model level.
- **Atomic Submission & Flag Persistence:** `Submission` records and their associated `MismatchFlag` rows are persisted within coordinated transactions in `SubmissionRepository`, ensuring referential integrity between submissions and verification flags.

---

## 2. Fuzzy Matching Engine Architecture

The core verification pipeline in `com.pensionsure.matching` operates with strict architectural boundaries:
- **Zero I/O and Zero UI Imports:** `MismatchEngine` is completely decoupled from JavaFX and JDBC. It receives domain entities and returns an immutable `MatchReport`, allowing deterministic unit testing without database or GUI harnesses.
- **Field-Specific Similarity Strategies:**
  - **Names (`NameMatchStrategy`):** Uses Jaro-Winkler similarity. Indian names frequently exhibit mid- or end-of-word transliteration variances while the prefix remains stable. Jaro-Winkler's prefix-weighting correctly prioritizes prefix agreement over raw character distance. Any difference below 1.0 triggers an operator alert.
  - **Addresses (`AddressMatchStrategy`):** Uses length-normalized Levenshtein distance (`1.0 - (editDistance / maxLen)`). Addresses frequently differ by abbreviations ("Rd" vs "Road", "St" vs "Street") or numerical indices where shared prefixes are absent. A threshold of 0.70 absorbs minor clerical variations without failing genuine residents.
  - **Dates of Birth (`DateMatchStrategy`):** Employs a two-tier evaluation model. Input strings are parsed across common notations (`DD/MM/YYYY`, `YYYY-MM-DD`, `MM/DD/YYYY`). If two dates represent the exact same calendar day in differing formats, a score of `0.90` is assigned alongside an auto-correction recommendation. Truly conflicting calendar days receive a score of `0.0`.
- **Composite Confidence:** The overall confidence score is computed as the arithmetic mean of the individual field scores.

---

## 3. Household Aggregation & Coordination

- **Relational Beneficiary Clustering:** Pensioners sharing a residential unit are linked via `household_id`.
- **Consolidated Verification Status:** The household service evaluates all co-located pensioners against the active deadline year. It computes the household completion ratio (e.g. `1/2 submitted`) and identifies remaining members so that mobile verifiers or family members can coordinate submissions in a single visit.

---

## 4. Deadline Scheduling & Escalation

- **Pure Functional Calculation:** Deadline urgency is calculated via `ReminderService.calculateEscalation(referenceDate, deadlineYear)` as a pure function comparing the reference date to November 30:
  - `INFO`: More than 14 days remaining.
  - `WARNING`: Between 3 and 14 days remaining.
  - `URGENT`: Fewer than 3 days remaining, deadline day, or overdue.
- **Background Daemon Thread:** `ReminderScheduler` utilizes a `ScheduledExecutorService` configured with daemon threads and clean shutdown hooks in `Main.stop()`, preventing lingering background threads on application close.

---

## 5. Verification & Test Architecture

- **Test Suite Structure:**
  - `MismatchEngineTest`: Validates benchmark matching scenarios (exact match, name typo, date format divergence, missing required fields).
  - `SampleCasesTest`: Parses external CSV test cases (`samples/match-cases.csv`) and asserts exact numerical scores and flags against the engine.
  - `HouseholdAggregationTest`: Verifies multi-pensioner query aggregation and readiness tracking against an in-memory database.
  - `ReminderServiceTest`: Exhaustively verifies deadline boundaries and escalation transitions with fixed deterministic dates.
