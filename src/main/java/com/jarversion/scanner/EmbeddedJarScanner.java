package com.jarversion.scanner;

import com.jarversion.LibraryEntry;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Recursively scans embedded JAR/ZIP entries inside a fat JAR.
 *
 * Uses a depth limit (max 5) to prevent infinite recursion.
 * Each embedded JAR is scanned with PomScanner + ManifestScanner recursively,
 * and tracks which parent JAR contained it.
 */
public class EmbeddedJarScanner {

    private static final int MAX_DEPTH = 5;
    private static final int BUFFER_SIZE = 8192;

    public List<LibraryEntry> scan(Path jarPath) throws IOException {
        List<LibraryEntry> entries = new ArrayList<>();
        scanRecursive(jarPath, 0, LibraryEntry.ROOT_PARENT, entries, new HashSet<>());
        return entries;
    }

    private void scanRecursive(Path jarPath, int depth, String parentName,
                                List<LibraryEntry> results, Set<String> visited)
            throws IOException {

        if (depth >= MAX_DEPTH) return;

        // Normalize path to avoid re-visiting same JAR
        String normalizedPath = jarPath.toAbsolutePath().normalize().toString();
        if (!visited.add(normalizedPath)) return;

        List<Path> embeddedJars = new ArrayList<>();
        List<String> embeddedNames = new ArrayList<>();

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
                        // Use the zip entry path as parent identifier
                        embeddedNames.add(parentName + "/" + extractArtifactName(name));
                    }
                }
            }
        }

        try {
            // Scan each embedded JAR — record parent reference
            for (int i = 0; i < embeddedJars.size(); i++) {
                Path embedded = embeddedJars.get(i);
                String childParentName = embeddedNames.get(i);
                int nextDepth = depth + 1;

                PomScanner pomScanner = new PomScanner();
                PomXmlScanner pomXmlScanner = new PomXmlScanner();
                ManifestScanner manifestScanner = new ManifestScanner();
                DependenciesFileScanner dependenciesFileScanner = new DependenciesFileScanner();

                List<LibraryEntry> pomEntries = pomScanner.scan(embedded);
                List<LibraryEntry> pomXmlEntries = pomXmlScanner.scan(embedded);
                List<LibraryEntry> manifestEntries = manifestScanner.scan(embedded);
                List<LibraryEntry> depsEntries = dependenciesFileScanner.scan(embedded);

                // Tag entries with depth + parent info
                for (LibraryEntry e : pomEntries) {
                    results.add(new LibraryEntry(
                        e.getGroupId(), e.getArtifactId(), e.getVersion(),
                        LibraryEntry.Source.EMBEDDED_JAR, nextDepth, childParentName));
                }
                for (LibraryEntry e : pomXmlEntries) {
                    results.add(new LibraryEntry(
                        e.getGroupId(), e.getArtifactId(), e.getVersion(),
                        LibraryEntry.Source.EMBEDDED_JAR, nextDepth, childParentName));
                }
                for (LibraryEntry e : manifestEntries) {
                    results.add(new LibraryEntry(
                        e.getGroupId(), e.getArtifactId(), e.getVersion(),
                        LibraryEntry.Source.EMBEDDED_JAR, nextDepth, childParentName));
                }
                for (LibraryEntry e : depsEntries) {
                    results.add(new LibraryEntry(
                        e.getGroupId(), e.getArtifactId(), e.getVersion(),
                        LibraryEntry.Source.EMBEDDED_JAR, nextDepth, childParentName));
                }

                // Recurse into this embedded JAR's own embedded JARs
                scanRecursive(embedded, nextDepth, childParentName, results, visited);
            }
        } finally {
            // Cleanup temp files — always run, even on error
            for (Path embedded : embeddedJars) {
                try {
                    Files.deleteIfExists(embedded);
                } catch (IOException ignored) {
                    // Temp file cleanup is best-effort
                }
            }
        }
    }

    /**
     * Derive a human-readable artifact name from the zip entry path.
     * e.g. "BOOT-INF/lib/spring-boot-3.1.5.jar" → "spring-boot-3.1.5.jar"
     */
    private String extractArtifactName(String zipEntryName) {
        String name = zipEntryName.replace('\\', '/');
        int lastSlash = name.lastIndexOf('/');
        return lastSlash >= 0 ? name.substring(lastSlash + 1) : name;
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
                byte[] buffer = new byte[BUFFER_SIZE];
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
