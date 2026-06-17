package com.jarversion;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import com.jarversion.output.TextFormatter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * JarVersionInspector — CLI entry point.
 *
 * Scans a JAR/WAR file and displays all embedded libraries with versions.
 */
@Command(
    name = "jar-version-inspector",
    mixinStandardHelpOptions = true,
    version = "1.0.0",
    description = "Scan a JAR file and list all embedded library versions."
)
public class JarVersionInspector implements Callable<Integer> {

    @Parameters(index = "0", description = "Path to the JAR/WAR file to scan")
    private Path jarPath;

    @Option(names = {"-v", "--verbose"}, description = "Show detailed scan progress")
    private boolean verbose;

    @Option(names = {"--no-dedupe"}, description = "Disable duplicate merge (default: enabled)")
    private boolean noDedupe;

    @Option(names = {"--min-version"}, description = "Only show libraries with version >= specified")
    private String minVersion;

    @Option(names = {"--filter"}, description = "Only show library matching groupId:artifactId pattern")
    private String filter;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new JarVersionInspector()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        try {
            // 1. Validate input
            validateInput();

            // 2. Run scan
            ScannerEngine engine = new ScannerEngine(verbose);
            List<LibraryEntry> entries = engine.scan(jarPath);

            // 3. Deduplicate if enabled
            if (!noDedupe) {
                entries = engine.deduplicate(entries);
            }

            // 4. Apply filters
            if (minVersion != null) {
                entries = engine.filterByMinVersion(entries, minVersion);
            }
            if (filter != null) {
                entries = engine.filterByGA(entries, filter);
            }

            // 5. Sort
            entries = engine.sort(entries);

            // 6. Output
            long jarSize = Files.size(jarPath);
            String report = TextFormatter.format(entries, jarPath, jarSize);
            System.out.println(report);

            return entries.isEmpty() ? 1 : 0;

        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            return 2;
        } catch (IOException e) {
            System.err.println("IO Error: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return 2;
        }
    }

    private void validateInput() {
        if (jarPath == null) {
            throw new IllegalArgumentException("JAR path is required.");
        }
        if (!Files.exists(jarPath)) {
            throw new IllegalArgumentException("File not found: " + jarPath);
        }
        if (!Files.isRegularFile(jarPath)) {
            throw new IllegalArgumentException("Not a regular file: " + jarPath);
        }
        String name = jarPath.getFileName().toString().toLowerCase();
        if (!name.endsWith(".jar") && !name.endsWith(".war") && !name.endsWith(".zip")) {
            throw new IllegalArgumentException("Unsupported file type. Expected .jar, .war, or .zip: " + jarPath);
        }
    }
}
