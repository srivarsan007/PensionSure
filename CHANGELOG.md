# Changelog

All notable changes to PensionSure will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.2] - 2026-11-24

### Added
- Standalone `Launcher` entry point class that decouples application startup from `javafx.application.Application`, allowing execution via `java -jar`.
- Multi-platform automated release workflow (`.github/workflows/release.yml`) triggered by `v*` tags to compile and package standalone executable JARs for Linux (`x86_64`), Windows (`x86_64`), macOS Apple Silicon (`aarch64`), and macOS Intel (`x86_64`) with platform-specific JavaFX natives and SHA-256 checksums.
- Operating system download matrix and environment troubleshooting guidance in `README.md`.

### Changed
- Restored `mvnw` and `mvnw.cmd` Maven Wrapper scripts to the unmodified official upstream distribution with no host-specific paths.
- Renamed offline sample database seed script to `samples/seed-data.sql` and updated all documentation references.
- Configured Maven Shade Plugin to prevent `dependency-reduced-pom.xml` generation during builds.

## [1.0.1] - 2026-11-22

### Added
- Official Maven Wrapper scripts (`mvnw`, `mvnw.cmd`, `.mvn/`).
- Standardized community governance files (`CODE_OF_CONDUCT.md`, `SECURITY.md`, `CHANGELOG.md`).
- GitHub issue templates (`bug_report.yml`, `feature_request.yml`, `config.yml`), pull request template, and `CODEOWNERS`.
- Dependabot configuration for Maven and GitHub Actions dependency updates.
- Automated static analysis and code security scanning workflow via CodeQL.
- Project metadata and developer profiles in `pom.xml`.
- Technical architecture documentation in `docs/design-notes.md`.

### Changed
- Reorganized documentation visual assets to `docs/images/`.
- Updated CI matrix to include Maven artifact packaging and test verification across Ubuntu and macOS on JDK 17 and JDK 21.

## [1.0.0] - 2026-11-20

### Added
- Initial public release of the PensionSure desktop assistant.
- Field-specific fuzzy matching engine (Jaro-Winkler for names, normalized Levenshtein for addresses, 7-pattern ISO-8601 date normalisation).
- Two-tier date mismatch evaluator distinguishing formatting variance from identity divergence.
- Multi-pensioner household grouping and completion tracking.
- Deadline countdown evaluation with automated escalation tiers (`INFO`, `WARNING`, `URGENT`).
- Zero-configuration embedded SQLite database with automatic schema bootstrapping.
- Complete JUnit 5 test suite validating pure logic, boundary conditions, and benchmark cases.
