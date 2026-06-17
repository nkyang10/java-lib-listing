# Java Lib Listing — Project Specification
[GitHub](https://github.com/nkyang10/java-lib-listing)

## 1. 概述

Jar Version Inspector（以下簡稱 JVI）係一個 CLI 工具，用途係 scan 一個 JAR 檔案並以純文字格式列出所有 embedded libraries 及其版本。

### 1.1 解決問題

- Developer 成日睇唔到 JAR 入面用緊邊個 library version
- Maven/Gradle 嘅 dependency tree 唔容易對比已打包 JAR 嘅實際內容
- Fat JAR / uber JAR 更加難逐個 library 去睇

### 1.2 目標用戶

- Java developer（Maven / Gradle / Spring Boot project）
- CI/CD pipeline 用來做 dependency 版本審計
- Code reviewer 想快速確認 library version

---

## 2. 功能需求

### FR-01: 基本 JAR Scan

- **輸入**: JAR 檔案路徑（必須存在，.jar/.war/.zip）
- **輸出**: 純文字表格，列出所有 library + version
- **Exit Code**: 0 (有結果) / 1 (無 library 資料) / 2 (錯誤)

### FR-02: Maven pom.properties Scan

- 掃描 `META-INF/maven/{groupId}/{artifactId}/pom.properties`
- 提取 `groupId`、`artifactId`、`version`
- 輸出格式 `groupId:artifactId:version`

### FR-03: MANIFEST.MF Scan

- 掃描 `META-INF/MANIFEST.MF`
- 提取 `Implementation-Title` / `Implementation-Version`
- 提取 `Bundle-Name` / `Bundle-Version`（OSGi bundle）
- 提取 `Specification-Title` / `Specification-Version`
- 提取 `Class-Path` 指向嘅外部 JAR（只記錄路徑同 filename）

### FR-04: Embedded JAR Scan（Fat JAR）

- 遞迴掃描 JAR 內嘅 embedded JAR/ZIP entries
- 對每個 embedded JAR 同樣執行 FR-02 / FR-03
- 避免無限遞迴（max depth = 5）
- 標註每一層嘅 nest depth（indentation 表示）

### FR-05: 去重整合

- 相同 `groupId:artifactId` 合併為一個 entry
- 保留最新版本（semver 比較）
- 顯示被合併嘅重複數量

### FR-06: 輸出格式

```
Jar Version Inspector — Report
===============================
Source: /path/to/app.jar
Scanned: 2026-06-17 14:30:00

Libraries (12 found, 3 deduplicated):
────────────────────────────────────────────────────────────────
 ch.qos.logback:logback-classic         1.4.14
 com.fasterxml.jackson.core:jackson-databind  2.15.3
 com.google.guava:guava                 32.1.3-jre
 org.slf4j:slf4j-api                   2.0.9
 org.springframework.boot:spring-boot   3.1.5
  └─ org.springframework:spring-core    6.0.13      [embedded]
  └─ org.springframework:spring-beans   6.0.13      [embedded]
 ...

MANIFEST Libraries:
────────────────────────────────────────────────────────────────
 spring-boot-loader                    3.1.5        (Implementation-Version)

Summary:
────────────────────────────────────────────────────────────────
  Jar size:              24.5 MB
  Total entries:         15
  From pom.properties:   10
  From MANIFEST.MF:      3
  From embedded JARs:    2
  Duplicates merged:     3
```

### FR-07: 進階選項（Optional）

- `--verbose` / `-v` — 顯示詳細 scan 過程
- `--json` — JSON 格式輸出（future）
- `--dedupe` — on/off（default on）
- `--min-version` — 只顯示指定 version 以上嘅 library
- `--filter G:A` — 只顯示特定 group:artifact

---

## 3. 系統架構

### 3.1 Component Diagram

```
┌─────────────────────────────────────────────┐
│              JarVersionInspector (CLI)       │
│  ┌─────────────┐                            │
│  │  pom.xml     │  Picocli Command Line      │
│  │  Gradle      │  ← args: path, options     │
│  └─────────────┘                            │
│         │                                    │
│         ▼                                    │
│  ┌─────────────────┐                        │
│  │  ScannerEngine   │  Orchestrate scanners  │
│  │  (entry point)   │                        │
│  └──┬────┬────┬────┘                        │
│     │    │    │                              │
│     ▼    ▼    ▼                              │
│  ┌───┐ ┌────┐ ┌────────┐                    │
│  │Pom│ │Man │ │Embedded│  Layer per source   │
│  │Sc │ │fest│ │JarSc   │                    │
│  │an │ │Scan│ │anner   │                    │
│  └───┘ └────┘ └────────┘                    │
│     │         │       │                      │
│     ▼         ▼       ▼                      │
│  ┌──────────────────────┐                   │
│  │  LibraryEntry (Model) │                   │
│  └──────────────────────┘                   │
│         │                                    │
│         ▼                                    │
│  ┌──────────────────┐                       │
│  │  TextFormatter    │  Output rendering     │
│  └──────────────────┘                       │
└─────────────────────────────────────────────┘
```

### 3.2 核心類別

| Class | Responsibility |
|-------|---------------|
| `JarVersionInspector` | CLI entry point, 解析 args, 啟動 scan |
| `ScannerEngine` | Orchestrate all scanners, 去重, 排序 |
| `LibraryEntry` | Model: groupId, artifactId, version, source, depth |
| `PomScanner` | 掃描 META-INF/maven/**/pom.properties |
| `ManifestScanner` | 掃描 META-INF/MANIFEST.MF |
| `EmbeddedJarScanner` | 遞迴掃 embedded JAR/ZIP |
| `TextFormatter` | 輸出純文字報告 |

### 3.3 Data Flow

```
JAR Path → JarVersionInspector (CLI)
  → ScannerEngine.scan(path)
    → PomScanner.scan(jarFS) → List<LibraryEntry>
    → ManifestScanner.scan(jarFS) → List<LibraryEntry>
    → EmbeddedJarScanner.scan(jarFS) → List<LibraryEntry>
  → ScannerEngine.deduplicate(entries) → List<LibraryEntry>
  → ScannerEngine.sort(entries)
  → TextFormatter.format(entries, stats) → String
  → System.out
```

---

## 4. 技術棧

| Component | Choice | Reason |
|-----------|--------|--------|
| Build | Gradle (Kotlin DSL) | 用戶指定 |
| Language | Java 17 | LTS, 主流 |
| CLI | picocli 4.7.x | 業界標準 CLI framework |
| Test | JUnit 5 + AssertJ | 標準 testing stack |
| Logging | SLF4J + java.util.logging | 無需額外 log library |

---

## 5. 測試策略

### 5.1 單元測試

- `PomScannerTest` — 用預先整好嘅 `pom.properties` 字串去 test parsing
- `ManifestScannerTest` — 用 `java.util.jar.Manifest` 建構測試
- `EmbeddedJarScannerTest` — 用 memory-based JAR (JarOutputStream + ByteArrayOutputStream)
- `TextFormatterTest` — 驗證 output format
- `ScannerEngineTest` — 驗證 dedup + merge logic

### 5.2 Integration Test

- 用真實 sample JAR（spring-boot fat JAR）驗證 end-to-end
- 用 empty JAR 驗證 edge case
- 用 corrupted JAR 驗證 error handling

---

## 6. Project Structure

```
jar-version-inspector/
├── AGENTS.md
├── SPEC.md
├── README.md
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── gradlew
├── gradlew.bat
└── src/
    ├── main/
    │   ├── java/
    │   │   └── com/
    │   │       └── jarversion/
    │   │           ├── JarVersionInspector.java
    │   │           ├── ScannerEngine.java
    │   │           ├── LibraryEntry.java
    │   │           ├── scanner/
    │   │           │   ├── PomScanner.java
    │   │           │   ├── ManifestScanner.java
    │   │           │   └── EmbeddedJarScanner.java
    │   │           └── output/
    │   │               └── TextFormatter.java
    │   └── resources/
    │       └── (optional: log config)
    └── test/
        └── java/
            └── com/
                └── jarversion/
                    ├── JarVersionInspectorTest.java
                    └── scanner/
                        ├── PomScannerTest.java
                        ├── ManifestScannerTest.java
                        └── EmbeddedJarScannerTest.java
```
