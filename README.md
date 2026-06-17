<<<<<<< HEAD
# java-lib-listing
=======
# Java Lib Listing

[![Build](https://github.com/nkyang10/java-lib-listing/actions/workflows/ci.yml/badge.svg)](https://github.com/nkyang10/java-lib-listing/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-17%2B-blue)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)
[![GitHub release](https://img.shields.io/github/v/release/nkyang10/java-lib-listing)](https://github.com/nkyang10/java-lib-listing/releases)

**Scan any JAR/WAR and instantly list every library with its version.**

A CLI tool for Java developers who need to know exactly what's inside a packaged JAR — without extracting it manually. Works with Maven-built JARs, Gradle-built JARs, Spring Boot fat JARs, OSGi bundles, and any hybrid.

---

## Why?

You've probably been here before:

- You download a JAR and have **no idea** what version of logback/jackson/Guava is inside
- You want to **compare** two JARs to see if a dependency was updated
- Your CI pipeline needs to **audit** which library versions made it into the final artifact
- Someone hands you a fat JAR and says "figure out what's in it"

**Java Lib Listing** answers all of these in one command.

## Features

| Feature | Description |
|---------|-------------|
| **Maven JARs** | Reads `META-INF/maven/**/pom.properties` for groupId:artifactId:version |
| **Gradle JARs** | Reads `META-INF/MANIFEST.MF` (Implementation, Bundle, Specification versions) |
| **Fat JARs** | Recursively scans embedded JARs up to 5 levels deep |
| **OSGi bundles** | Detects Bundle-Name/Bundle-Version from manifest |
| **Auto-dedup** | Merges same library from multiple sources, keeps highest version |
| **Filter** | `--filter G:A` to find specific libraries, `--min-version X` for version gating |
| **Verbose** | `--verbose` to see scan progress and what's being checked |
| **Clean exit codes** | 0 = data, 1 = empty, 2 = error — CI-friendly |

## Quick Start

### Option A: Download the fat JAR

```bash
curl -L -o java-lib-listing.jar \
  https://github.com/nkyang10/java-lib-listing/releases/download/v1.0.0/java-lib-listing-1.0.0-fat.jar

java -jar java-lib-listing.jar path/to/your.jar
```

### Option B: Build from source

```bash
git clone https://github.com/nkyang10/java-lib-listing.git
cd java-lib-listing

./gradlew fatJar
java -jar build/libs/java-lib-listing-1.0.0-fat.jar path/to/your.jar
```

### Option C: Run with Gradle

```bash
./gradlew run --args="path/to/your.jar"
```

## Usage

```bash
java -jar java-lib-listing.jar [options] <jar-path>
```

### Options

| Flag | Description |
|------|-------------|
| `-v`, `--verbose` | Show detailed scan progress |
| `--no-dedupe` | Disable duplicate merging |
| `--min-version X` | Only show libraries >= X (e.g. `--min-version 3.0`) |
| `--filter G:A` | Only show matching libraries (e.g. `--filter jackson`) |
| `-h`, `--help` | Print help and exit |
| `--version` | Print version and exit |

### Exit Codes

| Code | Meaning |
|------|---------|
| `0` | Libraries found and displayed |
| `1` | No library data found (empty JAR or no metadata) |
| `2` | Error (invalid path, corrupted file, etc.) |

## Examples

### Basic scan

```bash
$ java -jar java-lib-listing.jar my-app.jar

Jar Version Inspector — Report
===============================
Source: /home/user/my-app.jar
Scanned: 2026-06-17 14:30:00

Libraries (12 found):
────────────────────────────────────────────────────────────────────────────────
  ch.qos.logback:logback-classic                1.4.14
  com.fasterxml.jackson.core:jackson-databind   2.15.3
  com.google.guava:guava                        32.1.3-jre
  ...

MANIFEST Libraries:
────────────────────────────────────────────────────────────────────────────────
  MyApp                                        2.0.0           (Implementation-Version)

Summary:
────────────────────────────────────────────────────────────────────────────────
  Jar size:                      24.5 MB
  Total entries:                 12
  From pom.properties:           10
  From MANIFEST.MF:              2
  From embedded JARs:            0
```

### Scan a Spring Boot fat JAR

```bash
$ java -jar java-lib-listing.jar spring-boot-app.jar

Libraries (47 found):
────────────────────────────────────────────────────────────────────────────────
  com.mycompany:my-app                          1.0.0
    ch.qos.logback:logback-classic                1.4.14          [embedded]
    org.springframework.boot:spring-boot          3.1.5           [embedded]
      com.squareup.okhttp3:okhttp                   4.12.0          [embedded]
      com.squareup.retrofit2:retrofit               2.9.0           [embedded]
    ...
```

Nested JARs inside embedded JARs are shown with deeper indentation — the tree makes the dependency hierarchy visible at a glance.

### Filter for a specific library

```bash
$ java -jar java-lib-listing.jar --filter jackson my-app.jar

Libraries (2 found):
  com.fasterxml.jackson.core:jackson-databind   2.15.3          [embedded]
  com.fasterxml.jackson.dataformat:jackson-dataformat-xml  2.15.3 [embedded]
```

### CI usage (exit code check)

```bash
# Fail the build if any library is below version 3.0
java -jar java-lib-listing.jar --min-version 3.0 build/libs/app.jar
if [ $? -eq 1 ]; then
  echo "ERROR: No libraries found or versions too old!"
  exit 1
fi
```

## Supported Architectures

| Scenario | Detection Method |
|----------|-----------------|
| Maven-built JAR | `META-INF/maven/**/pom.properties` |
| Gradle-built JAR | `META-INF/MANIFEST.MF` (Implementation-Version) |
| Spring Boot fat JAR | Recursive embedded JAR scan (`BOOT-INF/lib/`, `WEB-INF/lib/`) |
| OSGi bundle | `Bundle-Name` / `Bundle-Version` in manifest |
| Internal/proprietary JAR | `Implementation-Title` / `Implementation-Version` in manifest |
| Any hybrid combination | All of the above simultaneously |

## Test Suite

45 tests covering:
- 6 unit test classes (scanner logic, deduplication, formatting)
- 18 integration tests against 5 real sample JARs
- Edge cases: empty JARs, missing manifests, corrupt paths, invalid files
- Multi-level deep nesting (3 levels of embedded JARs)

```bash
./gradlew test              # Run all tests
./gradlew jacocoTestReport  # Generate coverage report
```

## Sample JARs

Five sample JARs are included in `scripts/sample-jars/` for testing:

| Sample | Size | What it simulates |
|--------|------|-------------------|
| `sample1` | 2.2 KB | Maven-built JAR with 3 libraries + manifest |
| `sample2` | 2.0 KB | Fat JAR with 2 embedded libraries |
| `sample3` | 1.1 KB | OSGi bundle with Bundle-Version |
| `sample4` | 460 B | Empty JAR with no library data |
| `sample5` | 4.0 KB | **Multi-level fat JAR** — 3 levels deep, Gradle + Maven + internal sources |

Regenerate with: `bash scripts/create-sample-jars.sh`

## For AI Coding Assistants

This repository includes `AGENTS.md` — an AI-friendly instruction file that helps coding agents (Claude Code, Copilot, Codex, etc.) understand the project architecture, coding conventions, and testing expectations. See [AGENTS.md](AGENTS.md) for details.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup, PR guidelines, and how to add new scanners.

## License

MIT — see [LICENSE](LICENSE).
>>>>>>> ee14ebc (Initial release v1.0.0: JAR library version scanner)
