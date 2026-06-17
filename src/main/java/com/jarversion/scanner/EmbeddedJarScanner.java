package com.jarversion.scanner;

import com.jarversion.LibraryEntry;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Recursively scans embedded JAR/ZIP entries inside a fat JAR.
 *
 * Uses a depth limit (max 5) to prevent infinite recursion.
 * Each embedded JAR is scanned with PomScanner + ManifestScanner recursively.
 */
public class EmbeddedJarScanner {

    private static final int MAX_DEPTH = 5;

    public List<LibraryEntry> scan(Path jarPath) throws IOException {
        List<LibraryEntry> entries = new ArrayList<>();
        scanRecursive(jarPath, 0, entries, new HashSet<>());
        return entries;
    }

    private void scanRecursive(Path jarPath, int depth, List<LibraryEntry> results, Set<String> visited)
            throws IOException {

        if (depth >= MAX_DEPTH) return;

        // Normalize path to avoid re-visiting same JAR
        String normalizedPath = jarPath.toAbsolutePath().normalize().toString();
        if (!visited.add(normalizedPath)) return;

        List<Path> embeddedJars = new ArrayList<>();

        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                // Detect embedded JAR/ZIP
                if (isEmbeddedArchive(name)) {
                    Path tempFile = extractToTemp(zip, entry);
                    if (tempFile != null) {
                        embeddedJars.add(tempFile);

                        // Scan this embedded JAR with all scanners
                        PomScanner pomScanner = new PomScanner();
                        PomXmlScanner pomXmlScanner = new PomXmlScanner();
                        ManifestScanner manifestScanner = new ManifestScanner();
                        DependenciesFileScanner dependenciesFileScanner = new DependenciesFileScanner();

                        List<LibraryEntry> pomEntries = pomScanner.scan(tempFile);
                        List<LibraryEntry> pomXmlEntries = pomXmlScanner.scan(tempFile);
                        List<LibraryEntry> manifestEntries = manifestScanner.scan(tempFile);
                        List<LibraryEntry> depsEntries = dependenciesFileScanner.scan(tempFile);

                        // Tag entries with depth info
                        int nextDepth = depth + 1;
                        for (LibraryEntry e : pomEntries) {
                            results.add(new LibraryEntry(
                                e.getGroupId(), e.getArtifactId(), e.getVersion(),
                                LibraryEntry.Source.EMBEDDED_JAR, nextDepth
                            ));
                        }
                        for (LibraryEntry e : pomXmlEntries) {
                            results.add(new LibraryEntry(
                                e.getGroupId(), e.getArtifactId(), e.getVersion(),
                                LibraryEntry.Source.EMBEDDED_JAR, nextDepth
                            ));
                        }
                        for (LibraryEntry e : manifestEntries) {
                            results.add(new LibraryEntry(
                                e.getGroupId(), e.getArtifactId(), e.getVersion(),
                                LibraryEntry.Source.EMBEDDED_JAR, nextDepth
                            ));
                        }
                        for (LibraryEntry e : depsEntries) {
                            results.add(new LibraryEntry(
                                e.getGroupId(), e.getArtifactId(), e.getVersion(),
                                LibraryEntry.Source.EMBEDDED_JAR, nextDepth
                            ));
                        }
                    }
                }
            }
        }

        // Recurse into each embedded JAR
        for (Path embedded : embeddedJars) {
            scanRecursive(embedded, depth + 1, results, visited);
            try {
                Files.deleteIfExists(embedded);
            } catch (IOException ignored) {
                // Temp file cleanup is best-effort
            }
        }
    }

    private boolean isEmbeddedArchive(String name) {
        String lower = name.toLowerCase();
        return (lower.endsWith(".jar") || lower.endsWith(".zip"))
            && !lower.startsWith("meta-inf/")
            && name.contains("/"); // Must be in a subdirectory, not root-level
    }

    /**
     * Extract a ZipEntry to a temporary file for scanning.
     */
    private Path extractToTemp(ZipFile zip, ZipEntry entry) {
        try {
            Path tempFile = Files.createTempFile("jvi-embedded-", ".jar");
            try (InputStream is = zip.getInputStream(entry);
                 OutputStream os = Files.newOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
            }
            return tempFile;
        } catch (IOException e) {
            return null;
        }
    }
}
