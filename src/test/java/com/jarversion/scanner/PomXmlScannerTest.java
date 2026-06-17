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

import static org.assertj.core.api.Assertions.assertThat;

class PomXmlScannerTest {

    @Test
    void scan_validPomXml_returnsEntries(@TempDir Path tempDir) throws IOException {
        Path jarPath = tempDir.resolve("test.jar");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
            jos.putNextEntry(new JarEntry("META-INF/maven/org.example/my-lib/pom.xml"));
            jos.write("""
                <?xml version="1.0"?>
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>my-lib</artifactId>
                  <version>2.0.0</version>
                </project>
                """.getBytes());
            jos.closeEntry();
        }

        PomXmlScanner scanner = new PomXmlScanner();
        List<LibraryEntry> entries = scanner.scan(jarPath);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0)).satisfies(e -> {
            assertThat(e.getGroupId()).isEqualTo("org.example");
            assertThat(e.getArtifactId()).isEqualTo("my-lib");
            assertThat(e.getVersion()).isEqualTo("2.0.0");
            assertThat(e.getSource()).isEqualTo(LibraryEntry.Source.POM_XML);
        });
    }

    @Test
    void scan_noPomXml_returnsEmpty(@TempDir Path tempDir) throws IOException {
        Path jarPath = tempDir.resolve("empty.jar");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
            jos.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
            jos.write("Manifest-Version: 1.0\n".getBytes());
            jos.closeEntry();
        }

        PomXmlScanner scanner = new PomXmlScanner();
        List<LibraryEntry> entries = scanner.scan(jarPath);
        assertThat(entries).isEmpty();
    }

    @Test
    void scan_skipsPropertyPlaceholders(@TempDir Path tempDir) throws IOException {
        Path jarPath = tempDir.resolve("props.jar");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
            jos.putNextEntry(new JarEntry("META-INF/maven/org.example/my-lib/pom.xml"));
            jos.write("""
                <?xml version="1.0"?>
                <project>
                  <groupId>org.example</groupId>
                  <artifactId>my-lib</artifactId>
                  <version>${project.version}</version>
                </project>
                """.getBytes());
            jos.closeEntry();
        }

        PomXmlScanner scanner = new PomXmlScanner();
        List<LibraryEntry> entries = scanner.scan(jarPath);

        // version is a placeholder, so null is acceptable
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getVersion()).isNull();
    }
}
