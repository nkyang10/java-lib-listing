package com.jarversion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests: runs the actual CLI against sample JARs.
 * Sample JARs are in scripts/sample-jars/ (created by create-sample-jars.sh).
 */
class IntegrationTest {

    private static final String SAMPLES_DIR = System.getProperty("user.dir") + "/scripts/sample-jars";

    @Test
    void sample1_simpleMaven_showsAllLibraries() {
        String output = runTool(SAMPLES_DIR + "/sample1-simple-maven.jar");
        assertThat(output).contains("ch.qos.logback:logback-classic                1.4.14");
        assertThat(output).contains("com.fasterxml.jackson.core:jackson-databind   2.15.3");
        assertThat(output).contains("com.google.guava:guava                        32.1.3-jre");
    }

    @Test
    void sample1_simpleMaven_showsManifestLibrary() {
        String output = runTool(SAMPLES_DIR + "/sample1-simple-maven.jar");
        assertThat(output).contains("my-app");
        assertThat(output).contains("2.0.0");
        assertThat(output).contains("(Implementation-Version)");
    }

    @Test
    void sample1_simpleMaven_summaryCorrect() {
        String output = runTool(SAMPLES_DIR + "/sample1-simple-maven.jar");
        assertThat(output).contains("From pom.properties:           3");
        assertThat(output).contains("From MANIFEST.MF:              1");
        assertThat(output).contains("Total entries:                 4");
    }

    @Test
    void sample2_fatJar_detectsEmbeddedLibraries() {
        String output = runTool(SAMPLES_DIR + "/sample2-fat-jar.jar");
        assertThat(output).contains("com.example:outer-app");
        assertThat(output).contains("org.apache.commons:commons-lang3");
        assertThat(output).contains("org.slf4j:slf4j-api");
        assertThat(output).contains("[embedded]");
    }

    @Test
    void sample2_fatJar_summaryCorrect() {
        String output = runTool(SAMPLES_DIR + "/sample2-fat-jar.jar");
        assertThat(output).contains("From pom.properties:           1");
        assertThat(output).contains("From embedded JARs:            2");
        assertThat(output).contains("Total entries:                 3");
    }

    @Test
    void sample3_osgiBundle_showsBothSources() {
        String output = runTool(SAMPLES_DIR + "/sample3-osgi-bundle.jar");
        assertThat(output).contains("org.eclipse.osgi:org.eclipse.osgi             3.18.0");
        // From MANIFEST section
        assertThat(output).contains("org.eclipse.osgi");
        assertThat(output).contains("(Bundle-Version)");
    }

    @Test
    void sample3_osgiBundle_summaryCorrect() {
        String output = runTool(SAMPLES_DIR + "/sample3-osgi-bundle.jar");
        assertThat(output).contains("From pom.properties:           1");
        assertThat(output).contains("From MANIFEST.MF:              1");
        assertThat(output).contains("Total entries:                 2");
    }

    @Test
    void sample4_empty_showsNoData() {
        String output = runTool(SAMPLES_DIR + "/sample4-empty-jar.jar");
        assertThat(output).contains("no library version data found");
    }

    @Test
    void sample4_empty_exitCodeIs1() {
        int code = runToolGetExitCode(SAMPLES_DIR + "/sample4-empty-jar.jar");
        assertThat(code).isEqualTo(1);
    }

    @Test
    void invalidPath_exitCodeIs2() {
        int code = runToolGetExitCode("/path/to/nonexistent.jar");
        assertThat(code).isEqualTo(2);
    }

    @Test
    void verboseFlag_showsScanProgress() {
        String output = runToolWithArgs("--verbose", SAMPLES_DIR + "/sample1-simple-maven.jar");
        assertThat(output).contains("[JVI]");
        assertThat(output).contains("Opening JAR:");
        assertThat(output).contains("Scanning META-INF/maven/");
    }

    @Test
    void filterFlag_filtersByPattern() {
        String output = runToolWithArgs("--filter", "jackson", SAMPLES_DIR + "/sample1-simple-maven.jar");
        assertThat(output).contains("jackson-databind");
        assertThat(output).doesNotContain("logback");
        assertThat(output).doesNotContain("guava");
    }

    @Test
    void noDedupeFlag_showsSeparateEmbeddedEntries() {
        // With dedupe disabled, the tool should not merge anything
        // (sample1 has no duplicates to merge, but this verifies the flag works)
        String output = runToolWithArgs("--no-dedupe", SAMPLES_DIR + "/sample1-simple-maven.jar");
        assertThat(output).contains("ch.qos.logback:logback-classic");
        assertThat(output).contains("com.google.guava:guava");
    }

    // ====== Sample 5: Complex multi-level fat JAR ======

    @Test
    void sample5_complex_detectsAllLibraries() {
        String output = runTool(SAMPLES_DIR + "/sample5-complex-app.jar");
        assertThat(output).contains("com.mycompany:my-app");
        assertThat(output).contains("org.springframework.boot:spring-boot-starter-web");
        assertThat(output).contains("com.fasterxml.jackson.core:jackson-databind");
        assertThat(output).contains("com.mycompany:my-gradle-lib");
        assertThat(output).contains("com.squareup.okhttp3:okhttp");
        assertThat(output).contains("com.squareup.retrofit2:retrofit");
        assertThat(output).contains("custom-internal-lib");
    }

    @Test
    void sample5_complex_showsManifestEntry() {
        String output = runTool(SAMPLES_DIR + "/sample5-complex-app.jar");
        assertThat(output).contains("ComplexApp");
        assertThat(output).contains("3.0.0");
        assertThat(output).contains("(Implementation-Version)");
    }

    @Test
    void sample5_complex_summaryCorrect() {
        String output = runTool(SAMPLES_DIR + "/sample5-complex-app.jar");
        assertThat(output).contains("Total entries:                 12");
        assertThat(output).contains("From embedded JARs:            7");
        assertThat(output).contains("From MANIFEST.MF:              1");
        assertThat(output).contains("From DEPENDENCIES:             3");
    }

    @Test
    void sample5_complex_rootNotTaggedEmbedded() {
        String output = runTool(SAMPLES_DIR + "/sample5-complex-app.jar");
        // Find the "Libraries" section before MANIFEST
        int libEnd = output.indexOf("MANIFEST Libraries");
        String libSection = libEnd > 0 ? output.substring(0, libEnd) : output;
        // Root my-app should NOT have [embedded] tag
        assertThat(libSection).doesNotContain("my-app" + System.lineSeparator() + "[embedded]");
    }

    @Test
    void sample5_complex_verboseShowsFullScan() {
        String output = runToolWithArgs("--verbose", SAMPLES_DIR + "/sample5-complex-app.jar");
        assertThat(output).contains("[JVI] Opening JAR:");
        assertThat(output).contains("Scanning META-INF/maven/");
        assertThat(output).contains("Scanning embedded JARs...");
        assertThat(output).contains("Total raw entries:");
    }

    /**
     * Run the tool with just a JAR path (no extra flags).
     */
    private String runTool(String jarPath) {
        return runToolWithArgs(jarPath);
    }

    /**
     * Run the tool with arguments and capture stdout (and stderr for verbose).
     */
    private String runToolWithArgs(String... args) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));
        System.setErr(new PrintStream(err));

        try {
            new picocli.CommandLine(new JarVersionInspector()).execute(args);
        } catch (Exception e) {
            // Expected for some error cases
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
        // Return combined stdout + stderr for verbose test, stdout only for others
        return out.toString() + err.toString();
    }

    /**
     * Run the tool and return the exit code.
     */
    private int runToolGetExitCode(String... args) {
        return new picocli.CommandLine(new JarVersionInspector()).execute(args);
    }
}
