# Changelog

## [1.4.0] — 2026-06-18

### Added
- **`--tree` mode** — Dependency reference stack view. Shows which JAR files reference which libraries,
  with embedded libraries grouped under their containing JAR filenames. Uses box-drawing characters
  (├── └── │) for tree hierarchy.
- **`TreeFormatter`** — New output formatter that builds a tree from `depth` + `parentName` data.
  Supports `--tree --color` for ANSI output.
- **`parentName` field on `LibraryEntry`** — Tracks which embedded JAR (zip entry path) a library
  was found inside, enabling parent-child tree reconstruction.
- **`EmbeddedJarScanner` parent tracking** — Now passes the containing JAR's filename as `parentName`
  to all discovered entries.
- **Tests** — 11 `TreeFormatterTest` tests (empty, flat, nested, multi-parent, color, dedup, buildTree).
  82 tests total all green.

### Changed
- `LibraryEntry` constructor overloaded: `(g, a, v, source, depth)` and `(g, a, v, source, depth, parentName)`.
- `JarVersionInspector.runScan()` now routes to `TreeFormatter` when `--tree` is set.
- `AGENTS.md` updated with tree mode documentation and formatter table.
- `SPEC.md` — FR-08 added for `--tree` spec.
- `README.md` merge conflict resolved, tree mode example added.
- Version bumped to 1.4.0.

### Added
- **DIFF mode** — Compare two JARs: `java-lib-listing old.jar new.jar`. Shows upgraded, downgraded,
  added, removed, and unchanged libraries with version transitions. Supports `--json`, `--html`, `--color`.
- **`--html` output** — Dark-themed GitHub-style HTML report for CI/CD dashboard or browser viewing.
  Full support for single scan and DIFF reports.
- **`--color` output** — ANSI color-coded terminal output (bold headers, green versions).
- **`DiffResult`** model — Categorizes library changes into UPGRADED, DOWNGRADED, ADDED, REMOVED, UNCHANGED.
- **`DiffFormatter`** — Text formatter with icons (⬆⬇🆕❌) for each change type, color support.
- **`HtmlFormatter`** — Renders both single-scan and DIFF results as standalone HTML with dark theme.
- **`JsonFormatter.formatDiff()`** — JSON DIFF output for CI/CD pipeline parsing.
- **Tests** — 71 tests all green.

### Changed
- CLI now accepts 1 or 2 JAR paths. Single path = normal scan. Two paths = DIFF mode.
- `@Parameters` updated with `arity = "0..1"` for optional second JAR path.
- JarVersionInspector refactored into `runScan()` and `runDiff()` methods.

### Fixed
- None.

### Added
- **Deep scanner performance overhaul**: 2-segment package grouping, known-library skipping, reduced from 200+ queries to ~28.
- **fc: result filtering**: Only accepts Maven Central results where groupId matches the query's package prefix (prevents false positives like `com.braintreepayments` for Bouncy Castle queries).
- **G:A:classifier:V parsing in DEPENDENCIES scanner**: Regex-based extraction handles `org.bouncycastle:bcutil-jdk18on:jar:1.83` format correctly, even when embedded in description text with URLs.
- **Version lookup for partial-metadata entries**: `--deep` now queries Maven Central by G:A for entries found by DEPENDENCIES scanner that have group+artifact but no version.
- **Progress logging**: Deep scanner shows `[1/28] Querying: org.bouncycastle (2526 classes)` with per-group status.
- **Tests**: `getTwoSegmentPrefix` unit tests for new 2-segment grouping logic.

### Changed
- `DeepScanner` constructor accepts `Set<String> knownPrefixes` to skip already-identified libraries.
- `ScannerEngine.scan()` extracts known package prefixes from all scanners' results before passing to DeepScanner.
- `DependenciesFileScanner.parseDependencies()` rewritten with 3 sub-parsers in priority order: G:A:classifier:V → G:A:V → G:A V (space-separated).

### Fixed
- **Deep scan timeout on large JARs** (84MB / 200+ packages → <90s completion).
- **Bouncy Castle version not showing** — now correctly parses `META-INF/DEPENDENCIES` content for `bcutil-jdk18on:jar:1.83`.
- **False positive fc: results** — Maven Central `fc:` search now filters results to match package prefix.
- **DependenciesScanner URL colon issue** — descriptions containing `https://` no longer break parsing.

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
