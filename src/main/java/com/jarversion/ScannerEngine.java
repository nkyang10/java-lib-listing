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
        this.deepScanner = new DeepScanner(verbose, Collections.emptySet());
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
            // Collect known package prefixes from already-found entries
            // so DeepScanner doesn't waste time on them
            Set<String> knownPrefixes = extractKnownPrefixes(allEntries);
            log("Known package prefixes to skip: " + knownPrefixes.size());
            DeepScanner scopedScanner = new DeepScanner(verbose, knownPrefixes);
            allEntries.addAll(scopedScanner.scan(jarPath));

            // Stage 6b: Look up versions for entries with G:A but no version
            // (e.g., from DEPENDENCIES scanner that found artifact name only)
            int versionLookups = 0;
            for (int i = 0; i < allEntries.size(); i++) {
                LibraryEntry entry = allEntries.get(i);
                if (entry.getGroupId() != null && entry.getArtifactId() != null
                    && (entry.getVersion() == null || entry.getVersion().isEmpty())) {
                    String version = lookupVersionByGA(entry.getGroupId(), entry.getArtifactId());
                    if (version != null) {
                        allEntries.set(i, new LibraryEntry(
                            entry.getGroupId(), entry.getArtifactId(), version,
                            entry.getSource(), entry.getDepth()));
                        versionLookups++;
                    }
                }
            }
            if (versionLookups > 0) {
                log("Looked up " + versionLookups + " versions by G:A");
            }
        }

        log("Total raw entries: " + allEntries.size());
        return allEntries;
    }

    /**
     * Extract package prefixes from already-found library entries.
     * These tell DeepScanner which libraries are already identified.
     */
    private Set<String> extractKnownPrefixes(List<LibraryEntry> entries) {
        Set<String> prefixes = new HashSet<>();
        for (LibraryEntry entry : entries) {
            String g = entry.getGroupId();
            String a = entry.getArtifactId();
            if (g != null) {
                // Add full groupId as known prefix
                prefixes.add(g);
                // Also add org.bouncycastle style (groupId = org.bouncycastle)
                // Note: For entries with partial metadata, don't skip them
                if (entry.getVersion() != null && a != null) {
                    // Only skip fully-identified libraries
                    prefixes.add(g + "." + a); // com.fasterxml.jackson.core
                }
            }
        }
        return prefixes;
    }

    /**
     * Look up the latest version of a library by G:A from Maven Central.
     * Uses the search API to find the latest version of a known artifact.
     */
    private String lookupVersionByGA(String groupId, String artifactId) {
        try {
            String encodedG = java.net.URLEncoder.encode(groupId, java.nio.charset.StandardCharsets.UTF_8);
            String encodedA = java.net.URLEncoder.encode(artifactId, java.nio.charset.StandardCharsets.UTF_8);
            String url = "https://search.maven.org/solrsearch/select?q=g:"
                + encodedG + "+AND+a:" + encodedA + "&rows=1&wt=json";
            java.net.URI uri = java.net.URI.create(url);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "JavaLibListing/1.0");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            String response;
            try (java.io.InputStream is = conn.getResponseCode() == 200
                ? conn.getInputStream() : conn.getErrorStream()) {
                response = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }

            if (response == null || response.contains("\"numFound\":0")) return null;

            // Extract latestVersion field
            // Format: {"response":{"docs":[{"latestVersion":"1.80",...}]}}
            String marker = "\"latestVersion\":\"";
            int start = response.indexOf(marker);
            if (start < 0) return null;
            start += marker.length();
            int end = response.indexOf('"', start);
            if (end < 0) return null;
            return response.substring(start, end);
        } catch (Exception e) {
            log("Version lookup failed for " + groupId + ":" + artifactId + " - " + e.getMessage());
            return null;
        }
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
