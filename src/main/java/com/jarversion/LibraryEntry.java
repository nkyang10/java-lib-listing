package com.jarversion;

import java.util.Objects;

/**
 * Model class representing a single library entry found inside a JAR.
 */
public class LibraryEntry {

    public static final String ROOT_PARENT = "ROOT";
    public static final String UNKNOWN_LABEL = "unknown";

    private final String groupId;
    private final String artifactId;
    private final String version;
    private final Source source;
    private final int depth;
    private final String parentName;

    public LibraryEntry(String groupId, String artifactId, String version, Source source, int depth) {
        this(groupId, artifactId, version, source, depth, null);
    }

    public LibraryEntry(String groupId, String artifactId, String version, Source source, int depth, String parentName) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.source = source;
        this.depth = depth;
        this.parentName = parentName;
    }

    public String getGroupId() { return groupId; }
    public String getArtifactId() { return artifactId; }
    public String getVersion() { return version; }
    public Source getSource() { return source; }
    public int getDepth() { return depth; }
    public String getParentName() { return parentName; }

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
        return artifactId != null ? artifactId : UNKNOWN_LABEL;
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
            && source == that.source
            && Objects.equals(parentName, that.parentName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version, source, depth, parentName);
    }

    @Override
    public String toString() {
        String base = getDisplayName() + ":" + version + " [" + source + "]";
        if (parentName != null) {
            base += " parent=" + parentName;
        }
        return base;
    }
}
