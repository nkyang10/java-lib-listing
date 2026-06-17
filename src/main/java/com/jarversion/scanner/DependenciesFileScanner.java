package com.jarversion.scanner;

import com.jarversion.LibraryEntry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 *
 * Also handles G:A:classifier:V format used by Maven dependency plugin:
 *   org.bouncycastle:bcutil-jdk18on:jar:1.83
 */
public class DependenciesFileScanner {

    // Regex for G:A:classifier:V (classifier like jar/pom/war)
    private static final Pattern GA_CLASSIFIER_VERSION = Pattern.compile(
        "([a-zA-Z][a-zA-Z0-9.]*):([a-zA-Z][a-zA-Z0-9._-]*):(jar|pom|war|zip|test-jar|sources|javadoc):([0-9][a-zA-Z0-9._-]*)"
    );

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
     * Tries multiple formats in order of specificity.
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

            LibraryEntry entry = null;

            // Format 1: G:A:classifier:V (e.g. org.bouncycastle:bcutil-jdk18on:jar:1.83)
            // Uses regex to find the pattern anywhere in the line (handles descriptions with URLs)
            if (entry == null) {
                entry = parseGaClassifierVersion(trimmed);
            }

            // Format 2: G:A:V (e.g. org.apache.commons:commons-codec:1.16.0)
            if (entry == null) {
                entry = parseGaVersion(trimmed);
            }

            // Format 3: G:A V (e.g. com.fasterxml.jackson.core:jackson-core 2.15.3)
            if (entry == null) {
                entry = parseGaSpaceVersion(trimmed);
            }

            if (entry != null) {
                entries.add(entry);
            }
        }

        return entries;
    }

    private LibraryEntry parseGaClassifierVersion(String trimmed) {
        Matcher matcher = GA_CLASSIFIER_VERSION.matcher(trimmed);
        if (!matcher.find()) return null;

        String groupId = matcher.group(1);
        String artifactId = matcher.group(2);
        String version = matcher.group(4);

        // Clean trailing punctuation
        if (version.endsWith(",") || version.endsWith(".")) {
            version = version.substring(0, version.length() - 1).trim();
        }

        if (groupId.isEmpty() || artifactId.isEmpty() || version.isEmpty()) return null;

        return new LibraryEntry(groupId, artifactId, version,
            LibraryEntry.Source.DEPENDENCIES_FILE, 0);
    }

    private LibraryEntry parseGaVersion(String trimmed) {
        String[] parts = trimmed.split(":");
        if (parts.length < 3) return null;

        String groupId = parts[0].trim();
        String artifactId = parts[1].trim();
        String version = parts[2].trim();

        // Validate: group must contain a dot, version must start with digit
        if (!groupId.contains(".")) return null;
        if (version.isEmpty()) return null;
        if (!Character.isDigit(version.charAt(0))) return null;

        // Clean trailing punctuation
        if (version.endsWith(",") || version.endsWith(".")) {
            version = version.substring(0, version.length() - 1).trim();
        }

        if (groupId.isEmpty() || artifactId.isEmpty() || version.isEmpty()) return null;

        return new LibraryEntry(groupId, artifactId, version,
            LibraryEntry.Source.DEPENDENCIES_FILE, 0);
    }

    private LibraryEntry parseGaSpaceVersion(String trimmed) {
        String[] spaceParts = trimmed.split("\\s+");
        if (spaceParts.length < 2) return null;

        String ga = spaceParts[0];
        String version = spaceParts[spaceParts.length - 1];
        String[] gaParts = ga.split(":");
        if (gaParts.length != 2 || version.isEmpty()) return null;
        if (!gaParts[0].contains(".")) return null;

        return new LibraryEntry(gaParts[0], gaParts[1], version,
            LibraryEntry.Source.DEPENDENCIES_FILE, 0);
    }
}
