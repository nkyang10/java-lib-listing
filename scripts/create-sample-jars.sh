#!/bin/bash
# Create sample JAR files for integration testing
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SAMPLES_DIR="$SCRIPT_DIR/sample-jars"
mkdir -p "$SAMPLES_DIR"

# ====================================================
# Sample 1: Simple JAR with 3 Maven libraries + MANIFEST
# ====================================================
echo "Creating sample1: simple-maven.jar..."
TMPDIR=$(mktemp -d)

# Library 1: logback-classic
mkdir -p "$TMPDIR/META-INF/maven/ch.qos.logback/logback-classic"
cat > "$TMPDIR/META-INF/maven/ch.qos.logback/logback-classic/pom.properties" << 'EOF'
version=1.4.14
groupId=ch.qos.logback
artifactId=logback-classic
EOF

# Library 2: jackson-databind
mkdir -p "$TMPDIR/META-INF/maven/com.fasterxml.jackson.core/jackson-databind"
cat > "$TMPDIR/META-INF/maven/com.fasterxml.jackson.core/jackson-databind/pom.properties" << 'EOF'
version=2.15.3
groupId=com.fasterxml.jackson.core
artifactId=jackson-databind
EOF

# Library 3: guava
mkdir -p "$TMPDIR/META-INF/maven/com.google.guava/guava"
cat > "$TMPDIR/META-INF/maven/com.google.guava/guava/pom.properties" << 'EOF'
version=32.1.3-jre
groupId=com.google.guava
artifactId=guava
EOF

# Use a manifest file (NOT inside META-INF — jar cfm reads it separately)
cat > "$TMPDIR/manifest.mf" << 'EOF'
Manifest-Version: 1.0
Implementation-Title: my-app
Implementation-Version: 2.0.0

EOF

cd "$TMPDIR" && jar cfm "$SAMPLES_DIR/sample1-simple-maven.jar" manifest.mf META-INF
rm -rf "$TMPDIR"

# ====================================================
# Sample 2: Fat JAR (outer + 2 embedded JARs)
# ====================================================
echo "Creating sample2: fat-jar.jar..."
TMPDIR=$(mktemp -d)

# Create inner JAR 1: commons-lang3
INNER1="$TMPDIR/inner1.jar"
ITMP1=$(mktemp -d)
mkdir -p "$ITMP1/META-INF/maven/org.apache.commons/commons-lang3"
cat > "$ITMP1/META-INF/maven/org.apache.commons/commons-lang3/pom.properties" << 'EOF'
version=3.13.0
groupId=org.apache.commons
artifactId=commons-lang3
EOF
cat > "$ITMP1/manifest.mf" << 'EOF'
Manifest-Version: 1.0

EOF
cd "$ITMP1" && jar cfm "$INNER1" manifest.mf META-INF
rm -rf "$ITMP1"

# Create inner JAR 2: slf4j-api
INNER2="$TMPDIR/inner2.jar"
ITMP2=$(mktemp -d)
mkdir -p "$ITMP2/META-INF/maven/org.slf4j/slf4j-api"
cat > "$ITMP2/META-INF/maven/org.slf4j/slf4j-api/pom.properties" << 'EOF'
version=2.0.9
groupId=org.slf4j
artifactId=slf4j-api
EOF
cat > "$ITMP2/manifest.mf" << 'EOF'
Manifest-Version: 1.0

EOF
cd "$ITMP2" && jar cfm "$INNER2" manifest.mf META-INF
rm -rf "$ITMP2"

# Create outer directory structure
mkdir -p "$TMPDIR/outer"
mkdir -p "$TMPDIR/outer/META-INF/maven/com.example/outer-app"
cat > "$TMPDIR/outer/META-INF/maven/com.example/outer-app/pom.properties" << 'EOF'
version=1.0.0
groupId=com.example
artifactId=outer-app
EOF

# Embed the inner JARs in a lib subdirectory
mkdir -p "$TMPDIR/outer/lib"
cp "$INNER1" "$TMPDIR/outer/lib/commons-lang3.jar"
cp "$INNER2" "$TMPDIR/outer/lib/slf4j-api.jar"

# Manifest for outer JAR
cat > "$TMPDIR/manifest.mf" << 'EOF'
Manifest-Version: 1.0

EOF

cd "$TMPDIR/outer" && jar cfm "$SAMPLES_DIR/sample2-fat-jar.jar" "$TMPDIR/manifest.mf" META-INF lib
rm -rf "$TMPDIR"

# ====================================================
# Sample 3: OSGi bundle JAR (Bundle-Version only)
# ====================================================
echo "Creating sample3: osgi-bundle.jar..."
TMPDIR=$(mktemp -d)

mkdir -p "$TMPDIR/META-INF/maven/org.eclipse.osgi/org.eclipse.osgi"
cat > "$TMPDIR/META-INF/maven/org.eclipse.osgi/org.eclipse.osgi/pom.properties" << 'EOF'
version=3.18.0
groupId=org.eclipse.osgi
artifactId=org.eclipse.osgi
EOF

cat > "$TMPDIR/manifest.mf" << 'EOF'
Manifest-Version: 1.0
Bundle-Name: org.eclipse.osgi
Bundle-Version: 3.18.0
Bundle-Vendor: Eclipse.org

EOF

cd "$TMPDIR" && jar cfm "$SAMPLES_DIR/sample3-osgi-bundle.jar" manifest.mf META-INF
rm -rf "$TMPDIR"

# ====================================================
# Sample 4: Empty JAR (no library data)
# ====================================================
echo "Creating sample4: empty-jar.jar..."
TMPDIR=$(mktemp -d)

cat > "$TMPDIR/manifest.mf" << 'EOF'
Manifest-Version: 1.0

EOF
echo "Just a plain file" > "$TMPDIR/hello.txt"
cd "$TMPDIR" && jar cfm "$SAMPLES_DIR/sample4-empty-jar.jar" manifest.mf hello.txt
rm -rf "$TMPDIR"

# ====================================================
# Sample 5: Complex multi-level fat JAR
# Architecture:
#   complex-app.jar (Level 0)
#   ├── META-INF/maven/com.mycompany/my-app/pom.properties (my-app 2.0.0)
#   ├── META-INF/MANIFEST.MF (ComplexApp 3.0.0)
#   └── lib/
#       ├── spring-boot-starter-web-3.1.5.jar (Level 1 — Maven Central)
#       ├── jackson-databind-2.15.3.jar (Level 1 — Maven Central)
#       ├── my-gradle-lib-1.0.jar (Level 1 — Gradle-built, with nested deps)
#       │   ├── META-INF/gradle-plugins/
#       │   └── lib/okhttp-4.12.0.jar (Level 2 — Maven Central)
#       │   └── lib/retrofit-2.9.0.jar (Level 2 — Maven Central)
#       └── custom-internal-1.5.0.jar (Level 1 — internal build, only MANIFEST)
# ====================================================
echo "Creating sample5: complex-app.jar (multi-level fat JAR)..."
TMPDIR=$(mktemp -d)

# --- Build Level 2: okhttp (mimics Maven Central JAR) ---
OKHTTP="$TMPDIR/okhttp-4.12.0.jar"
mkdir -p "$TMPDIR/okhttp-build/META-INF/maven/com.squareup.okhttp3/okhttp"
cat > "$TMPDIR/okhttp-build/META-INF/maven/com.squareup.okhttp3/okhttp/pom.properties" << 'EOF'
version=4.12.0
groupId=com.squareup.okhttp3
artifactId=okhttp
EOF
# Also add the pom.xml (many Maven JARs have both)
cat > "$TMPDIR/okhttp-build/META-INF/maven/com.squareup.okhttp3/okhttp/pom.xml" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project>
  <groupId>com.squareup.okhttp3</groupId>
  <artifactId>okhttp</artifactId>
  <version>4.12.0</version>
</project>
EOF
cat > "$TMPDIR/okhttp-build/manifest.mf" << 'EOF'
Manifest-Version: 1.0

EOF
cd "$TMPDIR/okhttp-build" && jar cfm "$OKHTTP" manifest.mf META-INF
rm -rf "$TMPDIR/okhttp-build"

# --- Build Level 2: retrofit (mimics Maven Central JAR) ---
RETROFIT="$TMPDIR/retrofit-2.9.0.jar"
mkdir -p "$TMPDIR/retrofit-build/META-INF/maven/com.squareup.retrofit2/retrofit"
cat > "$TMPDIR/retrofit-build/META-INF/maven/com.squareup.retrofit2/retrofit/pom.properties" << 'EOF'
version=2.9.0
groupId=com.squareup.retrofit2
artifactId=retrofit
EOF
cat > "$TMPDIR/retrofit-build/manifest.mf" << 'EOF'
Manifest-Version: 1.0

EOF
cd "$TMPDIR/retrofit-build" && jar cfm "$RETROFIT" manifest.mf META-INF
rm -rf "$TMPDIR/retrofit-build"

# --- Build Level 1: my-gradle-lib (Gradle-built, embeds okhttp + retrofit) ---
GRADLE_LIB="$TMPDIR/my-gradle-lib-1.0.jar"
mkdir -p "$TMPDIR/gradle-build/META-INF/maven/com.mycompany/my-gradle-lib"
cat > "$TMPDIR/gradle-build/META-INF/maven/com.mycompany/my-gradle-lib/pom.properties" << 'EOF'
version=1.0.0
groupId=com.mycompany
artifactId=my-gradle-lib
EOF

# Gradle plugin marker (identifies this as a Gradle-built JAR)
mkdir -p "$TMPDIR/gradle-build/META-INF/gradle-plugins"
cat > "$TMPDIR/gradle-build/META-INF/gradle-plugins/my-plugin.properties" << 'EOF'
implementation-class=com.mycompany.gradle.MyPlugin
EOF

# Embed Level 2 JARs
mkdir -p "$TMPDIR/gradle-build/lib"
cp "$OKHTTP" "$TMPDIR/gradle-build/lib/okhttp-4.12.0.jar"
cp "$RETROFIT" "$TMPDIR/gradle-build/lib/retrofit-2.9.0.jar"

cat > "$TMPDIR/gradle-build/manifest.mf" << 'EOF'
Manifest-Version: 1.0
Implementation-Title: my-gradle-lib
Implementation-Version: 1.0.0
Created-By: Gradle 8.5

EOF

cd "$TMPDIR/gradle-build" && jar cfm "$GRADLE_LIB" manifest.mf META-INF lib
rm -rf "$TMPDIR/gradle-build"

# --- Build Level 1: spring-boot-starter-web (mimics Maven Central JAR) ---
SPRING="$TMPDIR/spring-boot-starter-web-3.1.5.jar"
mkdir -p "$TMPDIR/spring-build/META-INF/maven/org.springframework.boot/spring-boot-starter-web"
cat > "$TMPDIR/spring-build/META-INF/maven/org.springframework.boot/spring-boot-starter-web/pom.properties" << 'EOF'
version=3.1.5
groupId=org.springframework.boot
artifactId=spring-boot-starter-web
EOF
cat > "$TMPDIR/spring-build/manifest.mf" << 'EOF'
Manifest-Version: 1.0

EOF
cd "$TMPDIR/spring-build" && jar cfm "$SPRING" manifest.mf META-INF
rm -rf "$TMPDIR/spring-build"

# --- Build Level 1: jackson-databind (mimics Maven Central JAR) ---
JACKSON="$TMPDIR/jackson-databind-2.15.3.jar"
mkdir -p "$TMPDIR/jackson-build/META-INF/maven/com.fasterxml.jackson.core/jackson-databind"
cat > "$TMPDIR/jackson-build/META-INF/maven/com.fasterxml.jackson.core/jackson-databind/pom.properties" << 'EOF'
version=2.15.3
groupId=com.fasterxml.jackson.core
artifactId=jackson-databind
EOF
cat > "$TMPDIR/jackson-build/manifest.mf" << 'EOF'
Manifest-Version: 1.0

EOF
cd "$TMPDIR/jackson-build" && jar cfm "$JACKSON" manifest.mf META-INF
rm -rf "$TMPDIR/jackson-build"

# --- Build Level 1: custom-internal (internal build, only MANIFEST, no pom.properties) ---
CUSTOM="$TMPDIR/custom-internal-1.5.0.jar"
mkdir -p "$TMPDIR/custom-build/META-INF"
cat > "$TMPDIR/custom-build/manifest.mf" << 'EOF'
Manifest-Version: 1.0
Implementation-Title: custom-internal-lib
Implementation-Version: 1.5.0

EOF
mkdir -p "$TMPDIR/custom-build/com/mycompany/internal"
echo "placeholder" > "$TMPDIR/custom-build/com/mycompany/internal/SomeClass.class"
cd "$TMPDIR/custom-build" && jar cfm "$CUSTOM" manifest.mf META-INF com
rm -rf "$TMPDIR/custom-build"

# --- Build Level 0: complex-app (outer fat JAR, embeds ALL Level 1 JARs) ---
mkdir -p "$TMPDIR/outer-build/META-INF/maven/com.mycompany/my-app"
cat > "$TMPDIR/outer-build/META-INF/maven/com.mycompany/my-app/pom.properties" << 'EOF'
version=2.0.0
groupId=com.mycompany
artifactId=my-app
EOF

# Embed all Level 1 JARs
mkdir -p "$TMPDIR/outer-build/lib"
cp "$SPRING" "$TMPDIR/outer-build/lib/spring-boot-starter-web-3.1.5.jar"
cp "$JACKSON" "$TMPDIR/outer-build/lib/jackson-databind-2.15.3.jar"
cp "$GRADLE_LIB" "$TMPDIR/outer-build/lib/my-gradle-lib-1.0.jar"
cp "$CUSTOM" "$TMPDIR/outer-build/lib/custom-internal-1.5.0.jar"

# Add a META-INF/DEPENDENCIES file (Apache-style project)
cat > "$TMPDIR/outer-build/META-INF/DEPENDENCIES" << 'EOF'
Apache Commons Codec
───────────────────
- org.apache.commons:commons-codec:1.16.1

SLF4J
─────
- org.slf4j:slf4j-api:2.0.9

HikariCP
────────
- com.zaxxer:HikariCP:5.0.1
EOF

cat > "$TMPDIR/outer-build/manifest.mf" << 'EOF'
Manifest-Version: 1.0
Implementation-Title: ComplexApp
Implementation-Version: 3.0.0

EOF

cd "$TMPDIR/outer-build" && jar cfm "$SAMPLES_DIR/sample5-complex-app.jar" manifest.mf META-INF lib
rm -rf "$TMPDIR"

echo ""

# ====================================================
# Final: list all sample JARs with sizes
# ====================================================
echo ""
echo "=== Sample JARs created ==="
ls -lh "$SAMPLES_DIR"/*.jar
echo ""
echo "Done!"
