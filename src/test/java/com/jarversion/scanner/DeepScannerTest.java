package com.jarversion.scanner;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeepScannerTest {

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
}
