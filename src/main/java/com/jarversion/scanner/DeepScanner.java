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
 * 2. For each package, pick one representative class
 * 3. Search Maven Central by SHA-1 hash (most precise)
 * 4. Fallback to fully-qualified class name search
 * 5. Cache results to avoid redundant API calls
 */
public class DeepScanner {

    private static final String MAVEN_SEARCH_URL = "https://search.maven.org/solrsearch/select";
    private static final String CACHE_PATH = System.getProperty("user.home")
        + "/.java-lib-listing/deep-cache.json";

    private final boolean verbose;
    private final Map<String, LibraryEntry> cache; // package prefix → cached result
    private boolean cacheLoaded = false;

    public DeepScanner(boolean verbose) {
        this.verbose = verbose;
        this.cache = new HashMap<>();
    }

    /**
     * Deep-scan a JAR using class fingerprinting.
     * Only runs when metadata scan is insufficient.
     */
    public List<LibraryEntry> scan(Path jarPath) throws IOException {
        List<LibraryEntry> results = new ArrayList<>();
        loadCache();

        // 1. Extract class files grouped by package
        Map<String, List<String>> packageClasses = extractClassFiles(jarPath);
        log("Found " + packageClasses.size() + " unique packages");

        // 2. For each package, identify the library
        int queried = 0;
        for (Map.Entry<String, List<String>> entry : packageClasses.entrySet()) {
            String pkg = entry.getKey();
            List<String> classes = entry.getValue();

            // Check cache first
            if (cache.containsKey(pkg)) {
                LibraryEntry cached = cache.get(pkg);
                if (cached != null) {
                    results.add(cached);
                    log("Cached: " + pkg + " → " + cached.getDisplayName());
                }
                continue;
            }

            // Query Maven Central
            if (queried >= 50) break; // Safety limit per scan

            LibraryEntry identified = identifyLibrary(jarPath, pkg, classes);
            if (identified != null) {
                results.add(identified);
                cache.put(pkg, identified);
                queried++;
            } else {
                // Cache negative result too
                cache.put(pkg, null);
            }
        }

        saveCache();
        log("Total deep-scan results: " + results.size());
        return results;
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
                    || name.contains("$")) {
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
    private LibraryEntry identifyLibrary(Path jarPath, String pkg, List<String> classes) {
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
     */
    private LibraryEntry identifyByClassName(String className) {
        try {
            String encoded = URLEncoder.encode(className, StandardCharsets.UTF_8);
            String url = MAVEN_SEARCH_URL + "?q=fc:" + encoded + "&rows=1&wt=json";
            String response = httpGet(url);

            return parseMavenResponse(response);
        } catch (Exception e) {
            log("fc search failed for " + className + ": " + e.getMessage());
            return null;
        }
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
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

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
