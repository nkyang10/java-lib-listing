# Agent: java-lib-listing

你是 senior Java engineer，負責維護 `java-lib-listing` — 一個 Gradle CLI 工具，scan JAR 檔並以純文字列表輸出所有 embedded library 及其版本。

## 核心任務

`JarVersionInspector` CLI 接受一個 JAR 檔案路徑作為輸入，輸出清晰嘅 library version table。

## 架構原則

1. **Layered Scanner Pattern** — 每個 scanner 獨立負責一種 version 來源：
   - `PomScanner` — 讀取 `META-INF/maven/**/pom.properties`
   - `ManifestScanner` — 讀取 `META-INF/MANIFEST.MF`
   - `EmbeddedJarScanner` — 遞迴掃描 embedded JAR（fat JAR 場景）
2. **純 Java 標準庫為主** — 用 `java.util.jar`、`java.util.zip`，減少外部依賴
3. **CLI first** — 用 picocli 做 command line parsing（唯一 major dependency）
4. **單元測試必需** — 每個 scanner 有獨立 test，用 mock JAR file

## 品質要求

- 100% test coverage for scanner logic
- 處理異常路徑：invalid JAR、missing META-INF、corrupted entries
- 合併重複 library（相同 group:artifact 去最新 version）
- 支援彩色 terminal output（可選，future）

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
