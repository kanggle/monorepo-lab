package com.example.scmplatform.gateway.integration;

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
 *   <li>client_credentials token (V0013 internal client shape, {@code aud} = that client) →
 *       403 AUDIENCE_FORBIDDEN — the client is not on this edge's audience allowlist
 *       (TASK-MONO-697 AC-4 (b)).</li>
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
    void clientCredentialsTokenIsRejectedWith403AudienceForbidden() {
        // TASK-MONO-697 AC-4 (b). This token carries aud = scm-platform-internal-services-client,
        // which is how the IdP mints it — and that client is not on this edge's audience
        // allowlist. The AC-0 census found no configured caller of it anywhere in the tree, so the
        // allowlist was deliberately NOT widened for it: under ENFORCE it is refused at the edge.
        // Do not "fix" this cell by minting an allowlisted aud — no such token exists in production.
        //
        // This cell used to be the IT witness that rule-6 admission admits a scope-only token
        // (200). That proof now lives in RoleAdmissionFilterTest#admitsScopeOnlyMachineToken,
        // against the RoleAdmissionFilter this gateway actually wires. No downstream response is
        // queued: a 403 at the edge must never reach the MockWebServer.
        String token = jwt.signClientCredentialsToken();

        webTestClient.get().uri("/api/v1/procurement/po")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("AUDIENCE_FORBIDDEN");
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
        // and must be 403'd at the edge. The scope leg (scope, no role → admitted) is pinned by
        // RoleAdmissionFilterTest#admitsScopeOnlyMachineToken (TASK-MONO-697 AC-4) — together they
        // pin that admission gates on "role OR scope", not role alone. code=FORBIDDEN (not
        // TENANT_FORBIDDEN, not AUDIENCE_FORBIDDEN) proves it is the admission gate.
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
        // TASK-MONO-542: this used to flip the LAST base64url character of the signature
        // inline. For RS256/2048 that character carries 2 significant bits and 4 padding
        // bits, so measured over 400 tokens it left the signature byte-identical 26.75% of
        // the time — a quarter of runs handed a perfectly VALID token to this test, the
        // gateway correctly routed it downstream, the MockWebServer had nothing queued and
        // blocked, and the test died on a 5s read timeout that looked like flakiness.
        // Do not reintroduce the flip; signing with a foreign key never verifies.
        // (Same fix erp/wms took in MONO-458 and finance in MONO-461.)
        String tampered = jwt.signForgedSignatureToken("buyer-1");

        webTestClient.get().uri("/api/v1/procurement/po")
                .header("Authorization", "Bearer " + tampered)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }
}
