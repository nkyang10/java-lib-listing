package com.jarversion;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import com.jarversion.output.DiffFormatter;
import com.jarversion.output.HtmlFormatter;
import com.jarversion.output.TextFormatter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * JarVersionInspector — CLI entry point.
 *
 * Single mode: scan a JAR/WAR file and display all embedded library versions.
 * DIFF mode: compare two JAR files and show version differences.
 */
@Command(
    name = "jar-version-inspector",
    mixinStandardHelpOptions = true,
    version = "1.3.0",
    description = "Scan JAR files and list all embedded library versions."
)
public class JarVersionInspector implements Callable<Integer> {

    @Parameters(index = "0", description = "Path to JAR/WAR file (or first JAR for --diff)")
    private Path jarPath1;

    @Parameters(index = "1", arity = "0..1", description = "Second JAR for comparison (optional, enables DIFF mode)")
    private Path jarPath2;

    @Option(names = {"-v", "--verbose"}, description = "Show detailed scan progress")
    private boolean verbose;

    @Option(names = {"--no-dedupe"}, description = "Disable duplicate merge (default: enabled)")
    private boolean noDedupe;

    @Option(names = {"--deep"}, description = "Enable deep class fingerprinting via Maven Central (for shaded/uber JARs)")
    private boolean deep;

    @Option(names = {"--min-version"}, description = "Only show libraries with version >= specified")
    private String minVersion;

    @Option(names = {"--filter"}, description = "Only show library matching groupId:artifactId pattern")
    private String filter;

    @Option(names = {"--json"}, description = "Output in JSON format (for CI/CD)")
    private boolean json;

    @Option(names = {"--html"}, description = "Output in HTML format")
    private boolean html;

    @Option(names = {"--color"}, description = "Enable colored terminal output")
    private boolean color;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new JarVersionInspector()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        try {
            if (jarPath2 != null) {
                return runDiff();
            } else {
                return runScan(jarPath1);
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        } catch (IOException e) {
            System.err.println("IO Error: " + e.getMessage());
            if (verbose) e.printStackTrace();
            return 2;
        }
    }

    private Integer runScan(Path path) throws IOException {
        validatePath(path);

        ScannerEngine engine = new ScannerEngine(verbose, deep);
        List<LibraryEntry> entries = engine.scan(path);

        if (!noDedupe) {
            entries = engine.deduplicate(entries);
        }

        if (minVersion != null) {
            entries = engine.filterByMinVersion(entries, minVersion);
        }
        if (filter != null) {
            entries = engine.filterByGA(entries, filter);
        }
        entries = engine.sort(entries);

        long jarSize = Files.size(path);
        int dedupCount = noDedupe ? 0 : engine.getLastDedupCount();

        String report;
        if (json) {
            report = com.jarversion.output.JsonFormatter.format(entries, path, jarSize, dedupCount);
        } else if (html) {
            report = HtmlFormatter.format("Scan Report", entries, path, jarSize, dedupCount);
        } else if (color) {
            report = TextFormatter.formatColor(entries, path, jarSize, dedupCount);
        } else {
            report = TextFormatter.format(entries, path, jarSize, dedupCount);
        }
        System.out.println(report);

        return entries.isEmpty() ? 1 : 0;
    }

    private Integer runDiff() throws IOException {
        validatePath(jarPath1);
        validatePath(jarPath2);

        if (verbose) {
            System.err.println("[JVI] DIFF mode: comparing " + jarPath1 + " vs " + jarPath2);
        }

        ScannerEngine engine = new ScannerEngine(verbose, deep);

        List<LibraryEntry> oldEntries = engine.scan(jarPath1);
        oldEntries = engine.deduplicate(oldEntries);
        oldEntries = engine.sort(oldEntries);

        List<LibraryEntry> newEntries = engine.scan(jarPath2);
        newEntries = engine.deduplicate(newEntries);
        newEntries = engine.sort(newEntries);

        DiffResult diff = DiffResult.compute(oldEntries, newEntries);

        String report;
        if (json) {
            report = com.jarversion.output.JsonFormatter.formatDiff(diff, jarPath1, jarPath2);
        } else if (html) {
            report = HtmlFormatter.formatDiff(diff, jarPath1, jarPath2);
        } else if (color) {
            report = DiffFormatter.formatColor(diff, jarPath1, jarPath2);
        } else {
            report = DiffFormatter.format(diff, jarPath1, jarPath2);
        }
        System.out.println(report);

        return diff.hasChanges() ? 0 : 1;
    }

    private void validatePath(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("JAR path is required.");
        }
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("File not found: " + path);
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Not a regular file: " + path);
        }
        String name = path.getFileName().toString().toLowerCase();
        if (!name.endsWith(".jar") && !name.endsWith(".war") && !name.endsWith(".zip")) {
            throw new IllegalArgumentException("Unsupported file type. Expected .jar, .war, or .zip: " + path);
        }
    }
}
