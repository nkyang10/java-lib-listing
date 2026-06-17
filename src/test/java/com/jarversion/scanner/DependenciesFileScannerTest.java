package com.jarversion.scanner;

import com.jarversion.LibraryEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DependenciesFileScannerTest {

    private final DependenciesFileScanner scanner = new DependenciesFileScanner();

    @Test
    void parseDependencies_standardFormat() {
        List<String> lines = List.of(
            "Apache Commons Codec",
            "───────────────────",
            "- org.apache.commons:commons-codec:1.16.1",
            "",
            "SLF4J",
            "─────",
            "- org.slf4j:slf4j-api:2.0.9",
            "",
            "HikariCP",
            "────────",
            "- com.zaxxer:HikariCP:5.0.1"
        );

        List<LibraryEntry> entries = scanner.parseDependencies(lines);

        assertThat(entries).hasSize(3);
        assertThat(entries).extracting(LibraryEntry::getArtifactId)
            .containsExactlyInAnyOrder("commons-codec", "slf4j-api", "HikariCP");
        assertThat(entries).extracting(LibraryEntry::getVersion)
            .containsExactlyInAnyOrder("1.16.1", "2.0.9", "5.0.1");
        assertThat(entries).allMatch(e -> e.getSource() == LibraryEntry.Source.DEPENDENCIES_FILE);
    }

    @Test
    void parseDependencies_spaceSeparatedFormat() {
        List<String> lines = List.of(
            "com.fasterxml.jackson.core:jackson-core 2.15.3",
            "com.google.guava:guava 32.1.3-jre"
        );

        List<LibraryEntry> entries = scanner.parseDependencies(lines);

        assertThat(entries).hasSize(2);
        assertThat(entries).extracting(LibraryEntry::getArtifactId)
            .containsExactlyInAnyOrder("jackson-core", "guava");
    }

    @Test
    void parseDependencies_handlesTrailingPunctuation() {
        List<String> lines = List.of(
            "- org.apache.commons:commons-lang3:3.12.0,"
        );

        List<LibraryEntry> entries = scanner.parseDependencies(lines);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getVersion()).isEqualTo("3.12.0");
    }

    @Test
    void parseDependencies_skipHeaders() {
        List<String> lines = List.of(
            "============================",
            "  NOT A DEPENDENCY LINE  ",
            "- com.example:real-lib:1.0"
        );

        List<LibraryEntry> entries = scanner.parseDependencies(lines);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getArtifactId()).isEqualTo("real-lib");
    }

    @Test
    void parseDependencies_noBulletFormat() {
        List<String> lines = List.of(
            "org.apache.commons:commons-codec:1.16.1",
            "org.slf4j:slf4j-api:2.0.9"
        );

        List<LibraryEntry> entries = scanner.parseDependencies(lines);

        assertThat(entries).hasSize(2);
    }
}
