package com.jarversion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Result of comparing two JAR scans.
 * Categorizes libraries into: upgraded, downgraded, added, removed, unchanged.
 */
public class DiffResult {

    public static class DiffEntry {
        private final String groupId;
        private final String artifactId;
        private final String oldVersion;
        private final String newVersion;
        private final ChangeType type;

        public DiffEntry(String groupId, String artifactId,
                        String oldVersion, String newVersion, ChangeType type) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.oldVersion = oldVersion;
            this.newVersion = newVersion;
            this.type = type;
        }

        public String getGroupId() { return groupId; }
        public String getArtifactId() { return artifactId; }
        public String getOldVersion() { return oldVersion; }
        public String getNewVersion() { return newVersion; }
        public ChangeType getType() { return type; }
        public String getDisplayName() {
            return groupId != null ? groupId + ":" + artifactId : artifactId;
        }
    }

    public enum ChangeType {
        UPGRADED, DOWNGRADED, ADDED, REMOVED, UNCHANGED
    }

    private final List<DiffEntry> entries;
    private final int libraryCountOld;
    private final int libraryCountNew;

    public DiffResult(List<DiffEntry> entries, int libraryCountOld, int libraryCountNew) {
        this.entries = entries;
        this.libraryCountOld = libraryCountOld;
        this.libraryCountNew = libraryCountNew;
    }

    public List<DiffEntry> getEntries() { return entries; }
    public int getLibraryCountOld() { return libraryCountOld; }
    public int getLibraryCountNew() { return libraryCountNew; }

    public boolean hasChanges() {
        return entries.stream().anyMatch(e -> e.getType() != ChangeType.UNCHANGED);
    }

    public List<DiffEntry> getByType(ChangeType type) {
        return entries.stream()
            .filter(e -> e.getType() == type)
            .sorted(Comparator.comparing(DiffEntry::getDisplayName))
            .collect(Collectors.toList());
    }

    /**
     * Compute diff between two library lists.
     */
    public static DiffResult compute(List<LibraryEntry> oldLibs, List<LibraryEntry> newLibs) {
        // Build lookup maps by G:A key
        Map<String, LibraryEntry> oldMap = oldLibs.stream()
            .filter(e -> e.getGroupId() != null && e.getArtifactId() != null)
            .collect(Collectors.toMap(LibraryEntry::getKey, e -> e, (a, b) -> a));

        Map<String, LibraryEntry> newMap = newLibs.stream()
            .filter(e -> e.getGroupId() != null && e.getArtifactId() != null)
            .collect(Collectors.toMap(LibraryEntry::getKey, e -> e, (a, b) -> a));

        // Also handle entries without groupId (MANIFEST entries)
        Map<String, LibraryEntry> oldManifest = oldLibs.stream()
            .filter(e -> e.getGroupId() == null || e.getArtifactId() == null)
            .collect(Collectors.toMap(LibraryEntry::getDisplayName, e -> e, (a, b) -> a));

        Map<String, LibraryEntry> newManifest = newLibs.stream()
            .filter(e -> e.getGroupId() == null || e.getArtifactId() == null)
            .collect(Collectors.toMap(LibraryEntry::getDisplayName, e -> e, (a, b) -> a));

        List<DiffEntry> results = new ArrayList<>();

        // Check all G:A pairs
        for (String key : oldMap.keySet()) {
            LibraryEntry oldEntry = oldMap.get(key);
            LibraryEntry newEntry = newMap.get(key);
            if (newEntry == null) {
                results.add(new DiffEntry(oldEntry.getGroupId(), oldEntry.getArtifactId(),
                    oldEntry.getVersion(), null, ChangeType.REMOVED));
            } else {
                String oldV = oldEntry.getVersion() != null ? oldEntry.getVersion() : "";
                String newV = newEntry.getVersion() != null ? newEntry.getVersion() : "";
                if (oldV.equals(newV)) {
                    results.add(new DiffEntry(oldEntry.getGroupId(), oldEntry.getArtifactId(),
                        oldV, newV, ChangeType.UNCHANGED));
                } else if (compareVersions(newV, oldV) > 0) {
                    results.add(new DiffEntry(oldEntry.getGroupId(), oldEntry.getArtifactId(),
                        oldV, newV, ChangeType.UPGRADED));
                } else {
                    results.add(new DiffEntry(oldEntry.getGroupId(), oldEntry.getArtifactId(),
                        oldV, newV, ChangeType.DOWNGRADED));
                }
            }
        }

        // Added entries
        for (String key : newMap.keySet()) {
            if (!oldMap.containsKey(key)) {
                LibraryEntry newEntry = newMap.get(key);
                results.add(new DiffEntry(newEntry.getGroupId(), newEntry.getArtifactId(),
                    null, newEntry.getVersion(), ChangeType.ADDED));
            }
        }

        // Unstructured entries (no G:A)
        for (String name : oldManifest.keySet()) {
            LibraryEntry oldEntry = oldManifest.get(name);
            LibraryEntry newEntry = newManifest.get(name);
            if (newEntry == null) {
                results.add(new DiffEntry(null, oldEntry.getDisplayName(),
                    oldEntry.getVersion(), null, ChangeType.REMOVED));
            } else {
                results.add(new DiffEntry(null, oldEntry.getDisplayName(),
                    oldEntry.getVersion(), newEntry.getVersion(), ChangeType.UNCHANGED));
            }
        }
        for (String name : newManifest.keySet()) {
            if (!oldManifest.containsKey(name)) {
                LibraryEntry newEntry = newManifest.get(name);
                results.add(new DiffEntry(null, newEntry.getDisplayName(),
                    null, newEntry.getVersion(), ChangeType.ADDED));
            }
        }

        return new DiffResult(results, oldLibs.size(), newLibs.size());
    }

    /** Simple version comparison (same as ScannerEngine). */
    private static int compareVersions(String a, String b) {
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
}
