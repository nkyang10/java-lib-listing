package com.jarversion.scanner;

import com.jarversion.LibraryEntry;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Scans META-INF/maven/{groupId}/{artifactId}/pom.xml for library version info.
 *
 * Extracts groupId, artifactId, version from the full POM file.
 * Many Maven JARs include a pom.xml alongside pom.properties.
 * This scanner provides an additional source for cases where
 * pom.properties is missing or incomplete.
 */
public class PomXmlScanner {

    private static final String POM_XML_PREFIX = "META-INF/maven/";

    public List<LibraryEntry> scan(Path jarPath) throws IOException {
        List<LibraryEntry> entries = new ArrayList<>();

        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            Enumeration<? extends ZipEntry> zipEntries = zip.entries();

            while (zipEntries.hasMoreElements()) {
                ZipEntry entry = zipEntries.nextElement();
                String name = entry.getName();

                // Match META-INF/maven/**/pom.xml
                if (name.endsWith("/pom.xml") && name.startsWith(POM_XML_PREFIX)) {
                    LibraryEntry lib = parsePomXml(zip, entry, name);
                    if (lib != null) {
                        entries.add(lib);
                    }
                }
            }
        }

        return entries;
    }

    /**
     * Parse a pom.xml file and extract groupId, artifactId, version.
     * Uses basic XML parsing — handles simple POMs without
     * parent inheritance or property substitution.
     */
    private LibraryEntry parsePomXml(ZipFile zip, ZipEntry entry, String name) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Disable external entities for security
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder builder = factory.newDocumentBuilder();

            try (InputStream is = zip.getInputStream(entry)) {
                org.w3c.dom.Document doc = builder.parse(is);
                doc.getDocumentElement().normalize();

                String groupId = getTextContent(doc, "groupId");
                String artifactId = getTextContent(doc, "artifactId");
                String version = getTextContent(doc, "version");

                // Fallback: extract from file path if XML parsing fails
                if ((groupId == null || artifactId == null) && name.startsWith(POM_XML_PREFIX)) {
                    String path = name.substring(POM_XML_PREFIX.length());
                    // path = "com.example/my-lib/pom.xml"
                    int lastSlash = path.lastIndexOf('/');
                    if (lastSlash > 0) {
                        String artifactPart = path.substring(0, lastSlash); // "com.example/my-lib"
                        int groupSlash = artifactPart.lastIndexOf('/');
                        if (groupSlash > 0) {
                            if (groupId == null) {
                                groupId = artifactPart.substring(0, groupSlash).replace('/', '.');
                            }
                            if (artifactId == null) {
                                artifactId = artifactPart.substring(groupSlash + 1);
                            }
                        }
                    }
                }

                if (groupId != null && artifactId != null) {
                    return new LibraryEntry(groupId, artifactId, version,
                        LibraryEntry.Source.POM_XML, 0);
                }
            }
        } catch (Exception e) {
            // XML parsing failed — skip this entry silently
        }
        return null;
    }

    /**
     * Get text content of a direct child element.
     */
    private String getTextContent(org.w3c.dom.Document doc, String tagName) {
        org.w3c.dom.NodeList list = doc.getDocumentElement().getElementsByTagName(tagName);
        if (list.getLength() > 0) {
            String text = list.item(0).getTextContent();
            if (text != null) {
                text = text.trim();
                if (!text.isEmpty() && !text.startsWith("${")) {
                    return text;
                }
            }
        }
        return null;
    }
}
