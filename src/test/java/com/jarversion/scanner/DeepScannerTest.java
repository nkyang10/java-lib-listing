package com.jarversion.scanner;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeepScannerTest {

    // ── getPackagePrefix (3-segment grouping) ──

    @Test
    void getPackagePrefix_standardPackage() {
        String result = DeepScanner.getPackagePrefix("com.fasterxml.jackson.databind.ObjectMapper");
        assertThat(result).isEqualTo("com.fasterxml.jackson");
    }

    @Test
    void getPackagePrefix_shallowPackage() {
        String result = DeepScanner.getPackagePrefix("com.example.Foo");
        assertThat(result).isEqualTo("com.example");
    }

    @Test
    void getPackagePrefix_rootPackage() {
        String result = DeepScanner.getPackagePrefix("Foo");
        assertThat(result).isNull();
    }

    @Test
    void getPackagePrefix_deeplyNested() {
        String result = DeepScanner.getPackagePrefix("org.springframework.boot.autoconfigure.SpringBootApplication");
        assertThat(result).isEqualTo("org.springframework.boot");
    }

    @Test
    void getPackagePrefix_twoSegment() {
        String result = DeepScanner.getPackagePrefix("okhttp3.Request");
        assertThat(result).isEqualTo("okhttp3");
    }

    @Test
    void getPackagePrefix_netty() {
        String result = DeepScanner.getPackagePrefix("io.netty.buffer.ByteBuf");
        assertThat(result).isEqualTo("io.netty.buffer");
    }
}
