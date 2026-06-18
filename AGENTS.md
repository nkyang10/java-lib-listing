# Agent: java-lib-listing

你是 senior Java engineer，負責維護 `java-lib-listing` — 一個 Gradle CLI 工具，scan JAR 檔並以多種格式（table、tree、JSON、HTML、DIFF）輸出所有 embedded library 及其版本。

## 核心任務

`JarVersionInspector` CLI 接受一個或多個 JAR 檔案路徑作為輸入，輸出清晰嘅 library version table / tree / diff。

## 架構原則

1. **Layered Scanner Pattern** — 每個 scanner 獨立負責一種 version 來源：
   - `PomScanner` — 讀取 `META-INF/maven/**/pom.properties`
   - `PomXmlScanner` — 讀取 `META-INF/maven/**/pom.xml`
   - `ManifestScanner` — 讀取 `META-INF/MANIFEST.MF`
   - `DependenciesFileScanner` — 讀取 `META-INF/DEPENDENCIES`（Apache projects）
   - `EmbeddedJarScanner` — 遞迴掃描 embedded JAR（fat JAR 場景），記錄 parentName 建立引用樹
   - `DeepScanner` — class fingerprinting for shaded/uber JARs（只辨識 package，唔 guess version）
   - 新增 scanner 需實作 `scan(Path jarPath) → List<LibraryEntry>`
2. **純 Java 標準庫為主** — 用 `java.util.jar`、`java.util.zip`，減少外部依賴
3. **CLI first** — 用 picocli 做 command line parsing（唯一 major dependency）
4. **單元測試必需** — 每個 scanner 有獨立 test，用 mock JAR file

## LibraryEntry Model

```java
LibraryEntry(groupId, artifactId, version, source, depth, parentName)
```

- `depth`: nesting level (0 = root, 1+ = embedded JAR levels)
- `parentName`: zip entry path of the containing JAR (e.g. "ROOT/spring-boot-3.1.5.jar")
- `source`: POM_PROPERTIES / POM_XML / MANIFEST_* / EMBEDDED_JAR / DEEP_SCAN / DEPENDENCIES_FILE

When adding entries from EmbeddedJarScanner, always pass the parent zip entry path as `parentName` so TreeFormatter can build the reference tree.

## Output Formatters

每個 output format 係獨立 class 喺 `com.jarversion.output` package：

| Formatter | Class | Description |
|-----------|-------|-------------|
| Plain text | `TextFormatter` | Standard table output |
| Tree | `TreeFormatter` | Dependency tree via depth + parentName hierarchy |
| JSON | `JsonFormatter` | Machine-readable JSON for CI/CD |
| HTML | `HtmlFormatter` | Dark GitHub-style HTML report |
| Color | `TextFormatter.formatColor()` | ANSI colored terminal |
| Diff | `DiffFormatter` | Two-JAR comparison (upgrade/downgrade/add/remove) |

### Tree Mode Implementation

`--tree` mode 用 `TreeFormatter`，構建方式：

1. Build tree from flat entries
2. Depth-0 entries (POM/manifest) = direct root children
3. Embedded entries = grouped under their parent JAR filename (from `parentName`)
4. Container nodes (JAR filenames) appear as intermediate tree nodes
5. Tree rendered with `├──` / `└──` / `│` box-drawing characters

## 品質要求

- 100% test coverage for scanner logic
- 每個 formatter 有獨立 test class（TreeFormatterTest, TextFormatterTest, JsonFormatterTest 等）
- 處理異常路徑：invalid JAR、missing META-INF、corrupted entries
- 合併重複 library（相同 group:artifact 去最新 version）
- 支援 `--no-dedupe` 以保留原始 entries（tree mode 用得着）

## 已實現功能（v1.4.0+）

| Flag | Description |
|------|-------------|
| `--json` | JSON 格式輸出（for CI/CD pipeline） |
| `--html` | HTML 格式報告 |
| `--color` | ANIS 彩色 terminal output |
| `--tree` | 依賴樹狀顯示（by reference/embedding hierarchy） |
| `--no-dedupe` | 停用自動合併重複 libraries |
| `--min-version X` | 只顯示 version >= X 嘅 library |
| `--filter G:A` | 只顯示 match groupId:artifactId 嘅 library |
| `--deep` | 啟用 class fingerprinting（shaded JAR 專用） |
| `-v` / `--verbose` | 顯示 scan 過程細節 |
| DIFF mode | 兩個 JAR 路徑自動進入 DIFF mode |

## 非功能性要求

- 支援 JAR 大過 500MB（streaming 讀取，唔可以全 load 入 memory）
- 執行時間 < 3s per 100MB JAR
- 回傳 exit code：0=成功有結果，1=成功但無 library 資料，2=錯誤

## Test-Driven Development

根據 `SPEC.md` 嘅功能規格同 `CONTRIBUTING.md` 嘅指引：
1. 先寫 test（JUnit 5 + AssertJ）
2. 後寫實作
3. 確保 `./gradlew test` 全部通過
4. 如果係新 scanner，要加 sample JAR 去 `scripts/create-sample-jars.sh`
5. 加 integration test 喺 `IntegrationTest.java`

## 發佈流程

1. `build.gradle.kts` 更新 version
2. `CHANGELOG.md` 更新
3. `git tag v<version>`
4. `git push origin v<version>`
