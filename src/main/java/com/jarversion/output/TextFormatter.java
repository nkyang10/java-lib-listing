package com.jarversion.output;

import com.jarversion.LibraryEntry;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Formats library entries into a clean text report.
 */
public class TextFormatter {

    private TextFormatter() {}

    /**
     * Generate the full text report.
     */
    public static String format(List<LibraryEntry> entries, Path jarPath, long jarSizeBytes) {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("Jar Version Inspector — Report\n");
        sb.append("===============================\n");
        sb.append("Source: ").append(jarPath.toAbsolutePath().normalize()).append("\n");
        sb.append("Scanned: ")
            .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
            .append("\n\n");

        // Libraries section
        long fromPom = entries.stream()
            .filter(e -> e.getSource() == LibraryEntry.Source.POM_PROPERTIES).count();
        long fromPomXml = entries.stream()
            .filter(e -> e.getSource() == LibraryEntry.Source.POM_XML).count();
        long fromDepsFile = entries.stream()
            .filter(e -> e.getSource() == LibraryEntry.Source.DEPENDENCIES_FILE).count();
        long fromManifest = entries.stream()
            .filter(e -> e.getSource().name().startsWith("MANIFEST_")).count();
        long fromEmbedded = entries.stream()
            .filter(e -> e.getSource() == LibraryEntry.Source.EMBEDDED_JAR).count();

        List<LibraryEntry> pomEntries = entries.stream()
            .filter(e -> e.getSource() == LibraryEntry.Source.POM_PROPERTIES
                || e.getSource() == LibraryEntry.Source.POM_XML
                || e.getSource() == LibraryEntry.Source.DEPENDENCIES_FILE
                || e.getSource() == LibraryEntry.Source.EMBEDDED_JAR)
            .collect(Collectors.toList());

        List<LibraryEntry> manifestEntries = entries.stream()
            .filter(e -> e.getSource().name().startsWith("MANIFEST_"))
            .collect(Collectors.toList());

        // Detect how many were deduplicated vs raw
        long totalListed = pomEntries.size() + manifestEntries.size();

        sb.append("Libraries (").append(totalListed).append(" found)");
        sb.append(":\n");
        sb.append("─".repeat(80)).append("\n");

        if (pomEntries.isEmpty() && manifestEntries.isEmpty()) {
            sb.append("  (no library version data found)\n\n");
        } else {
            for (LibraryEntry entry : pomEntries) {
                String indent = "  ".repeat(entry.getDepth());
                String label = entry.getSource() == LibraryEntry.Source.EMBEDDED_JAR ? " [embedded]" : "";
                sb.append(String.format("  %s%-45s %-15s%s\n",
                    indent, entry.getDisplayName(), entry.getVersion() != null ? entry.getVersion() : "—", label));
            }

            if (!manifestEntries.isEmpty()) {
                sb.append("\nMANIFEST Libraries:\n");
                sb.append("─".repeat(80)).append("\n");
                for (LibraryEntry entry : manifestEntries) {
                    String sourceLabel = switch (entry.getSource()) {
                        case MANIFEST_IMPLEMENTATION -> "(Implementation-Version)";
                        case MANIFEST_BUNDLE -> "(Bundle-Version)";
                        case MANIFEST_SPECIFICATION -> "(Specification-Version)";
                        case MANIFEST_CLASS_PATH -> "(Class-Path)";
                        default -> "";
                    };
                    sb.append(String.format("  %-45s %-15s %s\n",
                        entry.getDisplayName(),
                        entry.getVersion() != null ? entry.getVersion() : "—",
                        sourceLabel));
                }
            }
        }

        // Summary
        sb.append("\nSummary:\n");
        sb.append("─".repeat(80)).append("\n");
        sb.append(String.format("  %-30s %s\n", "Jar size:", formatSize(jarSizeBytes)));
        sb.append(String.format("  %-30s %d\n", "Total entries:", entries.size()));
        sb.append(String.format("  %-30s %d\n", "From pom.properties:", fromPom));
        if (fromPomXml > 0) {
            sb.append(String.format("  %-30s %d\n", "From pom.xml:", fromPomXml));
        }
        if (fromDepsFile > 0) {
            sb.append(String.format("  %-30s %d\n", "From DEPENDENCIES:", fromDepsFile));
        }
        sb.append(String.format("  %-30s %d\n", "From MANIFEST.MF:", fromManifest));
        sb.append(String.format("  %-30s %d\n", "From embedded JARs:", fromEmbedded));

        return sb.toString();
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
