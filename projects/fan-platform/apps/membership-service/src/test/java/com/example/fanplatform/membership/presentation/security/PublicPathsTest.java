package com.example.fanplatform.membership.presentation.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Data-preservation + before/after classification parity for membership-service's {@link PublicPaths}
 * (ADR-MONO-058 § D5 — the matching mechanism now delegates to the shared {@code PublicPathSet}
 * in {@code libs/java-security-servlet}; the data — this class's own {@code EXACT}/{@code PREFIXES},
 * including the PortOne webhook entry — must not move or change).
 */
@DisplayName("PublicPaths (membership-service) — data preserved exactly across the D5 mechanism promotion")
class PublicPathsTest {

    @Test
    @DisplayName("EXACT is exactly the 3 actuator probes + the PortOne webhook — unchanged by the D5 promotion")
    void exactSetUnchanged() {
        assertThat(PublicPaths.EXACT).containsExactlyInAnyOrder(
                "/actuator/health", "/actuator/info", "/actuator/prometheus", "/webhooks/portone");
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
        assertThat(PublicPaths.isPublic("/webhooks/portone"))
                .as("the PortOne webhook is public by necessity (TASK-FAN-BE-033) — this must survive D5")
                .isTrue();

        assertThat(PublicPaths.isPublic("/actuator/env")).isFalse();
        assertThat(PublicPaths.isPublic("/actuator/heapdump")).isFalse();
        assertThat(PublicPaths.isPublic("/api/fan/memberships")).isFalse();
        assertThat(PublicPaths.isPublic("/internal/membership/access-check"))
                .as("/internal/** is exempted by a separate predicate composed in "
                        + "ServiceLevelOAuth2Config, NOT by PublicPaths itself — it must stay false here")
                .isFalse();
        assertThat(PublicPaths.isPublic((String) null)).isFalse();
    }
}
