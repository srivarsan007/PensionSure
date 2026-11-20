# Contributing to PensionSure

Thank you for your interest in improving PensionSure.

1. Fork the repository and create your branch from `main`.
2. Ensure your code compiles cleanly using Java 17+ and Maven 3.9+.
3. Maintain test isolation: core fuzzy matching must not depend on database or UI imports.
4. Run all unit tests with `mvn -B clean test` and verify that all tests pass.
5. Follow existing naming conventions and keep code comments concise and professional.
6. Test UI modifications locally with `mvn javafx:run` before opening a pull request.
7. Open a pull request with a descriptive title and summary of changes.
8. Verify CI passes across all operating system targets.
