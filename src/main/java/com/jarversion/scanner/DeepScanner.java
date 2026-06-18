package com.jarversion.scanner;

import com.jarversion.LibraryEntry;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Deep scanner: identifies libraries in JARs with missing metadata
 * (e.g., shaded/uber JARs) by fingerprinting class files against
 * Maven Central's search API.
 *
 * Strategy:
 * 1. Group .class files by top-level package prefix
 * 2. Skip packages already identified by other scanners
 * 3. Group remaining packages by 2-segment prefix to minimize queries
 * 4. For each group, pick a representative class and search Maven Central by SHA-1
 * 5. Fallback to fully-qualified class name search
 * 6. Cache results to avoid redundant API calls
 */
public class DeepScanner {

    private static final String MAVEN_SEARCH_URL = "https://search.maven.org/solrsearch/select";
    private static final String CACHE_PATH = System.getProperty("user.home")
        + "/.java-lib-listing/deep-cache.json";
    private static final int MAX_QUERIES = 20;
    // Known library prefixes that won't benefit from deep scan
    // (e.g., org.bouncycastle.* → org.bouncycastle)
    private static final Map<String, String> PACKAGE_TO_LIBRARY = new HashMap<>();
    static {
        // java/stdlib - always skip
        PACKAGE_TO_LIBRARY.put("java", "__SKIP__");
        PACKAGE_TO_LIBRARY.put("javax", "__SKIP__");
        PACKAGE_TO_LIBRARY.put("sun", "__SKIP__");
        PACKAGE_TO_LIBRARY.put("jdk", "__SKIP__");
        PACKAGE_TO_LIBRARY.put("com.sun", "__SKIP__");
        PACKAGE_TO_LIBRARY.put("oracle", "__SKIP__");
        PACKAGE_TO_LIBRARY.put("org.w3c", "__SKIP__");
        PACKAGE_TO_LIBRARY.put("org.xml", "__SKIP__");
        PACKAGE_TO_LIBRARY.put("org.ietf", "__SKIP__");
        PACKAGE_TO_LIBRARY.put("org.omg", "__SKIP__");
        PACKAGE_TO_LIBRARY.put("org.jcp", "__SKIP__");
    }

    private final boolean verbose;
    private final Set<String> knownPrefixes;
    private final Map<String, LibraryEntry> cache; // package prefix → cached result
    private boolean cacheLoaded = false;

    public DeepScanner(boolean verbose) {
        this(verbose, Collections.emptySet());
    }

    public DeepScanner(boolean verbose, Set<String> knownPrefixes) {
        this.verbose = verbose;
        this.knownPrefixes = knownPrefixes != null ? knownPrefixes : Collections.emptySet();
        this.cache = new HashMap<>();
    }

    /**
     * Deep-scan a JAR using class fingerprinting.
     * Only runs when metadata scan is insufficient.
     */
    public List<LibraryEntry> scan(Path jarPath) throws IOException {
        List<LibraryEntry> results = new ArrayList<>();
        loadCache();

        // 1. If there are known G:A pairs with missing versions, look them up
        //    (e.g., from DEPENDENCIES scanner that found name but no version)
        for (Map.Entry<String, LibraryEntry> cacheEntry : cache.entrySet()) {
            LibraryEntry cachedLib = cacheEntry.getValue();
            if (cachedLib != null && cachedLib.getVersion() != null
                && !cachedLib.getVersion().isEmpty()) {
                results.add(cachedLib);
            }
        }
        if (!results.isEmpty()) {
            log("Loaded " + results.size() + " fully-identified entries from cache");
        }

        // 2. Extract class files grouped by package (3-segment)
        Map<String, List<String>> packageClasses = extractClassFiles(jarPath);
        log("Found " + packageClasses.size() + " raw packages");

        // 2. Filter out known packages and group by 2-segment prefix
        Map<String, List<String>> queryGroups = buildQueryGroups(packageClasses);
        log("Built " + queryGroups.size() + " query groups (after skip + grouping)");

        // 3. Query each group
        int queried = 0;
        int groupNum = 0;
        for (Map.Entry<String, List<String>> group : queryGroups.entrySet()) {
            groupNum++;
            String groupPrefix = group.getKey();
            List<String> classes = group.getValue();

            // Check cache first — doesn't count toward query limit
            if (cache.containsKey(groupPrefix)) {
                LibraryEntry cached = cache.get(groupPrefix);
                if (cached != null) {
                    results.add(cached);
                    log("  Cached: " + groupPrefix + " → " + cached.getDisplayName());
                }
                continue;
            }

            if (queried >= MAX_QUERIES) {
                log("Reached max queries (" + MAX_QUERIES + "), stopping");
                break;
            }

            log("[" + groupNum + "/" + queryGroups.size() + "] Querying: " + groupPrefix
                + " (" + classes.size() + " classes)");

            LibraryEntry identified = identifyLibrary(jarPath, classes);
            queried++;
            if (identified != null) {
                results.add(identified);
                cache.put(groupPrefix, identified);
                log("  → " + identified.getDisplayName() + ":" + identified.getVersion());
            } else {
                cache.put(groupPrefix, null);
            }
        }

        saveCache();
        log("Total deep-scan results: " + results.size());
        return results;
    }

    /**
     * Build query groups from raw package map.
     * Skips known prefixes, groups sub-packages by 2-segment prefix,
     * and skips stdlib packages.
     */
    private Map<String, List<String>> buildQueryGroups(Map<String, List<String>> packageClasses) {
        Map<String, List<String>> groups = new LinkedHashMap<>();

        for (Map.Entry<String, List<String>> entry : packageClasses.entrySet()) {
            String pkg = entry.getKey();

            // Skip known packages (already found by other scanners)
            if (isKnownPackage(pkg)) {
                log("  Skip (known): " + pkg);
                continue;
            }

            // Skip stdlib or known non-library packages
            String twoSeg = getTwoSegmentPrefix(pkg);
            if (isSkipPackage(twoSeg) || isSkipPackage(getFirstSegment(pkg))) {
                log("  Skip (stdlib): " + pkg);
                continue;
            }

            // Use 2-segment prefix as group key to reduce queries
            groups.computeIfAbsent(twoSeg, k -> new ArrayList<>()).addAll(entry.getValue());
        }
        return groups;
    }

    /**
     * Check if a package prefix is already known via other scanners.
     */
    private boolean isKnownPackage(String pkg) {
        return knownPrefixes.stream().anyMatch(known ->
            pkg.equals(known) || pkg.startsWith(known + "."));
    }

    /**
     * Get the first 2 segments of a package name for grouping.
     * Examples:
     *   org.bouncycastle.asn1 → org.bouncycastle
     *   com.fasterxml.jackson.core → com.fasterxml.jackson
     *   io.netty.buffer → io.netty
     *   okhttp3 → okhttp3
     */
    static String getTwoSegmentPrefix(String pkg) {
        int firstDot = pkg.indexOf('.');
        if (firstDot < 0) return pkg;
        int secondDot = pkg.indexOf('.', firstDot + 1);
        if (secondDot < 0) return pkg;
        return pkg.substring(0, secondDot);
    }

    /**
     * Get the first segment of a package name.
     * e.g. javafx.scene → javafx, infrasys.gourmate4g → infrasys
     */
    private static String getFirstSegment(String pkg) {
        int dot = pkg.indexOf('.');
        return dot < 0 ? pkg : pkg.substring(0, dot);
    }

    /**
     * Check if a package prefix should be skipped (stdlib, custom code, etc.).
     */
    private static boolean isSkipPackage(String prefix) {
        return "__SKIP__".equals(PACKAGE_TO_LIBRARY.get(prefix));
    }

    /**
     * Extract .class files from JAR, grouped by top-level package prefix.
     */
    Map<String, List<String>> extractClassFiles(Path jarPath) throws IOException {
        Map<String, List<String>> packageMap = new LinkedHashMap<>();

        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                if (!name.endsWith(".class") || name.startsWith("module-info")
                    || name.contains("$")
                    || name.startsWith("META-INF/versions/")) {
                    continue;
                }

                // Convert path to class name
                // e.g., com/fasterxml/jackson/databind/ObjectMapper.class
                String className = name.replace('/', '.')
                    .substring(0, name.length() - ".class".length());

                // Extract top-level package prefix (first 2 segments)
                String topPackage = getTopPackage(className);
                if (topPackage == null) continue;

                packageMap.computeIfAbsent(topPackage, k -> new ArrayList<>()).add(className);
            }
        }

        return packageMap;
    }

    /**
     * Get top-level package prefix (first 3 segments, fallback to 2).
     *
     * Examples:
     *   com.fasterxml.jackson.databind.ObjectMapper → com.fasterxml.jackson
     *   org.springframework.boot.autoconfigure.SpringBootApplication → org.springframework.boot
     *   okhttp3.Request → okhttp3
     *   com.example.Foo → com.example
     *   Foo → null
     */
    static String getTopPackage(String className) {
        int firstDot = className.indexOf('.');
        if (firstDot < 0) return null;

        int secondDot = className.indexOf('.', firstDot + 1);
        if (secondDot < 0) {
            return className.substring(0, firstDot);
        }

        int thirdDot = className.indexOf('.', secondDot + 1);
        if (thirdDot < 0) {
            return className.substring(0, secondDot);
        }

        return className.substring(0, thirdDot);
    }

    /**
     * Identify a library by fingerprinting one of its class files.
     */
    private LibraryEntry identifyLibrary(Path jarPath, List<String> classes) {
        // Try SHA-1 search first (most precise)
        for (String className : classes) {
            LibraryEntry result = identifyBySha1(jarPath, className);
            if (result != null) {
                log("SHA-1 match: " + className + " → " + result.getDisplayName() + ":" + result.getVersion());
                return result;
            }
            // Only try a few per package
            if (classes.indexOf(className) >= 3) break;
        }

        // Fallback: try fc (fully-qualified class name) search
        for (String className : classes) {
            LibraryEntry result = identifyByClassName(className);
            if (result != null) {
                log("fc match: " + className + " → " + result.getDisplayName());
                return result;
            }
            if (classes.indexOf(className) >= 3) break;
        }

        return null;
    }

    /**
     * Search Maven Central by SHA-1 hash of a class file.
     */
    private LibraryEntry identifyBySha1(Path jarPath, String className) {
        try {
            String sha1 = computeSha1(jarPath, className);
            if (sha1 == null) return null;

            String url = MAVEN_SEARCH_URL + "?q=1:" + sha1 + "&rows=1&wt=json";
            String response = httpGet(url);

            return parseMavenResponse(response);
        } catch (Exception e) {
            log("SHA-1 search failed for " + className + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Search Maven Central by fully-qualified class name.
     * Only accepts results where groupId matches the package prefix
     * to avoid false positives (e.g., projects that depend on Bouncy Castle
     * being returned instead of Bouncy Castle itself).
     */
    private LibraryEntry identifyByClassName(String className) {
        try {
            String encoded = URLEncoder.encode(className, StandardCharsets.UTF_8);
            String url = MAVEN_SEARCH_URL + "?q=fc:" + encoded + "&rows=5&wt=json";
            String response = httpGet(url);

            return parseMavenResponseFiltered(response, className);
        } catch (Exception e) {
            log("fc search failed for " + className + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Parse Maven response and filter results to only accept those
     * where groupId matches the class name's package prefix.
     * This prevents false positives where a dependency returns
     * the parent artifact instead of the actual library.
     */
    private LibraryEntry parseMavenResponseFiltered(String json, String className) {
        if (json == null || json.isEmpty()) return null;
        if (json.contains("\"numFound\":0")) return null;

        // Extract the package prefix from the class name
        // e.g., org.bouncycastle.asn1.ASN1Object → org.bouncycastle
        String pkgPrefix = getTwoSegmentPrefix(className);
        if (pkgPrefix == null) return null;

        try {
            // Parse all docs, find first one whose g: matches pkgPrefix
            // Simple approach: extract all (g,a,v) tuples and filter
            List<String> groupIds = extractAllJsonFields(json, "\"g\":\"");
            List<String> artifactIds = extractAllJsonFields(json, "\"a\":\"");
            List<String> versions = extractAllJsonFields(json, "\"v\":\"");

            int size = Math.min(groupIds.size(),
                Math.min(artifactIds.size(), versions.size()));

            for (int i = 0; i < size; i++) {
                String g = groupIds.get(i);
                String a = artifactIds.get(i);
                String v = versions.get(i);

                // Only accept if groupId starts with the package prefix
                // e.g., org.bouncycastle for package org.bouncycastle.*
                if (g != null && g.startsWith(pkgPrefix)) {
                    return new LibraryEntry(g, a, v,
                        LibraryEntry.Source.DEEP_SCAN, 0);
                }
            }

            // No matching result found
            return null;
        } catch (Exception e) {
            log("Failed to parse filtered Maven response: " + e.getMessage());
            return null;
        }
    }

    /**
     * Extract all occurrences of a JSON string field.
     */
    private List<String> extractAllJsonFields(String json, String prefix) {
        List<String> results = new ArrayList<>();
        int start = 0;
        while (true) {
            int idx = json.indexOf(prefix, start);
            if (idx < 0) break;
            idx += prefix.length();
            int end = json.indexOf('"', idx);
            if (end < 0) break;
            results.add(json.substring(idx, end));
            start = end + 1;
        }
        return results;
    }

    /**
     * Compute SHA-1 hash of a class file entry within a JAR.
     */
    private String computeSha1(Path jarPath, String className) {
        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            String entryName = className.replace('.', '/') + ".class";
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) return null;

            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (InputStream is = zip.getInputStream(entry)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }

            StringBuilder sb = new StringBuilder(40);
            for (byte b : digest.digest()) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parse Maven Central search response JSON for G:A:V.
     *
     * Response format:
     * {"response":{"docs":[{"g":"com.google.guava","a":"guava","v":"32.1.3-jre"}]}}
     */
    private LibraryEntry parseMavenResponse(String json) {
        if (json == null || json.isEmpty()) return null;

        try {
            // Check if there are results
            if (json.contains("\"numFound\":0")) return null;

            // Extract g (groupId), a (artifactId), v (version)
            String groupId = extractJsonField(json, "\"g\":\"");
            String artifactId = extractJsonField(json, "\"a\":\"");
            String version = extractJsonField(json, "\"v\":\"");

            if (groupId != null && artifactId != null) {
                return new LibraryEntry(groupId, artifactId, version,
                    LibraryEntry.Source.DEEP_SCAN, 0);
            }
        } catch (Exception e) {
            log("Failed to parse Maven response: " + e.getMessage());
        }
        return null;
    }

    /**
     * Extract a JSON string field value.
     */
    private String extractJsonField(String json, String prefix) {
        int start = json.indexOf(prefix);
        if (start < 0) return null;
        start += prefix.length();
        int end = json.indexOf('"', start);
        if (end < 0) return null;
        return json.substring(start, end);
    }

    /**
     * Simple HTTP GET request using JDK's HttpURLConnection.
     */
    private String httpGet(String urlStr) throws IOException {
        URI uri = URI.create(urlStr);
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "JavaLibListing/1.0");
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(2000);

        try (InputStream is = conn.getResponseCode() == 200
            ? conn.getInputStream() : conn.getErrorStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ── Cache ──

    private void loadCache() {
        if (cacheLoaded) return;
        cacheLoaded = true;
        try {
            java.nio.file.Path cachePath = java.nio.file.Paths.get(CACHE_PATH);
            if (!java.nio.file.Files.exists(cachePath)) return;

            String content = java.nio.file.Files.readString(cachePath);
            // Simple JSON-like cache: one entry per line, pipe-separated
            // format: pkg|groupId|artifactId|version
            for (String line : content.split("\n")) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\|");
                if (parts.length >= 4) {
                    cache.put(parts[0], new LibraryEntry(
                        parts[1], parts[2], parts[3],
                        LibraryEntry.Source.DEEP_SCAN, 0
                    ));
                } else if (parts.length == 1) {
                    cache.put(parts[0], null); // cached negative
                }
            }
            log("Loaded " + cache.size() + " cached entries");
        } catch (Exception e) {
            log("Failed to load cache: " + e.getMessage());
        }
    }

    private void saveCache() {
        try {
            java.nio.file.Path cacheDir = java.nio.file.Paths.get(CACHE_PATH).getParent();
            java.nio.file.Files.createDirectories(cacheDir);

            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, LibraryEntry> entry : cache.entrySet()) {
                if (entry.getValue() != null) {
                    LibraryEntry lib = entry.getValue();
                    sb.append(entry.getKey()).append("|")
                        .append(lib.getGroupId()).append("|")
                        .append(lib.getArtifactId()).append("|")
                        .append(lib.getVersion() != null ? lib.getVersion() : "").append("\n");
                } else {
                    sb.append(entry.getKey()).append("\n");
                }
            }
            java.nio.file.Files.writeString(java.nio.file.Paths.get(CACHE_PATH), sb.toString());
        } catch (Exception e) {
            log("Failed to save cache: " + e.getMessage());
        }
    }

    private void log(String msg) {
        if (verbose) {
            System.err.println("[JVI-deep] " + msg);
        }
    }
}
