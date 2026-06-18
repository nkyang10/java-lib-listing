package com.jarversion.output;

import com.jarversion.LibraryEntry;
import com.jarversion.VersionUtils;

import static com.jarversion.LibraryEntry.ROOT_PARENT;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Renders library entries as a dependency tree (─ ├ └).
 * Groups embedded entries under their parent JAR filenames,
 * showing which library references which.
 */
public class TreeFormatter {

    private TreeFormatter() {}

    /**
     * Generate a tree-format report showing the dependency reference stack.
     */
    public static String format(List<LibraryEntry> entries, Path jarPath,
                                 long jarSizeBytes, int dedupCount) {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("Jar Version Inspector — Dependency Tree\n");
        sb.append("========================================\n");
        sb.append("Source: ").append(jarPath.toAbsolutePath().normalize()).append("\n");
        sb.append("Scanned: ")
            .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
            .append("\n\n");

        // Build the tree
        TreeNode root = buildTree(entries);

        // Count total libraries
        int libCount = countLibraries(root);

        sb.append("Libraries (").append(libCount).append(" found)").append(":\n");
        sb.append("─".repeat(80)).append("\n");

        if (libCount == 0) {
            sb.append("  (no library version data found)\n\n");
        } else {
            renderTree(sb, root, "", true);
        }

        // Summary
        sb.append("\nSummary:\n");
        sb.append("─".repeat(80)).append("\n");
        sb.append(String.format("  %-30s %s\n", "Jar size:", formatSize(jarSizeBytes)));
        sb.append(String.format("  %-30s %d\n", "Total entries:", entries.size()));
        if (dedupCount > 0) {
            sb.append(String.format("  %-30s %d\n", "Duplicates merged:", dedupCount));
        }

        // Source breakdown
        long fromPom = entries.stream()
            .filter(e -> e.getSource() == LibraryEntry.Source.POM_PROPERTIES).count();
        long fromPomXml = entries.stream()
            .filter(e -> e.getSource() == LibraryEntry.Source.POM_XML).count();
        long fromEmbedded = entries.stream()
            .filter(e -> e.getSource() == LibraryEntry.Source.EMBEDDED_JAR).count();
        long fromManifest = entries.stream()
            .filter(e -> e.getSource().name().startsWith("MANIFEST_")).count();
        sb.append(String.format("  %-30s %d\n", "From pom.properties:", fromPom));
        if (fromPomXml > 0) sb.append(String.format("  %-30s %d\n", "From pom.xml:", fromPomXml));
        sb.append(String.format("  %-30s %d\n", "From MANIFEST.MF:", fromManifest));
        sb.append(String.format("  %-30s %d\n", "From embedded JARs:", fromEmbedded));

        return sb.toString();
    }

    /** A node in the dependency tree. */
    static class TreeNode {
        final String label;
        final String version;
        final LibraryEntry.Source source;
        final List<TreeNode> children = new ArrayList<>();

        TreeNode(String label, String version, LibraryEntry.Source source) {
            this.label = label;
            this.version = version;
            this.source = source;
        }
    }

    /**
     * Build a dependency tree from flat library entries.
     *
     * Tree structure:
     * - Root node = the scanned JAR
     * - Depth-0 entries (POM, manifest, etc.) are direct children
     * - Depth-1+ entries (EMBEDDED_JAR) are grouped by their parentName path
     */
    static TreeNode buildTree(List<LibraryEntry> entries) {
        TreeNode root = new TreeNode("(root JAR)", null, null);

        // Separate root-level and embedded entries
        List<LibraryEntry> rootEntries = entries.stream()
            .filter(e -> e.getDepth() == 0 || e.getSource() != LibraryEntry.Source.EMBEDDED_JAR)
            .sorted(Comparator.comparing(LibraryEntry::getDisplayName))
            .collect(Collectors.toList());

        List<LibraryEntry> embeddedEntries = entries.stream()
            .filter(e -> e.getDepth() > 0 && e.getSource() == LibraryEntry.Source.EMBEDDED_JAR)
            .collect(Collectors.toList());

        // Add root-level entries directly under root
        for (LibraryEntry e : rootEntries) {
            root.children.add(new TreeNode(e.getDisplayName(), e.getVersion(), e.getSource()));
        }

        // Group embedded entries by their parentName path chain
        // parentName format: ROOT/<depth1-filename>/<depth2-filename>/...
        // Insert each entry under the correct parent node
        Map<String, TreeNode> pathIndex = new HashMap<>();
        pathIndex.put(ROOT_PARENT, root);

        for (LibraryEntry e : embeddedEntries) {
            String parentPath = e.getParentName() != null ? e.getParentName() : ROOT_PARENT;
            String entryKey = parentPath + "/" + e.getDisplayName() + ":" + e.getVersion();

            TreeNode node = new TreeNode(e.getDisplayName(), e.getVersion(), e.getSource());

            // Find or create the parent container (embedded JAR filename)
            TreeNode parent = findOrCreateContainer(pathIndex, parentPath, root);
            if (parent != null) {
                parent.children.add(node);
            } else {
                // Fallback: add to root
                root.children.add(node);
            }

            pathIndex.put(entryKey, node);
        }

        // Sort children for stable output
        sortTree(root);
        return root;
    }

    /**
     * Find or create container nodes for a parentName path.
     * parentName = "ROOT/spring-boot-3.1.5.jar"
     * This ensures the tree has intermediate nodes showing JAR filenames.
     */
    private static TreeNode findOrCreateContainer(Map<String, TreeNode> pathIndex,
                                                    String parentName, TreeNode root) {
        if (parentName == null || parentName.equals(ROOT_PARENT)) return root;

        // Walk the path segments, creating container nodes as needed
        String[] segments = parentName.split("/");
        TreeNode current = root;

        for (int i = 0; i < segments.length; i++) {
            String seg = segments[i];
            if (seg.isEmpty() || seg.equals(ROOT_PARENT)) continue;

            String pathSoFar = buildPath(segments, i);

            // Check if this container node already exists
            TreeNode container = pathIndex.get(pathSoFar);
            if (container == null) {
                // Use the filename (last segment) as the label
                container = new TreeNode(seg, null, null);
                current.children.add(container);
                pathIndex.put(pathSoFar, container);
            }
            current = container;
        }
        return current;
    }

    private static String buildPath(String[] segments, int upTo) {
        StringBuilder sb = new StringBuilder(ROOT_PARENT);
        for (int i = 0; i <= upTo; i++) {
            String seg = segments[i];
            if (!seg.isEmpty() && !seg.equals(ROOT_PARENT)) {
                sb.append("/").append(seg);
            }
        }
        return sb.toString();
    }

    private static void sortTree(TreeNode node) {
        node.children.sort((a, b) -> {
            // Container nodes (no version/source) come first
            boolean aIsContainer = a.version == null;
            boolean bIsContainer = b.version == null;
            if (aIsContainer != bIsContainer) return aIsContainer ? -1 : 1;
            return a.label.compareToIgnoreCase(b.label);
        });
        for (TreeNode child : node.children) {
            sortTree(child);
        }
    }

    private static int countLibraries(TreeNode node) {
        int count = 0;
        for (TreeNode child : node.children) {
            if (child.version != null) count++; // It's a library, not a container
            count += countLibraries(child);
        }
        return count;
    }

    /**
     * Render the tree with box-drawing characters.
     */
    private static void renderTree(StringBuilder sb, TreeNode node,
                                    String prefix, boolean isLast) {
        List<TreeNode> children = node.children;
        for (int i = 0; i < children.size(); i++) {
            TreeNode child = children.get(i);
            boolean lastChild = (i == children.size() - 1);

            // Branch line
            sb.append(prefix);
            sb.append(lastChild ? "  └── " : "  ├── ");

            // Label
            sb.append(child.label);
            if (child.version != null) {
                sb.append("  ").append(child.version);
            }

            // Source tag for embedded libraries
            if (child.source != null && child.source == LibraryEntry.Source.EMBEDDED_JAR) {
                sb.append("  [embedded]");
            } else if (child.source != null && child.source.name().startsWith("MANIFEST_")) {
                String tag = switch (child.source) {
                    case MANIFEST_IMPLEMENTATION -> "  (Implementation-Version)";
                    case MANIFEST_BUNDLE -> "  (Bundle-Version)";
                    case MANIFEST_SPECIFICATION -> "  (Specification-Version)";
                    case MANIFEST_CLASS_PATH -> "  (Class-Path)";
                    default -> "";
                };
                sb.append(tag);
            }

            sb.append("\n");

            // Recurse with extended prefix
            String childPrefix = prefix + (lastChild ? "    " : "  │   ");
            renderTree(sb, child, childPrefix, lastChild);
        }
    }

    // ── Color output ──

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_BOLD = "\u001B[1m";

    /** Format with ANSI color codes for terminal. */
    public static String formatColor(List<LibraryEntry> entries, Path jarPath,
                                      long jarSizeBytes, int dedupCount) {
        String plain = format(entries, jarPath, jarSizeBytes, dedupCount);

        plain = plain.replace("Jar Version Inspector",
            ANSI_BOLD + "Jar Version Inspector" + ANSI_RESET);
        plain = plain.replace("Summary:",
            ANSI_BOLD + ANSI_CYAN + "Summary:" + ANSI_RESET);
        plain = plain.replace("Libraries (",
            ANSI_BOLD + "Libraries (" + ANSI_RESET);
        plain = plain.replaceAll("(\\d+\\.\\d+[\\w.-]*)",
            ANSI_GREEN + "$1" + ANSI_RESET);

        return plain;
    }

    private static String formatSize(long bytes) {
        return VersionUtils.formatSize(bytes);
    }
}
