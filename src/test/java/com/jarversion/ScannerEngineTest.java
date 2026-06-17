package com.jarversion;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScannerEngineTest {

    @Test
    void deduplicate_mergesSameLibrary_keepsHighestVersion() {
        ScannerEngine engine = new ScannerEngine(false);

        List<LibraryEntry> entries = List.of(
            new LibraryEntry("com.example", "lib", "1.0.0", LibraryEntry.Source.POM_PROPERTIES, 0),
            new LibraryEntry("com.example", "lib", "2.0.0", LibraryEntry.Source.POM_PROPERTIES, 0),
            new LibraryEntry("com.example", "lib", "1.5.0", LibraryEntry.Source.EMBEDDED_JAR, 1)
        );

        List<LibraryEntry> deduped = engine.deduplicate(entries);

        // Should keep version 2.0.0 (highest)
        assertThat(deduped).hasSize(1);
        assertThat(deduped.get(0).getVersion()).isEqualTo("2.0.0");
    }

    @Test
    void deduplicate_preservesDifferentLibraries() {
        ScannerEngine engine = new ScannerEngine(false);

        List<LibraryEntry> entries = List.of(
            new LibraryEntry("com.a", "lib-a", "1.0", LibraryEntry.Source.POM_PROPERTIES, 0),
            new LibraryEntry("com.b", "lib-b", "2.0", LibraryEntry.Source.POM_PROPERTIES, 0)
        );

        List<LibraryEntry> deduped = engine.deduplicate(entries);
        assertThat(deduped).hasSize(2);
    }

    @Test
    void deduplicate_preservesNullGroupEntries() {
        ScannerEngine engine = new ScannerEngine(false);

        List<LibraryEntry> entries = List.of(
            new LibraryEntry("com.a", "lib-a", "1.0", LibraryEntry.Source.POM_PROPERTIES, 0),
            new LibraryEntry(null, "some-manifest-lib", "3.0", LibraryEntry.Source.MANIFEST_IMPLEMENTATION, 0)
        );

        List<LibraryEntry> deduped = engine.deduplicate(entries);
        assertThat(deduped).hasSize(2);
    }

    @Test
    void sort_ordersByName() {
        ScannerEngine engine = new ScannerEngine(false);

        List<LibraryEntry> entries = List.of(
            new LibraryEntry("z", "z-lib", "1.0", LibraryEntry.Source.POM_PROPERTIES, 0),
            new LibraryEntry("a", "a-lib", "1.0", LibraryEntry.Source.POM_PROPERTIES, 0)
        );

        List<LibraryEntry> sorted = engine.sort(entries);
        assertThat(sorted).extracting(LibraryEntry::getDisplayName)
            .containsExactly("a:a-lib", "z:z-lib");
    }

    @Test
    void filterByMinVersion_filtersCorrectly() {
        ScannerEngine engine = new ScannerEngine(false);

        List<LibraryEntry> entries = List.of(
            new LibraryEntry("com.a", "lib-a", "1.0", LibraryEntry.Source.POM_PROPERTIES, 0),
            new LibraryEntry("com.b", "lib-b", "2.5", LibraryEntry.Source.POM_PROPERTIES, 0),
            new LibraryEntry("com.c", "lib-c", "3.0", LibraryEntry.Source.POM_PROPERTIES, 0)
        );

        List<LibraryEntry> filtered = engine.filterByMinVersion(entries, "2.0");
        assertThat(filtered).hasSize(2);
        assertThat(filtered).extracting(LibraryEntry::getArtifactId)
            .containsExactlyInAnyOrder("lib-b", "lib-c");
    }

    @Test
    void filterByGA_filtersByPattern() {
        ScannerEngine engine = new ScannerEngine(false);

        List<LibraryEntry> entries = List.of(
            new LibraryEntry("com.fasterxml.jackson.core", "jackson-databind", "2.15.3",
                LibraryEntry.Source.POM_PROPERTIES, 0),
            new LibraryEntry("org.slf4j", "slf4j-api", "2.0.9",
                LibraryEntry.Source.POM_PROPERTIES, 0)
        );

        List<LibraryEntry> filtered = engine.filterByGA(entries, "jackson");
        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0).getArtifactId()).isEqualTo("jackson-databind");
    }
}
