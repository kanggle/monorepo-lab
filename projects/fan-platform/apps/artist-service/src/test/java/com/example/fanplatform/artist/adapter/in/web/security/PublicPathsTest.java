package com.example.fanplatform.artist.adapter.in.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Data-preservation + before/after classification parity for artist-service's {@link PublicPaths}
 * (ADR-MONO-058 § D5 — the matching mechanism now delegates to the shared {@code PublicPathSet}
 * in {@code libs/java-security-servlet}; the data — this class's own {@code EXACT}/{@code PREFIXES} —
 * must not move or change).
 */
@DisplayName("PublicPaths (artist-service) — data preserved exactly across the D5 mechanism promotion")
class PublicPathsTest {

    @Test
    @DisplayName("EXACT is exactly the 3 actuator probes — unchanged by the D5 promotion")
    void exactSetUnchanged() {
        assertThat(PublicPaths.EXACT).containsExactlyInAnyOrder(
                "/actuator/health", "/actuator/info", "/actuator/prometheus");
    }

    @Test
    @DisplayName("PREFIXES is exactly the health subtree — unchanged by the D5 promotion")
    void prefixesSetUnchanged() {
        assertThat(PublicPaths.PREFIXES).containsExactly("/actuator/health/");
    }

    @Test
    @DisplayName("classification parity: every probe path is classified the same as before this task")
    void classificationParityBeforeAndAfter() {
        assertThat(PublicPaths.isPublic("/actuator/health")).isTrue();
        assertThat(PublicPaths.isPublic("/actuator/info")).isTrue();
        assertThat(PublicPaths.isPublic("/actuator/prometheus")).isTrue();
        assertThat(PublicPaths.isPublic("/actuator/health/liveness")).isTrue();
        assertThat(PublicPaths.isPublic("/actuator/health/readiness")).isTrue();

        assertThat(PublicPaths.isPublic("/actuator/env")).isFalse();
        assertThat(PublicPaths.isPublic("/actuator/heapdump")).isFalse();
        assertThat(PublicPaths.isPublic("/api/artists")).isFalse();
        assertThat(PublicPaths.isPublic("/api/artists/a-1")).isFalse();
        assertThat(PublicPaths.isPublic((String) null)).isFalse();
    }
}
