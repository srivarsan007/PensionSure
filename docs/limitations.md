# PensionSure — System Boundaries & Intentional Limitations

This document outlines the architectural boundaries and deliberate out-of-scope decisions for the **PensionSure MVP** as defined in Section 10 of `architecture_blueprint.md`. These items represent future production scope rather than implementation oversights.

---

## 1. Out-of-Scope Capabilities (Blueprint §10)

### 1.1 Direct Disbursing Agency (PDA) / Bank API Integration
* **Why Excluded:** In India, Pension Disbursing Agencies (State Bank of India, Punjab National Bank, Central Pension Accounting Office, Defence PCDA) operate on internal core banking systems without public read APIs for third-party pension verification.
* **MVP Implementation:** PDA records are entered locally or pre-loaded as the authoritative baseline (`PDARecord` entity). This allows offline, privacy-preserving verification prior to official portal submission.
* **Future Extension:** Integration with DigiLocker or Account Aggregator (AA) frameworks once government APIs expose secure read access for authorized pensioners.

### 1.2 Optical Character Recognition (OCR) for Physical Documents
* **Why Excluded:** Adding document OCR (e.g., Tesseract, OpenCV) introduces heavy native C/C++ dependencies and high noise rates on folded, faded physical PPO booklets or passbooks.
* **MVP Implementation:** The project focuses its engineering budget where it delivers the highest reliability: the deterministic and fuzzy matching algorithms (`Jaro-Winkler`, `Levenshtein`, ISO date normalization) that detect discrepancies in structured text.
* **Future Extension:** Client-side document scan preprocessing via an isolated microservice or mobile camera integration.

### 1.3 Direct SMS / IVR Gateway Dispatch
* **Why Excluded:** Real SMS and Interactive Voice Response (IVR) gateways require external telecommunication credentials, paid API tokens (e.g., Twilio / CDAC SMS gateway), and Telecom Regulatory Authority of India (TRAI) DLT registration templates.
* **MVP Implementation:** `ReminderScheduler` and `ReminderService` execute the full periodic escalation calculation (>14d INFO, 14–3d WARNING, <3d URGENT) and log all dispatch records to the SQLite `reminder_log` audit table.
* **Future Extension:** Pluggable `NotificationChannel` interface implementations for webhook, SMS, and email providers.

---

## 2. Privacy & Data Protection Safeguards

* **Bank Account Numbers:** In strict adherence to the blueprint privacy mandate, full bank account numbers and sensitive financial identifiers are **never stored** in the database schema or model entities.
* **Local-First Architecture:** All pensioner information, mismatch reports, and reminder logs reside in an embedded, local SQLite database (`pensionsure.db`), ensuring no personal demographic information leaves the user's workstation.
