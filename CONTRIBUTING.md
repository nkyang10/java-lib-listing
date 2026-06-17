# Contributing to Java Lib Listing

First off, thanks for taking the time to contribute! 🎉

## Code of Conduct

This project is governed by a **be excellent to each other** policy. Be respectful, constructive, and inclusive.

## How to Contribute

### 1. Report Bugs

Open an issue with:
- A clear title and description
- Steps to reproduce
- Expected vs actual behavior
- The JAR file (or a minimal reproduction) if relevant

### 2. Suggest Features

Open an issue with:
- What you want to achieve
- Why it's useful
- Any prior art or references

### 3. Submit Code

1. Fork the repo
2. Create a feature branch: `git checkout -b feat/your-feature`
3. Make your changes
4. **Write tests** — every new feature needs test coverage
5. Run the full suite: `./gradlew test`
6. Commit with a descriptive message
7. Push and open a PR

### 4. PR Guidelines

- Keep PRs focused — one feature or fix per PR
- Update documentation if you change behavior
- Ensure CI passes
- Tag the PR with relevant labels (bug, enhancement, docs)

## Development Setup

```bash
# Prerequisites: Java 17+
./gradlew build          # Compile + test
./gradlew test           # Run tests
./gradlew run --args="<jar-path>"   # Quick test
./gradlew fatJar         # Build standalone fat JAR
```

## Project Structure

```
src/
├── main/java/com/jarversion/
│   ├── JarVersionInspector.java   ← CLI entry point
│   ├── LibraryEntry.java          ← Data model
│   ├── ScannerEngine.java         ← Scan orchestration
│   ├── scanner/                   ← Scan strategies
│   │   ├── PomScanner.java
│   │   ├── ManifestScanner.java
│   │   └── EmbeddedJarScanner.java
│   └── output/TextFormatter.java  ← Report rendering
└── test/java/com/jarversion/
    ├── *Test.java                 ← Unit tests
    └── IntegrationTest.java       ← Integration tests
scripts/
└── create-sample-jars.sh          ← Test fixture generator
```

## Adding a New Scanner

1. Create a class in `src/main/java/com/jarversion/scanner/` implementing `scan(Path jarPath) → List<LibraryEntry>`
2. Register it in `ScannerEngine.scan()`
3. Write unit tests with mock JARs (see `PomScannerTest` for pattern)
4. Add a sample JAR to `create-sample-jars.sh`
5. Write integration tests in `IntegrationTest.java`

## Release Process

1. Update version in `build.gradle.kts`
2. Update `CHANGELOG.md`
3. Tag with `v<version>`
4. Push tag: `git push origin v<version>`
5. GitHub Release will be created automatically by CI
