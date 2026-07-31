package com.wms.master.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins master-service's {@link PublicPaths} (ADR-MONO-058 § D5, TASK-BE-570).
 *
 * <p>{@link #exactSetUnchanged()}/{@link #prefixSetUnchanged()} pin the exact
 * literal data that used to live inline in {@code SecurityConfig.PUBLIC_PATHS}
 * (AC-3) — a future accidental edit of the data is caught even if every probe
 * path assertion below still passes. {@link #asAntPatternsMatchesOriginalArray()}
 * proves the Ant-pattern accessor reproduces the exact set the original
 * {@code PUBLIC_PATHS} array contained, byte-for-byte (AC-2's data half — the
 * filter-chain half is {@code PublicPathsFilterChainParityTest}).
 */
@DisplayName("master-service PublicPaths")
class PublicPathsTest {

    /** The literal {@code PUBLIC_PATHS} array this class replaces (pinned, not re-derived). */
    private static final Set<String> ORIGINAL_PUBLIC_PATHS = Set.of(
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/prometheus"
    );

    @Test
    @DisplayName("EXACT 데이터가 기존 PUBLIC_PATHS 와 동일하다 (AC-3)")
    void exactSetUnchanged() {
        assertThat(PublicPaths.EXACT).containsExactlyInAnyOrder(
                "/actuator/health", "/actuator/info", "/actuator/prometheus");
    }

    @Test
    @DisplayName("PREFIXES 데이터가 기존 /actuator/health/** 와 동일하다 (AC-3)")
    void prefixSetUnchanged() {
        assertThat(PublicPaths.PREFIXES).containsExactly("/actuator/health/");
    }

    @Test
    @DisplayName("asAntPatterns() 가 기존 PUBLIC_PATHS 배열과 집합적으로 동일하다 (AC-2 데이터)")
    void asAntPatternsMatchesOriginalArray() {
        assertThat(Set.copyOf(Arrays.asList(PublicPaths.asAntPatterns())))
                .isEqualTo(ORIGINAL_PUBLIC_PATHS);
    }

    @Test
    @DisplayName("EXACT 엔트리와 health 서브패스는 공개다 (AC-4)")
    void isPublic_exactEntriesAndHealthSubPath_true() {
        assertThat(PublicPaths.isPublic("/actuator/health")).isTrue();
        assertThat(PublicPaths.isPublic("/actuator/info")).isTrue();
        assertThat(PublicPaths.isPublic("/actuator/prometheus")).isTrue();
        assertThat(PublicPaths.isPublic("/actuator/health/liveness")).isTrue();
    }

    @Test
    @DisplayName("env/heapdump/보호된 API 경로는 비공개다 (AC-4)")
    void isPublic_nonPublicPaths_false() {
        assertThat(PublicPaths.isPublic("/actuator/env")).isFalse();
        assertThat(PublicPaths.isPublic("/actuator/heapdump")).isFalse();
        assertThat(PublicPaths.isPublic("/api/v1/master/warehouses")).isFalse();
    }
}
