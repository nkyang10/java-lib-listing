package com.jarversion.output;

import com.jarversion.LibraryEntry;
import com.jarversion.LibraryEntry.Source;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TreeFormatter — dependency tree output.
 */
class TreeFormatterTest {

    @Test
    void testEmptyEntries() {
        String result = TreeFormatter.format(List.of(), Paths.get("/tmp/test.jar"), 1000, 0);
        assertTrue(result.contains("Libraries (0 found)"));
        assertTrue(result.contains("(no library version data found)"));
    }

    @Test
    void testFlatEntriesShowsAll() {
        List<LibraryEntry> entries = new ArrayList<>();
        entries.add(new LibraryEntry("ch.qos.logback", "logback-classic", "1.4.14",
            Source.POM_PROPERTIES, 0));
        entries.add(new LibraryEntry("com.google.guava", "guava", "32.1.3-jre",
            Source.POM_PROPERTIES, 0));

        String result = TreeFormatter.format(entries, Paths.get("/tmp/test.jar"), 10000, 0);
        assertTrue(result.contains("logback-classic"));
        assertTrue(result.contains("1.4.14"));
        assertTrue(result.contains("guava"));
        assertTrue(result.contains("32.1.3-jre"));
        // Should show ├── for first, └── for last
        assertTrue(result.contains("├──"));
        assertTrue(result.contains("└──"));
    }

    @Test
    void testSingleEntry() {
        List<LibraryEntry> entries = new ArrayList<>();
        entries.add(new LibraryEntry("com.example", "my-lib", "2.0.0",
            Source.POM_PROPERTIES, 0));

        String result = TreeFormatter.format(entries, Paths.get("/tmp/test.jar"), 10000, 0);
        assertTrue(result.contains("my-lib"));
        assertTrue(result.contains("2.0.0"));
        // Only one entry → use └── (last child of root)
        assertTrue(result.contains("└──"));
    }

    @Test
    void testTreeStructureWithParent() {
        List<LibraryEntry> entries = new ArrayList<>();
        // Root-level entry
        entries.add(new LibraryEntry("org.springframework.boot", "spring-boot", "3.1.5",
            Source.POM_PROPERTIES, 0));
        // Embedded entry with parent reference
        entries.add(new LibraryEntry("com.squareup.okhttp3", "okhttp", "4.12.0",
            Source.EMBEDDED_JAR, 1, "ROOT/spring-boot-3.1.5.jar"));

        String result = TreeFormatter.format(entries, Paths.get("/tmp/test.jar"), 100000, 0);
        // Should show spring-boot at top level
        assertTrue(result.contains("spring-boot"));
        // Should show the embedded JAR filename as a container
        assertTrue(result.contains("spring-boot-3.1.5.jar"));
        // Should show okhttp as nested child
        assertTrue(result.contains("okhttp"));
        assertTrue(result.contains("4.12.0"));
    }

    @Test
    void testTreeWithMultipleDepths() {
        List<LibraryEntry> entries = new ArrayList<>();
        entries.add(new LibraryEntry("com.example", "app", "1.0.0",
            Source.POM_PROPERTIES, 0));
        // Level 1
        entries.add(new LibraryEntry("org.springframework.boot", "spring-boot", "3.1.5",
            Source.EMBEDDED_JAR, 1, "ROOT/spring-boot-3.1.5.jar"));
        // Level 2
        entries.add(new LibraryEntry("com.squareup.okhttp3", "okhttp", "4.12.0",
            Source.EMBEDDED_JAR, 2, "ROOT/spring-boot-3.1.5.jar/okhttp-4.12.0.jar"));

        String result = TreeFormatter.format(entries, Paths.get("/tmp/test.jar"), 100000, 0);
        assertTrue(result.contains("app"));
        assertTrue(result.contains("spring-boot-3.1.5.jar"));
        assertTrue(result.contains("okhttp"));
        // Should have embedded tag on okhttp
        assertTrue(result.contains("[embedded]"));
    }

    @Test
    void testTreeWithManifestEntries() {
        List<LibraryEntry> entries = new ArrayList<>();
        entries.add(new LibraryEntry(null, "MyApp", "2.0.0",
            Source.MANIFEST_IMPLEMENTATION, 0));
        entries.add(new LibraryEntry(null, "CommonLib", "1.5.0",
            Source.MANIFEST_BUNDLE, 1, "ROOT/CommonLib-1.5.0.jar"));

        String result = TreeFormatter.format(entries, Paths.get("/tmp/test.jar"), 50000, 0);
        assertTrue(result.contains("MyApp"));
        assertTrue(result.contains("2.0.0"));
        assertTrue(result.contains("CommonLib"));
    }

    @Test
    void testEmbeddedEntriesGroupedByParent() {
        // Two different parent JARs, each with their own children
        List<LibraryEntry> entries = new ArrayList<>();
        entries.add(new LibraryEntry("org.springframework.boot", "spring-boot", "3.1.5",
            Source.POM_PROPERTIES, 0));

        // spring-boot's children
        entries.add(new LibraryEntry("com.squareup.okhttp3", "okhttp", "4.12.0",
            Source.EMBEDDED_JAR, 1, "ROOT/spring-boot-3.1.5.jar"));
        entries.add(new LibraryEntry("io.netty", "netty-handler", "4.1.100.Final",
            Source.EMBEDDED_JAR, 1, "ROOT/spring-boot-3.1.5.jar"));

        // guava's children (different parent)
        entries.add(new LibraryEntry("com.google.guava", "guava", "32.1.3-jre",
            Source.EMBEDDED_JAR, 1, "ROOT/guava-32.1.3-jre.jar"));

        String result = TreeFormatter.format(entries, Paths.get("/tmp/test.jar"), 200000, 0);

        // Both container names should appear
        assertTrue(result.contains("spring-boot-3.1.5.jar"));
        assertTrue(result.contains("guava-32.1.3-jre.jar"));

        // Both embedded libraries should appear
        assertTrue(result.contains("okhttp"));
        assertTrue(result.contains("netty-handler"));
        assertTrue(result.contains("guava"));
    }

    @Test
    void testBuildTreeGroupsByDepth() {
        List<LibraryEntry> entries = new ArrayList<>();
        entries.add(new LibraryEntry("com.example", "root-lib", "1.0",
            Source.POM_PROPERTIES, 0));
        entries.add(new LibraryEntry("com.example", "embedded-lib", "2.0",
            Source.EMBEDDED_JAR, 1, "ROOT/child.jar"));

        TreeFormatter.TreeNode root = TreeFormatter.buildTree(entries);
        assertEquals(2, root.children.size()); // root-lib + child.jar container

        // Find container node
        TreeFormatter.TreeNode container = root.children.stream()
            .filter(n -> n.version == null)
            .findFirst().orElse(null);
        assertNotNull(container);
        assertEquals("child.jar", container.label);

        // Embedded lib should be under the container
        assertEquals(1, container.children.size());
        assertEquals("com.example:embedded-lib", container.children.get(0).label);
    }

    @Test
    void testFormatRespectsDedupCount() {
        List<LibraryEntry> entries = new ArrayList<>();
        entries.add(new LibraryEntry("com.example", "lib", "1.0",
            Source.POM_PROPERTIES, 0));

        String result = TreeFormatter.format(entries, Paths.get("/tmp/test.jar"), 1000, 3);
        assertTrue(result.contains("Duplicates merged:"));
        assertTrue(result.contains("3"));
    }

    @Test
    void testFormatSize() {
        List<LibraryEntry> entries = new ArrayList<>();
        entries.add(new LibraryEntry("com.example", "lib", "1.0",
            Source.POM_PROPERTIES, 0));

        String result = TreeFormatter.format(entries, Paths.get("/tmp/test.jar"), 1024 * 1024 * 50, 0);
        assertTrue(result.contains("50.0 MB"));
    }

    @Test
    void testColorOutputDoesNotCrash() {
        List<LibraryEntry> entries = new ArrayList<>();
        entries.add(new LibraryEntry("com.example", "lib", "1.0.0",
            Source.POM_PROPERTIES, 0));

        String result = TreeFormatter.formatColor(entries, Paths.get("/tmp/test.jar"), 1000, 0);
        assertTrue(result.contains("lib"));
        assertTrue(result.contains("\u001B[")); // ANSI escape
    }
}
