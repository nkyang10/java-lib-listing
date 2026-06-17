package com.jarversion.scanner;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeepScannerTest {

    // ── getTopPackage (3-segment grouping) ──

    @Test
    void getTopPackage_standardPackage() {
        String result = DeepScanner.getTopPackage("com.fasterxml.jackson.databind.ObjectMapper");
        assertThat(result).isEqualTo("com.fasterxml.jackson");
    }

    @Test
    void getTopPackage_shallowPackage() {
        String result = DeepScanner.getTopPackage("com.example.Foo");
        assertThat(result).isEqualTo("com.example");
    }

    @Test
    void getTopPackage_rootPackage() {
        String result = DeepScanner.getTopPackage("Foo");
        assertThat(result).isNull();
    }

    @Test
    void getTopPackage_deeplyNested() {
        String result = DeepScanner.getTopPackage("org.springframework.boot.autoconfigure.SpringBootApplication");
        assertThat(result).isEqualTo("org.springframework.boot");
    }

    @Test
    void getTopPackage_twoSegment() {
        String result = DeepScanner.getTopPackage("okhttp3.Request");
        assertThat(result).isEqualTo("okhttp3");
    }

    // ── getTwoSegmentPrefix (2-segment grouping) ──

    @Test
    void getTwoSegmentPrefix_standard() {
        String result = DeepScanner.getTwoSegmentPrefix("org.bouncycastle.asn1");
        assertThat(result).isEqualTo("org.bouncycastle");
    }

    @Test
    void getTwoSegmentPrefix_deep() {
        String result = DeepScanner.getTwoSegmentPrefix("com.fasterxml.jackson.core");
        assertThat(result).isEqualTo("com.fasterxml");
    }

    @Test
    void getTwoSegmentPrefix_shallow() {
        String result = DeepScanner.getTwoSegmentPrefix("okhttp3");
        assertThat(result).isEqualTo("okhttp3");
    }

    @Test
    void getTwoSegmentPrefix_singleSegment() {
        String result = DeepScanner.getTwoSegmentPrefix("java");
        assertThat(result).isEqualTo("java");
    }

    @Test
    void getTwoSegmentPrefix_netty() {
        String result = DeepScanner.getTwoSegmentPrefix("io.netty.buffer");
        assertThat(result).isEqualTo("io.netty");
    }
}
