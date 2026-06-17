# Changelog

## [1.1.0] — 2026-06-17

### Added
- **`--json` output mode** — Machine-readable JSON output for CI/CD pipeline consumption.
  Includes tool metadata, full library list (displayName, groupId, artifactId, version, source, depth),
  and summary by source type.
- **Dedup count in summary** — Text report now shows `Duplicates merged: N` when duplicates are
  removed during deduplication.
- **`JsonFormatter`** — New output formatter producing valid JSON with proper character escaping.
- **Test coverage** — 66 tests total (+9 new: 6 JsonFormatterTest unit tests + 3 IntegrationTest --json tests).

### Changed
- `TextFormatter.format()` now accepts `dedupCount` parameter for summary display.
- `ScannerEngine` tracks `lastDedupCount` for deduplication statistics.

## [1.0.0] — 2026-06-17

### Added
- Initial release.
- 7 library scanners:
  - **PomScanner** — `META-INF/maven/**/pom.properties`
  - **PomXmlScanner** — `META-INF/maven/**/pom.xml`
  - **ManifestScanner** — `META-INF/MANIFEST.MF` (Implementation, Bundle, Specification, Class-Path)
  - **DependenciesFileScanner** — `META-INF/DEPENDENCIES`
  - **EmbeddedJarScanner** — recursive scan of embedded JARs/ZIPs (fat JARs)
  - **DeepScanner** — class fingerprinting via Maven Central API (for shaded JARs)
- CLI options: `--verbose`, `--no-dedupe`, `--deep`, `--min-version`, `--filter`
- Text report with per-source summary.
- 57 unit/integration tests covering all scanners and edge cases.
- Fat JAR build via Gradle `fatJar` task.
