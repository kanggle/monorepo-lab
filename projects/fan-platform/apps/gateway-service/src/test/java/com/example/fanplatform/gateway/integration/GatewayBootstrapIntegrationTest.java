package com.example.fanplatform.gateway.integration;

import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * End-to-end happy / unhappy path tests through the gateway:
 *
 * <ul>
 *   <li>tenant_id=fan-platform → 200 (downstream MockWebServer responds).</li>
 *   <li>tenant_id=wms → 403 TENANT_FORBIDDEN.</li>
 *   <li>SUPER_ADMIN tenant_id=* → 200 (platform-scope wildcard).</li>
 *   <li>Legacy issuer → 200.</li>
 *   <li>Unknown issuer → 401.</li>
 * </ul>
 */
@Tag("integration")
class GatewayBootstrapIntegrationTest extends GatewayIntegrationBase {

    @Test
    void validFanPlatformTokenPassesThroughToDownstream() {
        downstream.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"posts\":[]}"));

        String token = jwt.signFanToken("fan-1");

        webTestClient.get().uri("/api/v1/community/posts")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.posts").exists();
    }

    @Test
    void crossTenantTokenIsRejectedWith403TenantForbidden() {
        String token = jwt.signCrossTenantToken("wms-user");

        webTestClient.get().uri("/api/v1/community/posts")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("TENANT_FORBIDDEN");
    }

    @Test
    void authenticatedTokenWithoutRoleIsRejectedWith403Forbidden() {
        // Rule-6 admission (TASK-MONO-416): a valid fan-platform token — correct tenant,
        // issuer and signature — carrying neither a role nor a scope is authenticated but
        // NOT authorized, and must be 403'd at the edge. code=FORBIDDEN (not TENANT_FORBIDDEN)
        // proves it is the admission gate firing, not the tenant gate; the request never
        // reaches the downstream MockWebServer (no response enqueued).
        String token = jwt.signNoRoleToken("roleless-1");

        webTestClient.get().uri("/api/v1/community/posts")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void superAdminWildcardTokenPassesThrough() {
        downstream.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"ok\":true}"));

        String token = jwt.signSuperAdminToken("super-1");

        webTestClient.get().uri("/api/v1/community/posts")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void tamperedTokenSignatureReturns401() {
        String token = jwt.signFanToken("fan-1");
        // Mangle the last byte of the signature segment so the token fails
        // signature verification at the gateway.
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "." +
                (parts[2].endsWith("A") ? parts[2].substring(0, parts[2].length() - 1) + "B"
                        : parts[2].substring(0, parts[2].length() - 1) + "A");

        webTestClient.get().uri("/api/v1/community/posts")
                .header("Authorization", "Bearer " + tampered)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }
}
