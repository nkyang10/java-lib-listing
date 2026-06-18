package com.jarversion.scanner;

import com.jarversion.LibraryEntry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Deep scanner: identifies class file packages in JARs with missing metadata
 * (e.g., shaded/uber JARs).
 *
 * This scanner does NOT guess versions — it only lists class file packages
 * that were not already identified by standard metadata scanners (POM, manifest).
 * Exact version info must come from pom.properties, pom.xml, MANIFEST.MF,
 * or DEPENDENCIES files scanned by the standard scanners.
 *
 * Strategy:
 * 1. Extract .class files grouped by package prefix
 * 2. Skip packages already identified by other scanners (knownPrefixes)
 * 3. Skip stdlib packages (java, javax, com.sun, etc.)
 * 4. Remaining packages logged for user awareness
 */
public class DeepScanner {

    // Known library prefixes that won't benefit from deep scan
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

    public DeepScanner(boolean verbose) {
        this(verbose, Collections.emptySet());
    }

    public DeepScanner(boolean verbose, Set<String> knownPrefixes) {
        this.verbose = verbose;
        this.knownPrefixes = knownPrefixes != null ? knownPrefixes : Collections.emptySet();
    }

    /**
     * Deep-scan a JAR.
     * Returns only entries already found by other scanners (no version guessing).
     * Unknown packages are logged for user awareness.
     */
    public List<LibraryEntry> scan(Path jarPath) throws IOException {
        List<LibraryEntry> results = new ArrayList<>();

        // Extract class files grouped by package
        Map<String, List<String>> packageClasses = extractClassFiles(jarPath);
        log("Found " + packageClasses.size() + " raw packages");

        // Check for unknown packages that weren't identified by standard scanners
        int unknownCount = 0;
        for (Map.Entry<String, List<String>> entry : packageClasses.entrySet()) {
            String pkg = entry.getKey();

            // Skip known packages (already found by other scanners)
            if (isKnownPackage(pkg)) continue;

            // Skip stdlib / known non-library packages
            if (isSkipPackage(pkg)) continue;

            unknownCount++;
            log("  Unknown: " + pkg + " (" + entry.getValue().size() + " classes)");
        }

        if (unknownCount > 0) {
            log("Warning: " + unknownCount + " unknown package(s) found — "
                + "these libraries have no version metadata in the JAR.");
        }

        log("Total deep-scan results: " + results.size());
        return results;
    }

    private boolean isKnownPackage(String pkg) {
        return knownPrefixes.stream().anyMatch(known ->
            pkg.equals(known) || pkg.startsWith(known + "."));
    }

    private boolean isSkipPackage(String pkg) {
        if ("__SKIP__".equals(PACKAGE_TO_LIBRARY.get(pkg))) return true;
        if ("__SKIP__".equals(PACKAGE_TO_LIBRARY.get(getFirstSegment(pkg)))) return true;
        return "__SKIP__".equals(PACKAGE_TO_LIBRARY.get(getTwoSegmentPrefix(pkg)));
    }

    private static String getTwoSegmentPrefix(String pkg) {
        int firstDot = pkg.indexOf('.');
        if (firstDot < 0) return pkg;
        int secondDot = pkg.indexOf('.', firstDot + 1);
        if (secondDot < 0) return pkg;
        return pkg.substring(0, secondDot);
    }

    private static String getFirstSegment(String pkg) {
        int dot = pkg.indexOf('.');
        return dot < 0 ? pkg : pkg.substring(0, dot);
    }

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

                String className = name.replace('/', '.')
                    .substring(0, name.length() - ".class".length());

                String pkg = getPackagePrefix(className);
                if (pkg == null) continue;

                packageMap.computeIfAbsent(pkg, k -> new ArrayList<>()).add(className);
            }
        }

        return packageMap;
    }

    static String getPackagePrefix(String className) {
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

    private void log(String msg) {
        if (verbose) {
            System.err.println("[JVI-deep] " + msg);
        }
    }
}
