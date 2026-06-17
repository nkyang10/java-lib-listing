package com.jarversion.scanner;

import com.jarversion.LibraryEntry;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Scans META-INF/maven/{groupId}/{artifactId}/pom.properties
 * for Maven library version information.
 *
 * Format of pom.properties:
 *   version=1.4.14
 *   groupId=ch.qos.logback
 *   artifactId=logback-classic
 */
public class PomScanner {

    private static final String POM_PROPERTIES_PREFIX = "META-INF/maven/";

    public List<LibraryEntry> scan(Path jarPath) throws IOException {
        List<LibraryEntry> entries = new ArrayList<>();

        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            Enumeration<? extends ZipEntry> zipEntries = zip.entries();

            while (zipEntries.hasMoreElements()) {
                ZipEntry entry = zipEntries.nextElement();
                String name = entry.getName();

                if (name.endsWith("/pom.properties") && name.startsWith(POM_PROPERTIES_PREFIX)) {
                    Properties props = new Properties();
                    try (InputStream is = zip.getInputStream(entry)) {
                        props.load(is);
                    }

                    String groupId = props.getProperty("groupId");
                    String artifactId = props.getProperty("artifactId");
                    String version = props.getProperty("version");

                    if (groupId != null && artifactId != null && version != null) {
                        entries.add(new LibraryEntry(
                            groupId, artifactId, version,
                            LibraryEntry.Source.POM_PROPERTIES, 0
                        ));
                    }
                }
            }
        }

        return entries;
    }
}
