package com.jarversion.output;

import com.jarversion.LibraryEntry;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Formats library entries as JSON for CI/CD pipeline consumption.
 */
public class JsonFormatter {

    private JsonFormatter() {}

    /**
     * Generate JSON report.
     */
    public static String format(List<LibraryEntry> entries, Path jarPath,
                                 long jarSizeBytes, int dedupCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

        // Metadata
        sb.append("  \"tool\": \"jar-version-inspector\",\n");
        sb.append("  \"version\": \"1.0.0\",\n");
        sb.append("  \"source\": \"").append(escapeJson(jarPath.toAbsolutePath().normalize().toString())).append("\",\n");
        sb.append("  \"scanned\": \"")
            .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
            .append("\",\n");
        sb.append("  \"jarSizeBytes\": ").append(jarSizeBytes).append(",\n");
        sb.append("  \"libraryCount\": ").append(entries.size()).append(",\n");
        sb.append("  \"dedupCount\": ").append(dedupCount).append(",\n");

        // Libraries array
        sb.append("  \"libraries\": [\n");
        for (int i = 0; i < entries.size(); i++) {
            LibraryEntry e = entries.get(i);
            sb.append("    {\n");
            sb.append("      \"displayName\": \"").append(escapeJson(e.getDisplayName())).append("\",\n");
            if (e.getGroupId() != null) {
                sb.append("      \"groupId\": \"").append(escapeJson(e.getGroupId())).append("\",\n");
            }
            sb.append("      \"artifactId\": \"").append(escapeJson(
                e.getArtifactId() != null ? e.getArtifactId() : "")).append("\",\n");
            sb.append("      \"version\": \"").append(escapeJson(
                e.getVersion() != null ? e.getVersion() : "")).append("\",\n");
            sb.append("      \"source\": \"").append(e.getSource().name()).append("\",\n");
            sb.append("      \"depth\": ").append(e.getDepth()).append("\n");
            sb.append("    }");
            if (i < entries.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("  ],\n");

        // Summary by source
        sb.append("  \"summary\": {\n");
        long fromPom = countBySource(entries, LibraryEntry.Source.POM_PROPERTIES);
        long fromPomXml = countBySource(entries, LibraryEntry.Source.POM_XML);
        long fromManifest = entries.stream()
            .filter(e -> e.getSource().name().startsWith("MANIFEST_")).count();
        long fromEmbedded = countBySource(entries, LibraryEntry.Source.EMBEDDED_JAR);
        long fromDepsFile = countBySource(entries, LibraryEntry.Source.DEPENDENCIES_FILE);
        long fromDeep = countBySource(entries, LibraryEntry.Source.DEEP_SCAN);

        sb.append("    \"fromPomProperties\": ").append(fromPom).append(",\n");
        sb.append("    \"fromPomXml\": ").append(fromPomXml).append(",\n");
        sb.append("    \"fromManifest\": ").append(fromManifest).append(",\n");
        sb.append("    \"fromEmbeddedJars\": ").append(fromEmbedded).append(",\n");
        sb.append("    \"fromDependenciesFile\": ").append(fromDepsFile).append(",\n");
        sb.append("    \"fromDeepScan\": ").append(fromDeep).append("\n");
        sb.append("  }\n");

        sb.append("}\n");
        return sb.toString();
    }

    private static long countBySource(List<LibraryEntry> entries, LibraryEntry.Source source) {
        return entries.stream().filter(e -> e.getSource() == source).count();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
