package com.jarversion;

import java.util.Objects;

/**
 * Model class representing a single library entry found inside a JAR.
 */
public class LibraryEntry {

    private final String groupId;
    private final String artifactId;
    private final String version;
    private final Source source;
    private final int depth;

    public LibraryEntry(String groupId, String artifactId, String version, Source source, int depth) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.source = source;
        this.depth = depth;
    }

    public String getGroupId() { return groupId; }
    public String getArtifactId() { return artifactId; }
    public String getVersion() { return version; }
    public Source getSource() { return source; }
    public int getDepth() { return depth; }

    /**
     * Unique key for deduplication: groupId:artifactId
     */
    public String getKey() {
        return (groupId != null ? groupId : "") + ":" + (artifactId != null ? artifactId : "");
    }

    /**
     * Display name for output.
     */
    public String getDisplayName() {
        if (groupId != null && artifactId != null) {
            return groupId + ":" + artifactId;
        }
        return artifactId != null ? artifactId : "unknown";
    }

    public enum Source {
        POM_PROPERTIES,
        POM_XML,
        MANIFEST_IMPLEMENTATION,
        MANIFEST_BUNDLE,
        MANIFEST_SPECIFICATION,
        MANIFEST_CLASS_PATH,
        DEPENDENCIES_FILE,
        DEEP_SCAN,
        EMBEDDED_JAR
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LibraryEntry that)) return false;
        return depth == that.depth
            && Objects.equals(groupId, that.groupId)
            && Objects.equals(artifactId, that.artifactId)
            && Objects.equals(version, that.version)
            && source == that.source;
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version, source, depth);
    }

    @Override
    public String toString() {
        return getDisplayName() + ":" + version + " [" + source + "]";
    }
}
