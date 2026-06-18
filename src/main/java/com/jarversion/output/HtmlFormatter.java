package com.jarversion.output;

import com.jarversion.DiffResult;
import com.jarversion.DiffResult.ChangeType;
import com.jarversion.DiffResult.DiffEntry;
import com.jarversion.LibraryEntry;
import com.jarversion.VersionUtils;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Formats scan results as a standalone HTML report.
 */
public class HtmlFormatter {

    private HtmlFormatter() {}

    private static final String CSS = ""
        + "<style>\n"
        + "  body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; "
        + "background: #0d1117; color: #c9d1d9; margin: 0; padding: 20px; }\n"
        + "  h1 { color: #58a6ff; border-bottom: 1px solid #30363d; padding-bottom: 8px; }\n"
        + "  h2 { color: #58a6ff; margin-top: 24px; }\n"
        + "  table { border-collapse: collapse; width: 100%; margin: 8px 0 16px 0; }\n"
        + "  th, td { text-align: left; padding: 6px 12px; border-bottom: 1px solid #21262d; }\n"
        + "  th { background: #161b22; color: #8b949e; font-weight: 600; position: sticky; top: 0; }\n"
        + "  tr:hover { background: #1c2128; }\n"
        + "  .upgraded { color: #3fb950; }\n"
        + "  .downgraded { color: #f85149; }\n"
        + "  .added { color: #3fb950; }\n"
        + "  .removed { color: #f85149; }\n"
        + "  .unchanged { color: #8b949e; }\n"
        + "  .meta { color: #8b949e; font-size: 13px; }\n"
        + "  .badge { display: inline-block; padding: 2px 8px; border-radius: 12px; "
        + "font-size: 12px; font-weight: 600; margin-right: 4px; }\n"
        + "  .badge-up { background: #1b3a1f; color: #3fb950; }\n"
        + "  .badge-down { background: #3a1b1b; color: #f85149; }\n"
        + "  .badge-add { background: #1b3a1f; color: #3fb950; }\n"
        + "  .badge-rm { background: #3a1b1b; color: #f85149; }\n"
        + "  .badge-same { background: #21262d; color: #8b949e; }\n"
        + "  .summary-box { background: #161b22; border: 1px solid #30363d; border-radius: 8px; "
        + "padding: 16px; margin: 16px 0; }\n"
        + "  .arrow { color: #58a6ff; margin: 0 6px; }\n"
        + "</style>\n";

    public static String format(String title, List<LibraryEntry> entries, Path jarPath,
                                 long jarSizeBytes, int dedupCount) {
        long fromPom = entries.stream().filter(e -> e.getSource() == LibraryEntry.Source.POM_PROPERTIES).count();
        long fromManifest = entries.stream().filter(e -> e.getSource().name().startsWith("MANIFEST_")).count();
        long fromEmbedded = entries.stream().filter(e -> e.getSource() == LibraryEntry.Source.EMBEDDED_JAR).count();
        long fromDeep = entries.stream().filter(e -> e.getSource() == LibraryEntry.Source.DEEP_SCAN).count();

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html><head><meta charset='utf-8'>\n");
        sb.append("<title>JAR Scan: ").append(escapeHtml(jarPath.getFileName().toString())).append("</title>\n");
        sb.append(CSS).append("</head><body>\n");
        sb.append("<h1>📦 JAR Version Inspector</h1>\n");
        sb.append("<p class='meta'>").append(escapeHtml(jarPath.toAbsolutePath().normalize().toString())).append("<br>\n");
        sb.append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("</p>\n");

        // Summary box
        sb.append("<div class='summary-box'>\n");
        sb.append("<strong>Summary</strong><br>\n");
        sb.append("Jar size: ").append(formatSizeHtml(jarSizeBytes)).append("<br>\n");
        sb.append("Libraries: ").append(entries.size()).append("<br>\n");
        if (dedupCount > 0) sb.append("Duplicates merged: ").append(dedupCount).append("<br>\n");
        sb.append("From pom.properties: ").append(fromPom).append("<br>\n");
        sb.append("From MANIFEST.MF: ").append(fromManifest).append("<br>\n");
        sb.append("From embedded JARs: ").append(fromEmbedded).append("<br>\n");
        if (fromDeep > 0) sb.append("From deep scan: ").append(fromDeep).append("<br>\n");
        sb.append("</div>\n");

        // Table
        sb.append("<table>\n<thead><tr>"
            + "<th>Library</th><th>Version</th><th>Source</th></tr></thead>\n<tbody>\n");
        for (LibraryEntry entry : entries) {
            String indent = entry.getDepth() > 0 ? "&nbsp;&nbsp;".repeat(entry.getDepth()) : "";
            String label = entry.getSource() == LibraryEntry.Source.EMBEDDED_JAR
                ? " <span class='badge badge-same'>embedded</span>" : "";
            sb.append("<tr><td>").append(indent).append(escapeHtml(entry.getDisplayName()))
                .append(label).append("</td>\n");
            sb.append("<td><code>").append(escapeHtml(entry.getVersion() != null ? entry.getVersion() : "—"))
                .append("</code></td>\n");
            sb.append("<td class='meta'>").append(entry.getSource().name()).append("</td></tr>\n");
        }
        sb.append("</tbody></table>\n");
        sb.append("</body></html>\n");
        return sb.toString();
    }

    public static String formatDiff(DiffResult diff, Path jarPath1, Path jarPath2) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html><head><meta charset='utf-8'>\n");
        sb.append("<title>JAR DIFF: ").append(escapeHtml(jarPath1.getFileName().toString()))
            .append(" vs ").append(escapeHtml(jarPath2.getFileName().toString())).append("</title>\n");
        sb.append(CSS).append("</head><body>\n");
        sb.append("<h1>📊 JAR Version Inspector — DIFF</h1>\n");
        sb.append("<p class='meta'>Old: ").append(escapeHtml(jarPath1.toAbsolutePath().normalize().toString()))
            .append("<br>\n");
        sb.append("New: ").append(escapeHtml(jarPath2.toAbsolutePath().normalize().toString())).append("<br>\n");
        sb.append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("</p>\n");

        // Summary
        sb.append("<div class='summary-box'>\n");
        sb.append("<strong>Summary</strong><br>\n");
        sb.append("Old: ").append(diff.getLibraryCountOld()).append(" libraries<br>\n");
        sb.append("New: ").append(diff.getLibraryCountNew()).append(" libraries<br>\n");
        int up = diff.getByType(ChangeType.UPGRADED).size();
        int down = diff.getByType(ChangeType.DOWNGRADED).size();
        int add = diff.getByType(ChangeType.ADDED).size();
        int rm = diff.getByType(ChangeType.REMOVED).size();
        int same = diff.getByType(ChangeType.UNCHANGED).size();
        sb.append("<span class='badge badge-up'>⬆ ").append(up).append(" upgraded</span> ");
        sb.append("<span class='badge badge-down'>⬇ ").append(down).append(" downgraded</span> ");
        sb.append("<span class='badge badge-add'>🆕 ").append(add).append(" added</span> ");
        sb.append("<span class='badge badge-rm'>❌ ").append(rm).append(" removed</span> ");
        sb.append("<span class='badge badge-same'>" ).append(same).append(" unchanged</span>\n");
        sb.append("</div>\n");

        // Diff table
        sb.append("<table>\n<thead><tr>"
            + "<th>Library</th><th>Old Version</th><th></th><th>New Version</th></tr></thead>\n<tbody>\n");
        for (DiffEntry entry : diff.getEntries()) {
            String cls = switch (entry.getType()) {
                case UPGRADED -> "upgraded";
                case DOWNGRADED -> "downgraded";
                case ADDED -> "added";
                case REMOVED -> "removed";
                case UNCHANGED -> "unchanged";
            };
            String icon = switch (entry.getType()) {
                case UPGRADED -> "⬆";
                case DOWNGRADED -> "⬇";
                case ADDED -> "🆕";
                case REMOVED -> "❌";
                case UNCHANGED -> "—";
            };
            String oldV = entry.getOldVersion() != null ? entry.getOldVersion() : "—";
            String newV = entry.getNewVersion() != null ? entry.getNewVersion() : "—";
            sb.append("<tr class='").append(cls).append("'>");
            sb.append("<td>").append(escapeHtml(entry.getDisplayName())).append("</td>");
            sb.append("<td><code>").append(escapeHtml(oldV)).append("</code></td>");
            sb.append("<td class='arrow'>").append(icon).append("</td>");
            sb.append("<td><code>").append(escapeHtml(newV)).append("</code></td>");
            sb.append("</tr>\n");
        }
        sb.append("</tbody></table>\n");
        sb.append("</body></html>\n");
        return sb.toString();
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    private static String formatSizeHtml(long bytes) {
        return VersionUtils.formatSize(bytes);
    }
}
