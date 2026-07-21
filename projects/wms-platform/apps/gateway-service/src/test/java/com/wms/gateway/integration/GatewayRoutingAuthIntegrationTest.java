package com.wms.gateway.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Routing + JWT authentication + rule-6 admission through the real Spring Cloud Gateway
 * route + filter chain (AC-2: every case drives an HTTP request via {@code WebTestClient},
 * none inspect a filter/config in isolation).
 *
 * <p>Each test exercises a specific real element of wms's edge:
 * <ul>
 *   <li>{@link #validOperatorTokenRoutesToDownstreamWithPathPreserved()} — the
 *       {@code master-service} route predicate + downstream forwarding; wms preserves the
 *       path (no RewritePath, unlike fan).</li>
 *   <li>{@link #missingBearerTokenIsRejected401()} — the shared {@code SecurityConfig}
 *       {@code .anyExchange().authenticated()} + {@code GatewayErrorHandler} envelope.</li>
 *   <li>{@link #tamperedSignatureIsRejected401()} — the {@code NimbusReactiveJwtDecoder}
 *       signature verification against the JWKS MockWebServer.</li>
 *   <li>{@link #expiredTokenIsRejected401()} — the {@code JwtTimestampValidator} in
 *       {@code GatewayJwtDecoders#validatorChain}.</li>
 *   <li>{@link #crossTenantTokenIsRejected403TenantForbidden()} — wms's strict
 *       {@code TenantClaimValidator} gate (no {@code "*"} wildcard) surfacing as
 *       {@code TENANT_FORBIDDEN}.</li>
 *   <li>{@link #rolelessTokenIsRejected403Forbidden()} — wms's {@code RoleAdmissionFilter}
 *       (rule-6), distinct from the tenant gate.</li>
 *   <li>{@link #healthEndpointIsPublic()} — the {@code PUBLIC_PATHS} permitAll.</li>
 * </ul>
 */
@Tag("integration")
class GatewayRoutingAuthIntegrationTest extends GatewayIntegrationBase {

    @Test
    void validOperatorTokenRoutesToDownstreamWithPathPreserved() throws Exception {
        downstream.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"warehouses\":[]}"));

        String token = jwt.signToken("op-route-1", "MASTER_READ", 300,
                Map.of("email", "op-route-1@test.local"));

        webTestClient.get().uri("/api/v1/master/warehouses")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.warehouses").exists();

        RecordedRequest received = downstream.takeRequest(5, TimeUnit.SECONDS);
        assertThat(received).as("downstream master-service stub did not receive the request").isNotNull();
        assertThat(received.getPath())
                .as("wms applies no RewritePath — the /api/v1/master path must be forwarded verbatim")
                .isEqualTo("/api/v1/master/warehouses");
    }

    @Test
    void missingBearerTokenIsRejected401() {
        webTestClient.get().uri("/api/v1/master/warehouses")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    void tamperedSignatureIsRejected401() {
        String token = jwt.signToken("op-tamper-1", "MASTER_READ", 300);
        String[] parts = token.split("\\.");
        String lastChar = parts[2].substring(parts[2].length() - 1);
        String flipped = "A".equals(lastChar) ? "B" : "A";
        String tampered = parts[0] + "." + parts[1] + "."
                + parts[2].substring(0, parts[2].length() - 1) + flipped;

        webTestClient.get().uri("/api/v1/master/warehouses")
                .header("Authorization", "Bearer " + tampered)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    void expiredTokenIsRejected401() {
        // ttl = -60s → exp already in the past → JwtTimestampValidator rejects.
        String token = jwt.signToken("op-expired-1", "MASTER_READ", -60);

        webTestClient.get().uri("/api/v1/master/warehouses")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    void crossTenantTokenIsRejected403TenantForbidden() {
        // A signature-valid, issuer-allowlisted token whose tenant_id is NOT wms. wms's gate
        // is strict equality with no "*" wildcard, so this is a tenant mismatch, surfaced as
        // 403 TENANT_FORBIDDEN (never reaches the downstream stub — no response enqueued).
        String token = jwt.signToken("scm-user-1", "MASTER_READ", 300,
                Map.of("tenant_id", "scm-platform"));

        webTestClient.get().uri("/api/v1/master/warehouses")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("TENANT_FORBIDDEN");
    }

    @Test
    void rolelessTokenIsRejected403Forbidden() {
        // Correct tenant, issuer and signature but carrying neither a role nor a scope:
        // authenticated but NOT authorized. code=FORBIDDEN (not TENANT_FORBIDDEN) proves it is
        // the RoleAdmissionFilter firing, not the tenant gate. Never reaches downstream.
        String token = jwt.signToken("roleless-1", null, 300);

        webTestClient.get().uri("/api/v1/master/warehouses")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void healthEndpointIsPublic() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }
}
