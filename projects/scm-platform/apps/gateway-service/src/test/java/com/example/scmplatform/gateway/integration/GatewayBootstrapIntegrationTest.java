package com.example.scmplatform.gateway.integration;

import com.example.scmplatform.gateway.testsupport.JwtTestHelper;
import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * End-to-end happy / unhappy path tests through the gateway:
 *
 * <ul>
 *   <li>tenant_id=scm → 200 (downstream MockWebServer responds).</li>
 *   <li>tenant_id=wms → 403 TENANT_FORBIDDEN.</li>
 *   <li>SUPER_ADMIN tenant_id=* → 200 (platform-scope wildcard).</li>
 *   <li>client_credentials token (V0013 internal client shape) → 200 — scm v1's
 *       primary auth pattern since v1 is backend-only.</li>
 *   <li>Tampered signature → 401.</li>
 * </ul>
 */
@Tag("integration")
class GatewayBootstrapIntegrationTest extends GatewayIntegrationBase {

    @Test
    void validScmTokenPassesThroughToDownstream() {
        downstream.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"po\":[]}"));

        String token = jwt.signScmToken("buyer-1");

        webTestClient.get().uri("/api/v1/procurement/po")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.po").exists();
    }

    @Test
    void clientCredentialsTokenPassesThroughToDownstream() {
        // scm v1 = backend only — the primary authentication pattern is
        // service-to-service via V0013-seeded client_credentials grant.
        downstream.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"ok\":true}"));

        String token = jwt.signClientCredentialsToken();

        webTestClient.get().uri("/api/v1/procurement/po")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void crossTenantTokenIsRejectedWith403TenantForbidden() {
        String token = jwt.signCrossTenantToken("wms-user");

        webTestClient.get().uri("/api/v1/procurement/po")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("TENANT_FORBIDDEN");
    }

    @Test
    void authenticatedTokenWithoutRoleOrScopeIsRejectedWith403Forbidden() {
        // Rule-6 admission (TASK-MONO-416): a valid scm token — correct tenant, issuer and
        // signature — carrying neither a role nor a scope is authenticated but NOT authorized,
        // and must be 403'd at the edge. The paired clientCredentialsTokenPassesThroughToDownstream
        // test (scope, no role → 200) is the regression guard for the scope leg — together they
        // pin that admission gates on "role OR scope", not role alone. code=FORBIDDEN (not
        // TENANT_FORBIDDEN) proves it is the admission gate, not the tenant gate.
        String token = jwt.signNoRoleToken("roleless-1");

        webTestClient.get().uri("/api/v1/procurement/po")
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

        webTestClient.get().uri("/api/v1/procurement/po")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void tamperedTokenSignatureReturns401() {
        // TASK-MONO-542: this used to mangle the LAST signature character inline, which for
        // RS256/2048 carries only 2 real bits. Measured over 400 tokens, that left the
        // signature bytes unchanged 26.75% of the time, so a quarter of runs handed a
        // perfectly VALID token to a test asserting 401 — the gateway correctly routed it
        // downstream, the MockWebServer had nothing queued and blocked, and the test died
        // on a 5s read timeout that looked like flakiness. tamperSignature() mutates the
        // first signature character and verifies the decoded bytes actually changed.
        String tampered = JwtTestHelper.tamperSignature(jwt.signScmToken("buyer-1"));

        webTestClient.get().uri("/api/v1/procurement/po")
                .header("Authorization", "Bearer " + tampered)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }
}
