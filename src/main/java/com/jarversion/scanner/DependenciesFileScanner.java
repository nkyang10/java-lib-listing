package com.jarversion.scanner;

import com.jarversion.LibraryEntry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Scans META-INF/DEPENDENCIES file (Apache project standard).
 *
 * Apache projects include a DEPENDENCIES file that lists
 * transitive dependencies in a human-readable format:
 *
 *   Commons Codec
 *   ─────────────
 *   - org.apache.commons:commons-codec:1.16.0
 *
 * Also parses simpler formats:
 *   org.apache.logging.log4j:log4j-api:2.20.0
 *   com.fasterxml.jackson.core:jackson-core 2.15.3
 */
public class DependenciesFileScanner {

    public List<LibraryEntry> scan(Path jarPath) throws IOException {
        List<LibraryEntry> entries = new ArrayList<>();

        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            Enumeration<? extends ZipEntry> zipEntries = zip.entries();

            while (zipEntries.hasMoreElements()) {
                ZipEntry entry = zipEntries.nextElement();
                String name = entry.getName();

                if (name.equals("META-INF/DEPENDENCIES")) {
                    List<String> lines;
                    try (InputStream is = zip.getInputStream(entry);
                         Scanner scanner = new Scanner(is)) {
                        lines = new ArrayList<>();
                        while (scanner.hasNextLine()) {
                            lines.add(scanner.nextLine());
                        }
                    }
                    entries.addAll(parseDependencies(lines));
                }
            }
        }

        return entries;
    }

    /**
     * Parse DEPENDENCIES file content for library entries.
     */
    List<LibraryEntry> parseDependencies(List<String> lines) {
        List<LibraryEntry> entries = new ArrayList<>();

        for (String line : lines) {
            // Skip headers, separators, blank lines
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.matches("^[─=]+$")
                || trimmed.matches("^[*]+$")) {
                continue;
            }

            // Strip leading bullet markers
            if (trimmed.startsWith("- ") && trimmed.length() > 2) {
                trimmed = trimmed.substring(2).trim();
            }

            // Try colon-separated format: group:artifact:version
            String[] colonParts = trimmed.split(":");
            if (colonParts.length >= 3) {
                String groupId = colonParts[0].trim();
                String artifactId = colonParts[1].trim();
                String version = colonParts[2].trim();

                // Clean trailing spaces/punctuation from version
                if (version.endsWith(",") || version.endsWith(".")) {
                    version = version.substring(0, version.length() - 1).trim();
                }

                if (!groupId.isEmpty() && !artifactId.isEmpty() && !version.isEmpty()) {
                    // Validate it looks like a proper G:A:V (not a sentence)
                    if (groupId.contains(".") || groupId.isEmpty()) {
                        entries.add(new LibraryEntry(
                            groupId, artifactId, version,
                            LibraryEntry.Source.DEPENDENCIES_FILE, 0
                        ));
                        continue;
                    }
                }
            }

            // Try space-separated format: group:artifact version
            String[] spaceParts = trimmed.split("\\s+");
            if (spaceParts.length >= 2) {
                String ga = spaceParts[0];
                String version = spaceParts[spaceParts.length - 1];
                String[] gaParts = ga.split(":");
                if (gaParts.length == 2 && !version.isEmpty()) {
                    entries.add(new LibraryEntry(
                        gaParts[0], gaParts[1], version,
                        LibraryEntry.Source.DEPENDENCIES_FILE, 0
                    ));
                }
            }
        }

        return entries;
    }
}
