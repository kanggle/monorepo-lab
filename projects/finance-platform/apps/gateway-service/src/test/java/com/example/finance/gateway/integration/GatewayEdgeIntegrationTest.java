package com.example.finance.gateway.integration;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end edge behaviour for the finance gateway, exercised through the real route + filter
 * chain over HTTP (TASK-MONO-458). Each test sends a {@link org.springframework.test.web.reactive.server.WebTestClient}
 * request that traverses: SecurityConfig (JWT verify) → AllowedIssuersValidator → TenantClaimValidator
 * → RoleAdmissionFilter → identity strip/enrich → RequestRateLimiter → the downstream stub. Nothing
 * here constructs a filter directly — that is the unit tests' job, and AC-2's line.
 *
 * <h2>AC-5 — how finance's coverage differs from iam's, and why</h2>
 * <ul>
 *   <li><b>1:1 routing, not RewritePath.</b> iam (and scm) strip a {@code /api/v1/} external
 *       prefix before forwarding; finance forwards {@code /api/finance/accounts/**} unchanged
 *       (TASK-MONO-357 § Routes — introducing an external prefix would be a client-visible
 *       contract change, not a gateway bootstrap). So {@link #validTokenReachesDownstreamOneToOne()}
 *       asserts the downstream receives the <em>same</em> path, where iam asserts a rewritten one.</li>
 *   <li><b>Rule-6 admission is wired.</b> finance is an entitlement-plane operator platform, so
 *       a role/scope-less token is 403'd at the edge ({@link #noRoleNoScopeTokenRejected403Forbidden()}).
 *       iam's gateway does not gate on rule-6 admission this way.</li>
 *   <li><b>Property prefix.</b> finance keys issuer/tenant under {@code financeplatform.oauth2.*},
 *       tenant {@code finance}; iam uses its own prefix and tenant. The shared validator chain and
 *       error envelope ({@code UNAUTHORIZED}/{@code TENANT_FORBIDDEN}/{@code FORBIDDEN}) are
 *       identical because both scan {@code com.example.apigateway}.</li>
 * </ul>
 *
 * <h2>AC-3 — mutation (CI-authoritative; local Windows Testcontainers is flaky)</h2>
 * Removing {@code com.example.apigateway} from
 * {@code GatewayServiceApplication#scanBasePackages} (which unregisters the shared
 * {@code SecurityConfig}) makes {@link #unauthenticatedRequestRejected401()} and
 * {@link #noRoleNoScopeTokenRejected403Forbidden()} go RED — the protected route would answer 200
 * without a token. Equivalently, deleting the {@code roleAdmissionFilter} bean from
 * {@code GatewayIdentityConfig} makes {@link #noRoleNoScopeTokenRejected403Forbidden()} RED.
 */
@Tag("integration")
class GatewayEdgeIntegrationTest extends GatewayIntegrationBase {

    private static final String ACCOUNTS_PATH = "/api/finance/accounts/42";

    @Test
    void validTokenReachesDownstreamOneToOne() throws InterruptedException {
        downstream.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"accountId\":\"42\"}"));

        String token = jwt.signFinanceToken("operator-1");

        webTestClient.get().uri(ACCOUNTS_PATH)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accountId").isEqualTo("42");

        // finance forwards 1:1 — no RewritePath — so the downstream must see the sent path.
        RecordedRequest received = downstream.takeRequest(5, TimeUnit.SECONDS);
        assertThat(received).as("downstream did not receive the forwarded request").isNotNull();
        assertThat(received.getPath())
                .as("finance has no RewritePath; the downstream must see the original path")
                .isEqualTo(ACCOUNTS_PATH);
    }

    @Test
    void scopeOnlyMachineTokenReachesDownstream() {
        downstream.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));

        // Scope but no role — admitted on the rule-6 "role OR scope" scope leg.
        String token = jwt.signScopeOnlyToken("machine-client");

        webTestClient.get().uri(ACCOUNTS_PATH)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void superAdminWildcardTokenReachesDownstream() {
        downstream.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));

        String token = jwt.signSuperAdminToken("super-1");

        webTestClient.get().uri(ACCOUNTS_PATH)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void unauthenticatedRequestRejected401() {
        webTestClient.get().uri(ACCOUNTS_PATH)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    void expiredTokenRejected401() {
        String token = jwt.signExpiredToken("operator-expired");

        webTestClient.get().uri(ACCOUNTS_PATH)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    void tamperedSignatureRejected401() {
        // Signed by a foreign key not in the JWKS (real kid advertised) → verification always fails.
        // Replaces a byte-flip tamper that was a ~25%-per-key no-op (MONO-458 residual; see
        // JwtTestHelper#signForgedSignatureToken).
        String tampered = jwt.signForgedSignatureToken("operator-1");

        webTestClient.get().uri(ACCOUNTS_PATH)
                .header("Authorization", "Bearer " + tampered)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    void wrongIssuerTokenRejected401() {
        // Signature and tenant are fine; the issuer is not on finance's allowlist.
        String token = jwt.signWrongIssuerToken("operator-1");

        webTestClient.get().uri(ACCOUNTS_PATH)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    void crossTenantTokenRejected403TenantForbidden() {
        // A signature-valid token for tenant "wms" with no finance entitlement. The tenant gate
        // maps this to 403 TENANT_FORBIDDEN (not 401) — re-authenticating would not help.
        String token = jwt.signCrossTenantToken("wms-user");

        webTestClient.get().uri(ACCOUNTS_PATH)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("TENANT_FORBIDDEN");
    }

    @Test
    void noRoleNoScopeTokenRejected403Forbidden() {
        // Correct tenant, issuer and signature but neither role nor scope: authenticated,
        // NOT authorized. Rule-6 admission 403s it with code=FORBIDDEN (not TENANT_FORBIDDEN),
        // proving it is the admission gate, not the tenant gate, that turned it away.
        String token = jwt.signNoRoleToken("roleless-1");

        webTestClient.get().uri(ACCOUNTS_PATH)
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
