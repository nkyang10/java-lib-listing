package com.jarversion.scanner;

import com.jarversion.LibraryEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.assertj.core.api.Assertions.assertThat;

class ManifestScannerTest {

    @Test
    void scan_withImplementationVersion(@TempDir Path tempDir) throws IOException {
        Path jarPath = createJarWithManifest(tempDir, manifest -> {
            Attributes attrs = manifest.getMainAttributes();
            attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
            attrs.put(Attributes.Name.IMPLEMENTATION_TITLE, "spring-boot-loader");
            attrs.put(Attributes.Name.IMPLEMENTATION_VERSION, "3.1.5");
        });

        ManifestScanner scanner = new ManifestScanner();
        List<LibraryEntry> entries = scanner.scan(jarPath);

        assertThat(entries).isNotEmpty();
        assertThat(entries).anyMatch(e ->
            "spring-boot-loader".equals(e.getArtifactId())
                && "3.1.5".equals(e.getVersion())
                && e.getSource() == LibraryEntry.Source.MANIFEST_IMPLEMENTATION
        );
    }

    @Test
    void scan_withBundleVersion(@TempDir Path tempDir) throws IOException {
        Path jarPath = createJarWithManifest(tempDir, manifest -> {
            Attributes attrs = manifest.getMainAttributes();
            attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
            attrs.putValue("Bundle-Name", "org.osgi.core");
            attrs.putValue("Bundle-Version", "6.0.0");
        });

        ManifestScanner scanner = new ManifestScanner();
        List<LibraryEntry> entries = scanner.scan(jarPath);

        assertThat(entries).anyMatch(e ->
            "org.osgi.core".equals(e.getArtifactId())
                && "6.0.0".equals(e.getVersion())
                && e.getSource() == LibraryEntry.Source.MANIFEST_BUNDLE
        );
    }

    @Test
    void scan_withSpecificationVersion(@TempDir Path tempDir) throws IOException {
        Path jarPath = createJarWithManifest(tempDir, manifest -> {
            Attributes attrs = manifest.getMainAttributes();
            attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
            attrs.put(Attributes.Name.SPECIFICATION_TITLE, "Java Servlet API");
            attrs.put(Attributes.Name.SPECIFICATION_VERSION, "4.0");
        });

        ManifestScanner scanner = new ManifestScanner();
        List<LibraryEntry> entries = scanner.scan(jarPath);

        assertThat(entries).anyMatch(e ->
            "Java Servlet API".equals(e.getArtifactId())
                && "4.0".equals(e.getVersion())
                && e.getSource() == LibraryEntry.Source.MANIFEST_SPECIFICATION
        );
    }

    @Test
    void scan_withClassPath(@TempDir Path tempDir) throws IOException {
        Path jarPath = createJarWithManifest(tempDir, manifest -> {
            Attributes attrs = manifest.getMainAttributes();
            attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
            attrs.put(Attributes.Name.CLASS_PATH, "lib/log4j.jar lib/commons.jar");
        });

        ManifestScanner scanner = new ManifestScanner();
        List<LibraryEntry> entries = scanner.scan(jarPath);

        assertThat(entries).anyMatch(e ->
            "lib/log4j.jar".equals(e.getArtifactId())
                && e.getSource() == LibraryEntry.Source.MANIFEST_CLASS_PATH
        );
        assertThat(entries).anyMatch(e ->
            "lib/commons.jar".equals(e.getArtifactId())
                && e.getSource() == LibraryEntry.Source.MANIFEST_CLASS_PATH
        );
    }

    @Test
    void scan_noManifest_returnsEmpty(@TempDir Path tempDir) throws IOException {
        Path jarPath = tempDir.resolve("no-manifest.jar");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
            // Just add a normal entry, no manifest
            jos.putNextEntry(new JarEntry("dummy.txt"));
            jos.write("hello".getBytes());
            jos.closeEntry();
        }

        ManifestScanner scanner = new ManifestScanner();
        List<LibraryEntry> entries = scanner.scan(jarPath);
        assertThat(entries).isEmpty();
    }

    @Test
    void scan_emptyManifest_returnsEmpty(@TempDir Path tempDir) throws IOException {
        Path jarPath = createJarWithManifest(tempDir, manifest -> {
            Attributes attrs = manifest.getMainAttributes();
            attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
            // No version-related attributes
        });

        ManifestScanner scanner = new ManifestScanner();
        List<LibraryEntry> entries = scanner.scan(jarPath);
        assertThat(entries).isEmpty();
    }

    @FunctionalInterface
    private interface ManifestCustomizer {
        void customize(Manifest manifest) throws IOException;
    }

    private Path createJarWithManifest(Path tempDir, ManifestCustomizer customizer) throws IOException {
        Path jarPath = tempDir.resolve("test-manifest.jar");
        Manifest manifest = new Manifest();

        try (FileOutputStream fos = new FileOutputStream(jarPath.toFile());
             JarOutputStream jos = new JarOutputStream(fos, manifest)) {

            // Use the output stream's manifest — need to create via the constructor
        }

        // Re-open to add custom manifest attributes
        customizer.customize(manifest);

        try (FileOutputStream fos = new FileOutputStream(jarPath.toFile());
             JarOutputStream jos = new JarOutputStream(fos, manifest)) {
            jos.putNextEntry(new JarEntry("dummy.txt"));
            jos.write("data".getBytes());
            jos.closeEntry();
        }

        return jarPath;
    }
}
