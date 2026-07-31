package com.wms.outbound.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins outbound-service's {@link PublicPaths} (ADR-MONO-058 § D5, TASK-BE-570).
 *
 * <p>Unlike master/inventory/inbound/admin, outbound-service's pre-existing
 * {@code PUBLIC_PATHS} array carried a 5th entry, {@code /webhooks/erp/order}
 * (HMAC-verified inside the controller) — this task's investigation re-verified
 * the divergence at implementation time (AC-3) rather than assuming the
 * 4-entry allow-list the task's own Goal narrative described for "all 5"
 * services holds here too.
 */
@DisplayName("outbound-service PublicPaths")
class PublicPathsTest {

    /** The literal {@code PUBLIC_PATHS} array this class replaces (pinned, not re-derived). */
    private static final Set<String> ORIGINAL_PUBLIC_PATHS = Set.of(
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/prometheus",
            "/webhooks/erp/order"
    );

    @Test
    @DisplayName("EXACT 데이터가 기존 PUBLIC_PATHS 와 동일하다 (webhook 포함, AC-3)")
    void exactSetUnchanged() {
        assertThat(PublicPaths.EXACT).containsExactlyInAnyOrder(
                "/actuator/health", "/actuator/info", "/actuator/prometheus", "/webhooks/erp/order");
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
    @DisplayName("EXACT 엔트리, health 서브패스, webhook 엔드포인트는 공개다 (AC-4)")
    void isPublic_exactEntriesAndHealthSubPathAndWebhook_true() {
        assertThat(PublicPaths.isPublic("/actuator/health")).isTrue();
        assertThat(PublicPaths.isPublic("/actuator/info")).isTrue();
        assertThat(PublicPaths.isPublic("/actuator/prometheus")).isTrue();
        assertThat(PublicPaths.isPublic("/actuator/health/liveness")).isTrue();
        assertThat(PublicPaths.isPublic("/webhooks/erp/order")).isTrue();
    }

    @Test
    @DisplayName("env/heapdump/보호된 API 경로는 비공개다 (AC-4)")
    void isPublic_nonPublicPaths_false() {
        assertThat(PublicPaths.isPublic("/actuator/env")).isFalse();
        assertThat(PublicPaths.isPublic("/actuator/heapdump")).isFalse();
        assertThat(PublicPaths.isPublic("/api/v1/outbound/orders")).isFalse();
    }
}
