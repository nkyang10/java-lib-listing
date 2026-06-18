package com.jarversion.scanner;

import com.jarversion.LibraryEntry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.jar.Attributes;

/**
 * Scans META-INF/MANIFEST.MF for version information.
 *
 * Extracts:
 * - Implementation-Title / Implementation-Version
 * - Bundle-Name / Bundle-Version (OSGi)
 * - Specification-Title / Specification-Version
 * - Class-Path entries
 */
public class ManifestScanner {

    public List<LibraryEntry> scan(Path jarPath) throws IOException {
        List<LibraryEntry> entries = new ArrayList<>();

        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Manifest manifest = jar.getManifest();
            if (manifest == null) {
                return entries;
            }

            Attributes attrs = manifest.getMainAttributes();

            // Implementation-Version
            String implTitle = attrs.getValue(Attributes.Name.IMPLEMENTATION_TITLE);
            String implVersion = attrs.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
            if (implTitle != null && implVersion != null) {
                entries.add(new LibraryEntry(
                    null, implTitle, implVersion,
                    LibraryEntry.Source.MANIFEST_IMPLEMENTATION, 0
                ));
            }

            // Bundle-Version (OSGi)
            String bundleName = attrs.getValue("Bundle-Name");
            String bundleVersion = attrs.getValue("Bundle-Version");
            if (bundleName != null && bundleVersion != null && implTitle == null) {
                entries.add(new LibraryEntry(
                    null, bundleName, bundleVersion,
                    LibraryEntry.Source.MANIFEST_BUNDLE, 0
                ));
            }

            // Specification-Version
            String specTitle = attrs.getValue(Attributes.Name.SPECIFICATION_TITLE);
            String specVersion = attrs.getValue(Attributes.Name.SPECIFICATION_VERSION);
            if (specTitle != null && specVersion != null) {
                entries.add(new LibraryEntry(
                    null, specTitle, specVersion,
                    LibraryEntry.Source.MANIFEST_SPECIFICATION, 0
                ));
            }

            // Class-Path entries
            String classPath = attrs.getValue(Attributes.Name.CLASS_PATH);
            if (classPath != null) {
                String[] jars = classPath.split("\\s+");
                for (String cp : jars) {
                    if (!cp.isEmpty()) {
                        entries.add(new LibraryEntry(
                            null, cp, null,
                            LibraryEntry.Source.MANIFEST_CLASS_PATH, 0
                        ));
                    }
                }
            }
        }

        return entries;
    }
}
