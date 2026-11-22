# Contributing to PensionSure

Thank you for your interest in contributing to PensionSure!

## Development Setup

1. Fork and clone the repository:
   ```bash
   git clone https://github.com/<your-username>/PensionSure.git
   cd PensionSure
   ```
2. Verify prerequisites: Java 17+ and Maven 3.9+ (or use the included `./mvnw`).
3. Create a feature or bugfix branch using descriptive naming:
   ```bash
   git checkout -b feat/your-feature-name
   # or
   git checkout -b fix/your-bugfix-name
   ```

## Building and Testing

Run the full verification suite before committing:
```bash
./mvnw -B clean verify
```
All tests must pass cleanly. If modifying UI components, launch the application locally to verify visual rendering:
```bash
./mvnw javafx:run
```

## Commit Guidelines

We follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:
- `feat:` for new capabilities
- `fix:` for bug fixes
- `test:` for test additions or adjustments
- `docs:` for documentation updates
- `chore:` for build tooling or dependency updates

## Pull Requests

1. Open a pull request against `main`.
2. Provide a clear description of the problem solved and the implementation details.
3. Ensure CI workflows run and pass on Ubuntu and macOS matrix runners.
