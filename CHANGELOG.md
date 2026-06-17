# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [1.0.0] — 2026-06-17

### Added
- Initial release
- CLI tool to scan JAR/WAR files for library versions
- Three scanner strategies:
  - **PomScanner**: reads `META-INF/maven/**/pom.properties` (Maven metadata)
  - **ManifestScanner**: reads `META-INF/MANIFEST.MF` (Implementation, Bundle, Specification versions + Class-Path)
  - **EmbeddedJarScanner**: recursively scans fat/uber JARs (up to 5 levels deep)
- Text-formatted report output with library table, manifest section, and summary
- Deduplication: merges same `groupId:artifactId`, keeps highest version
- Filtering: `--filter G:A` pattern, `--min-version` constraint
- `--verbose` mode for scan progress
- CLI flags: `--no-dedupe`, `--help`, `--version`
- Exit codes: 0 (data found), 1 (empty), 2 (error)
- Fat JAR build task (`./gradlew fatJar`)
- 45 unit + integration tests
- 5 sample JAR fixtures for testing
- CI pipeline (GitHub Actions)
