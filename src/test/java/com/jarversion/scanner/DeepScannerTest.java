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

class DeepScannerTest {

    // ── getPackagePrefix (3-segment grouping) ──

    @Test
    void getPackagePrefix_standardPackage() {
        String result = DeepScanner.getPackagePrefix("com.fasterxml.jackson.databind.ObjectMapper");
        assertThat(result).isEqualTo("com.fasterxml.jackson");
    }

    @Test
    void getPackagePrefix_shallowPackage() {
        String result = DeepScanner.getPackagePrefix("com.example.Foo");
        assertThat(result).isEqualTo("com.example");
    }

    @Test
    void getPackagePrefix_rootPackage() {
        String result = DeepScanner.getPackagePrefix("Foo");
        assertThat(result).isNull();
    }

    @Test
    void getPackagePrefix_deeplyNested() {
        String result = DeepScanner.getPackagePrefix("org.springframework.boot.autoconfigure.SpringBootApplication");
        assertThat(result).isEqualTo("org.springframework.boot");
    }

    @Test
    void getPackagePrefix_twoSegment() {
        String result = DeepScanner.getPackagePrefix("okhttp3.Request");
        assertThat(result).isEqualTo("okhttp3");
    }

    @Test
    void getPackagePrefix_netty() {
        String result = DeepScanner.getPackagePrefix("io.netty.buffer.ByteBuf");
        assertThat(result).isEqualTo("io.netty.buffer");
    }

    @Test
    void scan_unknownPackages_producesDeepScanEntries(@TempDir Path tempDir) throws IOException {
        Path jarPath = tempDir.resolve("shaded.jar");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
            jos.putNextEntry(new JarEntry("com/unknown/lib/Foo.class"));
            jos.write(new byte[]{0, 0, 0, 0});
            jos.closeEntry();
            jos.putNextEntry(new JarEntry("com/unknown/lib/Bar.class"));
            jos.write(new byte[]{0, 0, 0, 0});
            jos.closeEntry();
            jos.putNextEntry(new JarEntry("org/other/pkg/Baz.class"));
            jos.write(new byte[]{0, 0, 0, 0});
            jos.closeEntry();
        }

        DeepScanner scanner = new DeepScanner(false);
        List<LibraryEntry> results = scanner.scan(jarPath);

        assertThat(results).allMatch(e -> e.getSource() == LibraryEntry.Source.DEEP_SCAN);
        assertThat(results).anyMatch(e -> "com.unknown.lib".equals(e.getGroupId()));
        assertThat(results).anyMatch(e -> "org.other.pkg".equals(e.getGroupId()));
    }

    @Test
    void scan_knownPackages_areSkipped(@TempDir Path tempDir) throws IOException {
        Path jarPath = tempDir.resolve("known.jar");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
            jos.putNextEntry(new JarEntry("com/known/lib/Foo.class"));
            jos.write(new byte[]{0, 0, 0, 0});
            jos.closeEntry();
        }

        DeepScanner scanner = new DeepScanner(false, java.util.Set.of("com.known"));
        List<LibraryEntry> results = scanner.scan(jarPath);

        assertThat(results).isEmpty();
    }
}
