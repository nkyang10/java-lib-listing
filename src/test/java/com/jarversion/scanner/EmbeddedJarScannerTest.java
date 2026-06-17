package com.jarversion.scanner;

import com.jarversion.LibraryEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddedJarScannerTest {

    @Test
    void scan_noEmbeddedJars_returnsEmpty(@TempDir Path tempDir) throws IOException {
        Path jarPath = tempDir.resolve("simple.jar");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
            jos.putNextEntry(new JarEntry("META-INF/maven/com.example/test/pom.properties"));
            jos.write("""
                version=1.0
                groupId=com.example
                artifactId=test
                """.getBytes());
            jos.closeEntry();
        }

        EmbeddedJarScanner scanner = new EmbeddedJarScanner();
        List<LibraryEntry> entries = scanner.scan(jarPath);
        assertThat(entries).isEmpty();
    }

    @Test
    void scan_withEmbeddedJar_detectsLibraries(@TempDir Path tempDir) throws IOException {
        // First, create the inner JAR
        Path innerJarPath = tempDir.resolve("inner.jar");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(innerJarPath.toFile()))) {
            jos.putNextEntry(new JarEntry("META-INF/maven/com.inner/lib/pom.properties"));
            jos.write("""
                version=2.0.0
                groupId=com.inner
                artifactId=lib
                """.getBytes());
            jos.closeEntry();
        }

        // Embed the inner JAR into the outer JAR
        Path outerJarPath = tempDir.resolve("outer.jar");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(outerJarPath.toFile()))) {
            // Add the inner JAR as an entry
            jos.putNextEntry(new JarEntry("BOOT-INF/lib/inner.jar"));
            byte[] innerBytes = java.nio.file.Files.readAllBytes(innerJarPath);
            jos.write(innerBytes);
            jos.closeEntry();

            // Also add a pom.properties in the outer JAR
            jos.putNextEntry(new JarEntry("META-INF/maven/com.outer/app/pom.properties"));
            jos.write("""
                version=1.0.0
                groupId=com.outer
                artifactId=app
                """.getBytes());
            jos.closeEntry();
        }

        EmbeddedJarScanner scanner = new EmbeddedJarScanner();
        List<LibraryEntry> entries = scanner.scan(outerJarPath);

        // Should find the embedded library
        assertThat(entries).anyMatch(e ->
            "com.inner:lib".equals(e.getDisplayName())
                && "2.0.0".equals(e.getVersion())
                && e.getSource() == LibraryEntry.Source.EMBEDDED_JAR
                && e.getDepth() == 1 // One level deep
        );
    }
}
