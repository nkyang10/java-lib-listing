package com.jarversion.output;

import com.jarversion.DiffResult;
import com.jarversion.DiffResult.ChangeType;
import com.jarversion.DiffResult.DiffEntry;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Formats DIFF results as a text report.
 */
public class DiffFormatter {

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_BOLD = "\u001B[1m";

    private DiffFormatter() {}

    public static String format(DiffResult diff, Path jarPath1, Path jarPath2) {
        return formatInternal(diff, jarPath1, jarPath2, false);
    }

    public static String formatColor(DiffResult diff, Path jarPath1, Path jarPath2) {
        return formatInternal(diff, jarPath1, jarPath2, true);
    }

    private static String formatInternal(DiffResult diff, Path jarPath1, Path jarPath2, boolean color) {
        StringBuilder sb = new StringBuilder();

        String B = color ? ANSI_BOLD : "";
        String R = color ? ANSI_RESET : "";
        String G = color ? ANSI_GREEN : "";
        String RE = color ? ANSI_RED : "";
        String Y = color ? ANSI_YELLOW : "";
        String C = color ? ANSI_CYAN : "";

        sb.append(B).append("Jar Version Inspector — DIFF Report").append(R).append("\n");
        sb.append("=".repeat(80)).append("\n");
        sb.append("Old: ").append(jarPath1.toAbsolutePath().normalize()).append("\n");
        sb.append("New: ").append(jarPath2.toAbsolutePath().normalize()).append("\n");
        sb.append("Scanned: ")
            .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
            .append("\n\n");

        // Sections
        printSection(sb, diff.getByType(ChangeType.UPGRADED), "Upgraded", "⬆", G, B, R, Y);
        printSection(sb, diff.getByType(ChangeType.DOWNGRADED), "Downgraded", "⬇", RE, B, R, Y);
        printSection(sb, diff.getByType(ChangeType.ADDED), "Added", "🆕", G, B, R, Y);
        printSection(sb, diff.getByType(ChangeType.REMOVED), "Removed", "❌", RE, B, R, Y);

        // Unchanged
        List<DiffEntry> unchanged = diff.getByType(ChangeType.UNCHANGED);
        if (!unchanged.isEmpty()) {
            sb.append("\n").append(B).append("Unchanged (").append(unchanged.size()).append("):").append(R).append("\n");
            sb.append("─".repeat(80)).append("\n");
            for (DiffEntry entry : unchanged) {
                sb.append(String.format("  %-50s %s\n",
                    entry.getDisplayName(),
                    entry.getNewVersion() != null ? entry.getNewVersion() : "—"));
            }
        }

        // Summary
        sb.append("\n").append(B).append("Summary:").append(R).append("\n");
        sb.append("─".repeat(80)).append("\n");
        sb.append(String.format("  %-20s Old: %d libraries", "Libraries:", diff.getLibraryCountOld())).append("\n");
        sb.append(String.format("  %-20s New: %d libraries", "", diff.getLibraryCountNew())).append("\n");
        sb.append(String.format("  %-20s %d", "Upgraded:", diff.getByType(ChangeType.UPGRADED).size())).append("\n");
        sb.append(String.format("  %-20s %d", "Downgraded:", diff.getByType(ChangeType.DOWNGRADED).size())).append("\n");
        sb.append(String.format("  %-20s %d", "Added:", diff.getByType(ChangeType.ADDED).size())).append("\n");
        sb.append(String.format("  %-20s %d", "Removed:", diff.getByType(ChangeType.REMOVED).size())).append("\n");
        sb.append(String.format("  %-20s %d", "Unchanged:", diff.getByType(ChangeType.UNCHANGED).size())).append("\n");

        return sb.toString();
    }

    private static void printSection(StringBuilder sb, List<DiffEntry> entries,
                                      String title, String icon,
                                      String colorStart, String bold, String reset, String yellow) {
        if (entries.isEmpty()) return;

        sb.append("\n").append(bold).append(title).append(" (").append(entries.size()).append("):")
            .append(reset).append("\n");
        sb.append("─".repeat(80)).append("\n");
        for (DiffEntry entry : entries) {
            String oldV = entry.getOldVersion() != null ? entry.getOldVersion() : "—";
            String newV = entry.getNewVersion() != null ? entry.getNewVersion() : "—";
            sb.append(String.format("  %-50s %s → %s  %s %s%s\n",
                entry.getDisplayName(), oldV, newV, icon, colorStart, reset));
        }
    }
}
