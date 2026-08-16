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
        // TASK-MONO-543: this used to flip the LAST base64url character of the signature
        // inline. For RS256/2048 that character carries 2 significant bits and 4 padding
        // bits, so measured on fan's own keys over 400 tokens it left the signature
        // byte-identical 26.0% of the time — a quarter of runs handed a perfectly VALID
        // token to this test, the gateway correctly routed it downstream, the MockWebServer
        // had nothing queued and blocked, and the test died on a 5s read timeout that looked
        // like flakiness. Do not reintroduce the flip; signing with a foreign key never
        // verifies. (Same fix erp/wms took in MONO-458, finance in MONO-461, scm in MONO-542.)
        String tampered = jwt.signForgedSignatureToken("fan-1");

        webTestClient.get().uri("/api/v1/community/posts")
                .header("Authorization", "Bearer " + tampered)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }
}
