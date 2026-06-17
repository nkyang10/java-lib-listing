package com.jarversion.output;

import com.jarversion.LibraryEntry;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonFormatterTest {

    @Test
    void format_containsToolAndVersion() {
        List<LibraryEntry> entries = List.of(
            new LibraryEntry("ch.qos.logback", "logback-classic", "1.4.14",
                LibraryEntry.Source.POM_PROPERTIES, 0),
            new LibraryEntry("com.google.guava", "guava", "32.1.3-jre",
                LibraryEntry.Source.POM_PROPERTIES, 0)
        );

        String json = JsonFormatter.format(entries, Paths.get("/path/to/app.jar"), 25_000_000L, 0);

        assertThat(json).contains("\"tool\": \"jar-version-inspector\"");
        assertThat(json).contains("\"version\": \"1.0.0\"");
        assertThat(json).contains("\"jarSizeBytes\": 25000000");
        assertThat(json).contains("\"libraryCount\": 2");
        assertThat(json).contains("\"dedupCount\": 0");
    }

    @Test
    void format_containsLibraryEntries() {
        List<LibraryEntry> entries = List.of(
            new LibraryEntry("ch.qos.logback", "logback-classic", "1.4.14",
                LibraryEntry.Source.POM_PROPERTIES, 0)
        );

        String json = JsonFormatter.format(entries, Paths.get("/test.jar"), 1000L, 0);

        assertThat(json).contains("\"displayName\": \"ch.qos.logback:logback-classic\"");
        assertThat(json).contains("\"groupId\": \"ch.qos.logback\"");
        assertThat(json).contains("\"artifactId\": \"logback-classic\"");
        assertThat(json).contains("\"version\": \"1.4.14\"");
        assertThat(json).contains("\"source\": \"POM_PROPERTIES\"");
        assertThat(json).contains("\"depth\": 0");
    }

    @Test
    void format_showsDedupCount() {
        List<LibraryEntry> entries = List.of(
            new LibraryEntry("com.example", "lib", "2.0.0",
                LibraryEntry.Source.POM_PROPERTIES, 0)
        );

        String json = JsonFormatter.format(entries, Paths.get("/test.jar"), 1000L, 3);
        assertThat(json).contains("\"dedupCount\": 3");
    }

    @Test
    void format_showsSummaryBySource() {
        List<LibraryEntry> entries = List.of(
            new LibraryEntry("a", "a", "1.0", LibraryEntry.Source.POM_PROPERTIES, 0),
            new LibraryEntry("b", "b", "2.0", LibraryEntry.Source.POM_XML, 0),
            new LibraryEntry("c", "c", "3.0", LibraryEntry.Source.EMBEDDED_JAR, 1),
            new LibraryEntry(null, "manifest-lib", "4.0",
                LibraryEntry.Source.MANIFEST_IMPLEMENTATION, 0)
        );

        String json = JsonFormatter.format(entries, Paths.get("/test.jar"), 1000L, 0);

        assertThat(json).contains("\"fromPomProperties\": 1");
        assertThat(json).contains("\"fromPomXml\": 1");
        assertThat(json).contains("\"fromManifest\": 1");
        assertThat(json).contains("\"fromEmbeddedJars\": 1");
        assertThat(json).contains("\"fromDeepScan\": 0");
    }

    @Test
    void format_emptyEntries_returnsValidJson() {
        List<LibraryEntry> entries = List.of();
        String json = JsonFormatter.format(entries, Paths.get("/empty.jar"), 100L, 0);

        assertThat(json).contains("\"libraryCount\": 0");
        assertThat(json).contains("\"libraries\": [");
        assertThat(json).contains("]");
        assertThat(json).startsWith("{");
        assertThat(json).endsWith("}\n");
    }

    @Test
    void format_escapesSpecialChars() {
        List<LibraryEntry> entries = List.of(
            new LibraryEntry("test", "lib\"with\"quotes", "1.0",
                LibraryEntry.Source.POM_PROPERTIES, 0)
        );

        String json = JsonFormatter.format(entries, Paths.get("/test.jar"), 100L, 0);
        assertThat(json).doesNotContain("\"lib\"");
        assertThat(json).contains("lib\\\"with\\\"quotes");
    }
}
