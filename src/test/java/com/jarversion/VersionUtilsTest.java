package com.jarversion;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VersionUtilsTest {

    @Test
    void compareVersions_bothNull() {
        assertThat(VersionUtils.compareVersions(null, null)).isZero();
    }

    @Test
    void compareVersions_leftNull_returnsNegative() {
        assertThat(VersionUtils.compareVersions(null, "1.0")).isNegative();
    }

    @Test
    void compareVersions_rightNull_returnsPositive() {
        assertThat(VersionUtils.compareVersions("1.0", null)).isPositive();
    }

    @Test
    void compareVersions_equalStrings() {
        assertThat(VersionUtils.compareVersions("1.2.3", "1.2.3")).isZero();
    }

    @Test
    void compareVersions_majorVersion() {
        assertThat(VersionUtils.compareVersions("2.0.0", "1.0.0")).isPositive();
    }

    @Test
    void compareVersions_patchVersion() {
        assertThat(VersionUtils.compareVersions("1.0.1", "1.0.0")).isPositive();
    }

    @Test
    void compareVersions_mixedSeparators() {
        assertThat(VersionUtils.compareVersions("1.0.0", "1_0_0")).isZero();
    }

    @Test
    void compareVersions_samePrefixDifferentLength() {
        // Missing trailing segments treated as 0, so 1.0 == 1.0.0 numerically
        assertThat(VersionUtils.compareVersions("1.0", "1.0.0")).isZero();
    }

    @Test
    void compareVersions_nonNumericSegments() {
        assertThat(VersionUtils.compareVersions("1.0.0.Final", "1.0.0")).isZero();
    }

    @Test
    void compareVersions_qualifier() {
        assertThat(VersionUtils.compareVersions("32.1.3-jre", "32.1.3")).isZero();
    }

    @Test
    void formatSize_zero() {
        assertThat(VersionUtils.formatSize(0)).isEqualTo("0 B");
    }

    @Test
    void formatSize_bytes() {
        assertThat(VersionUtils.formatSize(500)).isEqualTo("500 B");
    }

    @Test
    void formatSize_kilobytes() {
        assertThat(VersionUtils.formatSize(1024)).isEqualTo("1.0 KB");
    }

    @Test
    void formatSize_megabytes() {
        assertThat(VersionUtils.formatSize(1048576)).isEqualTo("1.0 MB");
    }

    @Test
    void formatSize_gigabytes() {
        assertThat(VersionUtils.formatSize(1073741824L)).isEqualTo("1.0 GB");
    }
}
