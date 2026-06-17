package com.jarversion.output;

import com.jarversion.LibraryEntry;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextFormatterTest {

    @Test
    void format_containsSourceAndSummary() {
        List<LibraryEntry> entries = List.of(
            new LibraryEntry("ch.qos.logback", "logback-classic", "1.4.14",
                LibraryEntry.Source.POM_PROPERTIES, 0),
            new LibraryEntry("com.google.guava", "guava", "32.1.3-jre",
                LibraryEntry.Source.POM_PROPERTIES, 0)
        );

        String report = TextFormatter.format(entries, Paths.get("/path/to/app.jar"), 25_000_000L, 0);

        assertThat(report).contains("Jar Version Inspector — Report");
        assertThat(report).contains("/path/to/app.jar");
        assertThat(report).contains("ch.qos.logback:logback-classic");
        assertThat(report).contains("com.google.guava:guava");
        assertThat(report).contains("23.8 MB");  // ~25MB
        assertThat(report).containsPattern("From pom\\.properties:\\s+2");
    }

    @Test
    void format_emptyEntries_containsNoDataMessage() {
        List<LibraryEntry> entries = List.of();

        String report = TextFormatter.format(entries, Paths.get("/empty.jar"), 100L, 0);

        assertThat(report).contains("no library version data found");
    }
}
