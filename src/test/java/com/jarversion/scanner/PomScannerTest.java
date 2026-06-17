package com.jarversion.scanner;

import com.jarversion.LibraryEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class PomScannerTest {

    @Test
    void scan_validPomProperties_returnsEntries(@TempDir Path tempDir) throws IOException {
        // Create a minimal JAR with pom.properties
        Path jarPath = tempDir.resolve("test.jar");

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
            // Add pom.properties
            jos.putNextEntry(new JarEntry("META-INF/maven/ch.qos.logback/logback-classic/pom.properties"));
            jos.write("""
                version=1.4.14
                groupId=ch.qos.logback
                artifactId=logback-classic
                """.getBytes());
            jos.closeEntry();

            // Add another one
            jos.putNextEntry(new JarEntry("META-INF/maven/com.google.guava/guava/pom.properties"));
            jos.write("""
                version=32.1.3-jre
                groupId=com.google.guava
                artifactId=guava
                """.getBytes());
            jos.closeEntry();
        }

        PomScanner scanner = new PomScanner();
        List<LibraryEntry> entries = scanner.scan(jarPath);

        assertThat(entries).hasSize(2);
        assertThat(entries).extracting(LibraryEntry::getGroupId)
            .containsExactlyInAnyOrder("ch.qos.logback", "com.google.guava");
        assertThat(entries).extracting(LibraryEntry::getVersion)
            .containsExactlyInAnyOrder("1.4.14", "32.1.3-jre");
        assertThat(entries).allMatch(e -> e.getSource() == LibraryEntry.Source.POM_PROPERTIES);
        assertThat(entries).allMatch(e -> e.getDepth() == 0);
    }

    @Test
    void scan_noPomProperties_returnsEmpty(@TempDir Path tempDir) throws IOException {
        Path jarPath = tempDir.resolve("empty.jar");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
            jos.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
            jos.write("Manifest-Version: 1.0\n".getBytes());
            jos.closeEntry();
        }

        PomScanner scanner = new PomScanner();
        List<LibraryEntry> entries = scanner.scan(jarPath);
        assertThat(entries).isEmpty();
    }

    @Test
    void scan_incompleteProperties_skipsEntry(@TempDir Path tempDir) throws IOException {
        Path jarPath = tempDir.resolve("incomplete.jar");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
            // Missing version
            jos.putNextEntry(new JarEntry("META-INF/maven/com.example/bad-lib/pom.properties"));
            jos.write("""
                groupId=com.example
                artifactId=bad-lib
                """.getBytes());
            jos.closeEntry();

            // Complete one
            jos.putNextEntry(new JarEntry("META-INF/maven/com.example/good-lib/pom.properties"));
            jos.write("""
                version=1.0
                groupId=com.example
                artifactId=good-lib
                """.getBytes());
            jos.closeEntry();
        }

        PomScanner scanner = new PomScanner();
        List<LibraryEntry> entries = scanner.scan(jarPath);
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getArtifactId()).isEqualTo("good-lib");
    }
}
