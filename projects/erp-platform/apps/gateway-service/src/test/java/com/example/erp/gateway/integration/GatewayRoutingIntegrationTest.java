package com.example.erp.gateway.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * End-to-end happy / unhappy paths through the erp gateway's real route + filter chain
 * (TASK-MONO-458). Every case sends an HTTP request via {@code WebTestClient} — never a filter
 * instantiation or a route-config read (those live in the unit suite) — so this exercises the
 * production {@code SecurityConfig} resource-server, the {@code TenantClaimValidator} gate, the
 * {@code RoleAdmissionFilter} and the {@code masterdata-service} route as they are actually wired.
 *
 * <p>See {@link GatewayIntegrationBase} for the erp-vs-iam parity note (AC-5).
 */
@Tag("integration")
@DisplayName("erp gateway 통합 — 라우팅 · JWT 인증 · 역할 admission · 테넌트 게이트")
class GatewayRoutingIntegrationTest extends GatewayIntegrationBase {

    private static final String PATH = "/api/erp/masterdata/employees/1";

    // --- routing + positive auth ---

    @Test
    @DisplayName("유효한 erp operator 토큰 → downstream 200, 경로 1:1 보존 (rewrite 없음)")
    void validErpOperatorTokenRoutesToDownstreamPreservingPath() {
        String token = jwt.signErpOperatorToken("ops-1");

        webTestClient.get().uri(PATH)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                // The downstream stub echoes the path it actually received: erp forwards
                // /api/erp/** 1:1 with NO RewritePath (unlike iam/scm), so it must be unchanged.
                .expectHeader().valueEquals(RECEIVED_PATH_HEADER, PATH)
                .expectBody().jsonPath("$.employees").exists();
    }

    @Test
    @DisplayName("scope 만 있는 client_credentials 토큰 → 200 (머신 호출은 scope 로 인가)")
    void clientCredentialsScopeTokenPassesThroughToDownstream() {
        String token = jwt.signClientCredentialsToken("erp-internal-client");

        webTestClient.get().uri(PATH)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("SUPER_ADMIN wildcard(tenant_id=*) 토큰 → 200")
    void superAdminWildcardTokenPassesThrough() {
        String token = jwt.signSuperAdminToken("super-1");

        webTestClient.get().uri(PATH)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();
    }

    // --- authentication rejections (401) ---

    @Test
    @DisplayName("토큰 없음 → 401 UNAUTHORIZED (downstream 미도달)")
    void missingTokenIsRejectedWith401() {
        webTestClient.get().uri(PATH)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("만료된 토큰 → 401 UNAUTHORIZED")
    void expiredTokenIsRejectedWith401() {
        String token = jwt.signExpiredToken("ops-expired");

        webTestClient.get().uri(PATH)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("서명 변조 토큰 → 401 UNAUTHORIZED")
    void tamperedSignatureIsRejectedWith401() {
        String token = jwt.signErpOperatorToken("ops-tamper");
        String[] parts = token.split("\\.");
        String flipped = parts[2].endsWith("A")
                ? parts[2].substring(0, parts[2].length() - 1) + "B"
                : parts[2].substring(0, parts[2].length() - 1) + "A";
        String tampered = parts[0] + "." + parts[1] + "." + flipped;

        webTestClient.get().uri(PATH)
                .header("Authorization", "Bearer " + tampered)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("허용 목록 밖 issuer 토큰(서명은 유효) → 401 UNAUTHORIZED")
    void untrustedIssuerIsRejectedWith401() {
        String token = jwt.signUntrustedIssuerToken("ops-evil");

        webTestClient.get().uri(PATH)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    // --- authorization rejections (403) ---

    @Test
    @DisplayName("교차 테넌트 토큰(자격 없음) → 403 TENANT_FORBIDDEN (401 아님 — 서명은 유효)")
    void crossTenantTokenIsRejectedWith403TenantForbidden() {
        String token = jwt.signCrossTenantToken("wms-intruder");

        webTestClient.get().uri(PATH)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo("TENANT_FORBIDDEN");
    }

    @Test
    @DisplayName("역할도 scope 도 없는 유효 토큰 → 403 FORBIDDEN (인증됨, 인가 안 됨 — rule 6)")
    void authenticatedTokenWithoutRoleOrScopeIsRejectedWith403Forbidden() {
        // code=FORBIDDEN (not TENANT_FORBIDDEN) proves this is the RoleAdmissionFilter, not the
        // tenant gate — the tenant is erp, signature and issuer are valid; only authorization fails.
        String token = jwt.signNoRoleNoScopeToken("roleless-1");

        webTestClient.get().uri(PATH)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    // --- public route (no auth) ---

    @Test
    @DisplayName("actuator health 는 공개 경로 → 인증 없이 200")
    void actuatorHealthIsPublic() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }
}
