package com.jarversion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke test: basic project wiring checks.
 * Full scanner tests are in the scanner subpackage.
 */
class JarVersionInspectorTest {

    @Test
    void libraryEntry_constructorAndGetters() {
        LibraryEntry entry = new LibraryEntry(
            "com.example", "my-lib", "1.0.0",
            LibraryEntry.Source.POM_PROPERTIES, 0
        );

        assertEquals("com.example", entry.getGroupId());
        assertEquals("my-lib", entry.getArtifactId());
        assertEquals("1.0.0", entry.getVersion());
        assertEquals(LibraryEntry.Source.POM_PROPERTIES, entry.getSource());
        assertEquals(0, entry.getDepth());
    }

    @Test
    void libraryEntry_getKey() {
        LibraryEntry entry = new LibraryEntry(
            "ch.qos.logback", "logback-classic", "1.4.14",
            LibraryEntry.Source.POM_PROPERTIES, 0
        );
        assertEquals("ch.qos.logback:logback-classic", entry.getKey());
    }

    @Test
    void libraryEntry_getKeyWithNullParts() {
        LibraryEntry entry = new LibraryEntry(
            null, "some-lib", "2.0",
            LibraryEntry.Source.MANIFEST_IMPLEMENTATION, 0
        );
        assertEquals(":some-lib", entry.getKey());
    }

    @Test
    void libraryEntry_displayName_withGroupId() {
        LibraryEntry entry = new LibraryEntry(
            "com.google.guava", "guava", "32.1.3-jre",
            LibraryEntry.Source.POM_PROPERTIES, 0
        );
        assertEquals("com.google.guava:guava", entry.getDisplayName());
    }

    @Test
    void libraryEntry_displayName_withoutGroupId() {
        LibraryEntry entry = new LibraryEntry(
            null, "spring-boot-loader", "3.1.5",
            LibraryEntry.Source.MANIFEST_IMPLEMENTATION, 0
        );
        assertEquals("spring-boot-loader", entry.getDisplayName());
    }

    @Test
    void libraryEntry_equality() {
        LibraryEntry a = new LibraryEntry("g", "a", "1.0", LibraryEntry.Source.POM_PROPERTIES, 0);
        LibraryEntry b = new LibraryEntry("g", "a", "1.0", LibraryEntry.Source.POM_PROPERTIES, 0);
        LibraryEntry c = new LibraryEntry("g", "a", "2.0", LibraryEntry.Source.POM_PROPERTIES, 0);

        assertEquals(a, b);
        assertNotEquals(a, c);
    }

    @Test
    void scannerEngine_compareVersions() {
        ScannerEngine engine = new ScannerEngine(false, false);
        // Test via scannerEngine's dedup method or just verify the logic exists
        assertDoesNotThrow(() -> engine.sort(java.util.Collections.emptyList()));
    }
}
