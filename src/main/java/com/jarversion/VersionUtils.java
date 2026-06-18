package com.jarversion;

/**
 * Shared utility methods for version comparison and formatting.
 */
public final class VersionUtils {

    private VersionUtils() {}

    /**
     * Simple version string comparison (handles common patterns like 1.4.14, 2.15.3).
     * Returns negative if a &lt; b, positive if a &gt; b, zero if equal.
     */
    public static int compareVersions(String a, String b) {
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

    /**
     * Format byte size to human-readable string.
     */
    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
