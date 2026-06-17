package com.jarversion;

import com.jarversion.scanner.DeepScanner;
import com.jarversion.scanner.DependenciesFileScanner;
import com.jarversion.scanner.EmbeddedJarScanner;
import com.jarversion.scanner.ManifestScanner;
import com.jarversion.scanner.PomScanner;
import com.jarversion.scanner.PomXmlScanner;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates all scanners, deduplicates, and sorts results.
 */
public class ScannerEngine {

    private final boolean verbose;
    private final boolean deep;
    private int lastDedupCount = 0;
    private final PomScanner pomScanner;
    private final PomXmlScanner pomXmlScanner;
    private final ManifestScanner manifestScanner;
    private final DependenciesFileScanner dependenciesFileScanner;
    private final EmbeddedJarScanner embeddedJarScanner;
    private final DeepScanner deepScanner;

    public ScannerEngine(boolean verbose, boolean deep) {
        this.verbose = verbose;
        this.deep = deep;
        this.pomScanner = new PomScanner();
        this.pomXmlScanner = new PomXmlScanner();
        this.manifestScanner = new ManifestScanner();
        this.dependenciesFileScanner = new DependenciesFileScanner();
        this.embeddedJarScanner = new EmbeddedJarScanner();
        this.deepScanner = new DeepScanner(verbose);
    }

    /**
     * Scan a JAR file using all available scanners.
     */
    public List<LibraryEntry> scan(Path jarPath) throws IOException {
        log("Opening JAR: " + jarPath);

        List<LibraryEntry> allEntries = new ArrayList<>();

        // Stage 1: pom.properties
        log("Scanning META-INF/maven/pom.properties...");
        allEntries.addAll(pomScanner.scan(jarPath));

        // Stage 2: pom.xml (full POM files)
        log("Scanning META-INF/maven/pom.xml...");
        allEntries.addAll(pomXmlScanner.scan(jarPath));

        // Stage 3: MANIFEST.MF
        log("Scanning META-INF/MANIFEST.MF...");
        allEntries.addAll(manifestScanner.scan(jarPath));

        // Stage 4: META-INF/DEPENDENCIES (Apache projects)
        log("Scanning META-INF/DEPENDENCIES...");
        allEntries.addAll(dependenciesFileScanner.scan(jarPath));

        // Stage 5: Embedded JARs
        log("Scanning embedded JARs...");
        allEntries.addAll(embeddedJarScanner.scan(jarPath));

        // Stage 6: Deep class fingerprinting (only if --deep flag set)
        if (deep) {
            log("Running deep class fingerprinting (Maven Central)...");
            allEntries.addAll(deepScanner.scan(jarPath));
        }

        log("Total raw entries: " + allEntries.size());
        return allEntries;
    }

    /**
     * Deduplicate entries: same groupId:artifactId merge, keep highest version.
     */
    public List<LibraryEntry> deduplicate(List<LibraryEntry> entries) {
        log("Deduplicating entries...");
        Map<String, List<LibraryEntry>> grouped = entries.stream()
            .filter(e -> e.getGroupId() != null && e.getArtifactId() != null)
            .collect(Collectors.groupingBy(LibraryEntry::getKey));

        List<LibraryEntry> result = new ArrayList<>();

        for (Map.Entry<String, List<LibraryEntry>> group : grouped.entrySet()) {
            List<LibraryEntry> sameLib = group.getValue();
            // Pick the entry with the highest version (string comparison)
            LibraryEntry best = sameLib.stream()
                .min((a, b) -> compareVersions(b.getVersion(), a.getVersion()))
                .orElse(sameLib.get(0));
            result.add(best);
        }

        // Add entries with no group/artifact (unstructured)
        entries.stream()
            .filter(e -> e.getGroupId() == null || e.getArtifactId() == null)
            .forEach(result::add);

        lastDedupCount = entries.size() - result.size();
        log("After dedup: " + result.size() + " (removed " + lastDedupCount + " duplicates)");
        return result;
    }

    /** Returns how many duplicates were merged in the last dedup call. */
    public int getLastDedupCount() {
        return lastDedupCount;
    }

    /**
     * Filter entries by minimum version.
     */
    public List<LibraryEntry> filterByMinVersion(List<LibraryEntry> entries, String minVersion) {
        log("Filtering by min version: " + minVersion);
        return entries.stream()
            .filter(e -> e.getVersion() != null && compareVersions(e.getVersion(), minVersion) >= 0)
            .collect(Collectors.toList());
    }

    /**
     * Filter entries by groupId:artifactId pattern.
     */
    public List<LibraryEntry> filterByGA(List<LibraryEntry> entries, String pattern) {
        log("Filtering by pattern: " + pattern);
        return entries.stream()
            .filter(e -> e.getDisplayName().contains(pattern))
            .collect(Collectors.toList());
    }

    /**
     * Sort entries alphabetically by groupId:artifactId.
     */
    public List<LibraryEntry> sort(List<LibraryEntry> entries) {
        log("Sorting entries...");
        return entries.stream()
            .sorted(Comparator.comparing(LibraryEntry::getDisplayName)
                .thenComparing(LibraryEntry::getDepth))
            .collect(Collectors.toList());
    }

    /**
     * Simple version string comparison (handles common patterns like 1.4.14, 2.15.3).
     * For production, consider using a real semver library.
     */
    private int compareVersions(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        if (a.equals(b)) return 0;

        String[] partsA = a.split("[._-]");
        String[] partsB = b.split("[._-]");

        int len = Math.max(partsA.length, partsB.length);
        for (int i = 0; i < len; i++) {
            int numA = 0, numB = 0;
            if (i < partsA.length) {
                try { numA = Integer.parseInt(partsA[i]); }
                catch (NumberFormatException e) { numA = 0; }
            }
            if (i < partsB.length) {
                try { numB = Integer.parseInt(partsB[i]); }
                catch (NumberFormatException e) { numB = 0; }
            }
            if (numA != numB) return Integer.compare(numA, numB);
        }
        return 0;
    }

    private void log(String msg) {
        if (verbose) {
            System.err.println("[JVI] " + msg);
        }
    }
}
