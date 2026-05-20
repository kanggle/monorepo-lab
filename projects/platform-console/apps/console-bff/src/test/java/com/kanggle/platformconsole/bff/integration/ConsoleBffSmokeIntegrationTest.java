package com.kanggle.platformconsole.bff.integration;

import com.kanggle.platformconsole.bff.adapter.outbound.http.CredentialSelectionAdapter;
import com.kanggle.platformconsole.bff.adapter.outbound.http.OperatorCredentialContext;
import com.kanggle.platformconsole.bff.domain.credential.DomainTarget;
import com.kanggle.platformconsole.bff.domain.credential.OutboundCredential;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * console-bff smoke integration tests (AC-12).
 *
 * <p>Boots the full Spring context against a real port with GAP JWKS stubbed
 * via MockWebServer. Exercises:
 * <ol>
 *   <li>AC-9: {@code GET /actuator/health} returns 200.</li>
 *   <li>AC-10: {@code GET /actuator/prometheus} exposes the 3 mandatory metric names.</li>
 *   <li>AC-12 (c): per-domain {@code CredentialSelectionPort} 5-row dispatch dry-run.</li>
 *   <li>AC-12 (e): {@code OperatorCredentialContext} reads headers correctly.</li>
 * </ol>
 */
class ConsoleBffSmokeIntegrationTest extends AbstractConsoleBffIntegrationTest {

    private static RSAKey rsaKey;
    private static String gapOidcJwt;

    @LocalServerPort
    int port;

    @Autowired
    ApplicationContext applicationContext;

    @BeforeAll
    static void generateJwksAndJwt() throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID("test-key-1").generate();

        // Publish the public JWKS to the mock server
        String publicJwksJson = "{\"keys\":[" + rsaKey.toPublicJWK().toJSONString() + "]}";
        publishJwks(publicJwksJson);

        // Build a signed GAP OIDC JWT
        JWSSigner signer = new RSASSASigner(rsaKey);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("http://test-issuer")
                .subject("op-user-001")
                .audience("console-bff")
                .expirationTime(new Date(System.currentTimeMillis() + 3_600_000))
                .claim("tenant_id", "wms")
                .build();

        SignedJWT signed = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key-1").build(),
                claims);
        signed.sign(signer);
        gapOidcJwt = signed.serialize();
    }

    // ---- AC-9 ----

    @Test
    @DisplayName("GET /actuator/health — 200 OK without auth (permitAll)")
    void actuatorHealth_returns200() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("status");
    }

    @Test
    @DisplayName("GET /api/nonexistent — 401 without auth (authenticated() enforced)")
    void unauthenticated_protectedEndpoint_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/nonexistent", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---- AC-10 ----

    @Test
    @DisplayName("GET /actuator/prometheus — exposes all 3 mandatory metric names")
    void actuatorPrometheus_exposesMandatoryMetrics() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/prometheus", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        assertThat(body).contains("bff_fanout_latency_seconds");
        assertThat(body).contains("bff_fanout_errors_total");
        assertThat(body).contains("bff_aggregation_degrade_count_total");
    }

    // ---- AC-12 (c): CredentialSelectionPort 5-row dry-run ----

    @Test
    @DisplayName("CredentialSelectionAdapter: GAP → OperatorToken (dry-run, request context)")
    void credentialSelector_gapDomain_returnsOperatorToken() {
        withRequestContext("op-tok-abc", "gap", gapOidcJwt, () -> {
            CredentialSelectionAdapter adapter = applicationContext
                    .getBean(CredentialSelectionAdapter.class);
            OutboundCredential cred = adapter.selectFor(DomainTarget.GAP);
            assertThat(cred).isInstanceOf(OutboundCredential.OperatorToken.class);
            assertThat(((OutboundCredential.OperatorToken) cred).token()).isEqualTo("op-tok-abc");
        });
    }

    @Test
    @DisplayName("CredentialSelectionAdapter: WMS → GapOidcAccessToken (dry-run)")
    void credentialSelector_wmsDomain_returnsGapOidcToken() {
        withRequestContext("op-tok-abc", "wms", gapOidcJwt, () -> {
            CredentialSelectionAdapter adapter = applicationContext
                    .getBean(CredentialSelectionAdapter.class);
            OutboundCredential cred = adapter.selectFor(DomainTarget.WMS);
            assertThat(cred).isInstanceOf(OutboundCredential.GapOidcAccessToken.class);
        });
    }

    @Test
    @DisplayName("CredentialSelectionAdapter: SCM → GapOidcAccessToken (dry-run)")
    void credentialSelector_scmDomain_returnsGapOidcToken() {
        withRequestContext("op-tok-abc", "scm", gapOidcJwt, () -> {
            CredentialSelectionAdapter adapter = applicationContext
                    .getBean(CredentialSelectionAdapter.class);
            OutboundCredential cred = adapter.selectFor(DomainTarget.SCM);
            assertThat(cred).isInstanceOf(OutboundCredential.GapOidcAccessToken.class);
        });
    }

    @Test
    @DisplayName("CredentialSelectionAdapter: FINANCE → GapOidcAccessToken (dry-run)")
    void credentialSelector_financeDomain_returnsGapOidcToken() {
        withRequestContext("op-tok-abc", "finance", gapOidcJwt, () -> {
            CredentialSelectionAdapter adapter = applicationContext
                    .getBean(CredentialSelectionAdapter.class);
            OutboundCredential cred = adapter.selectFor(DomainTarget.FINANCE);
            assertThat(cred).isInstanceOf(OutboundCredential.GapOidcAccessToken.class);
        });
    }

    @Test
    @DisplayName("CredentialSelectionAdapter: ERP → GapOidcAccessToken (dry-run)")
    void credentialSelector_erpDomain_returnsGapOidcToken() {
        withRequestContext("op-tok-abc", "erp", gapOidcJwt, () -> {
            CredentialSelectionAdapter adapter = applicationContext
                    .getBean(CredentialSelectionAdapter.class);
            OutboundCredential cred = adapter.selectFor(DomainTarget.ERP);
            assertThat(cred).isInstanceOf(OutboundCredential.GapOidcAccessToken.class);
        });
    }

    // ---- AC-12 (e): OperatorCredentialContext header reading ----

    @Test
    @DisplayName("OperatorCredentialContext reads X-Operator-Token and X-Tenant-Id")
    void operatorCredentialContext_readsHeaders() {
        withRequestContext("my-op-token", "tenant-wms", gapOidcJwt, () -> {
            OperatorCredentialContext ctx = applicationContext
                    .getBean(OperatorCredentialContext.class);
            assertThat(ctx.getOperatorToken()).isEqualTo("my-op-token");
            assertThat(ctx.getTenantId()).isEqualTo("tenant-wms");
            assertThat(ctx.hasTenant()).isTrue();
        });
    }

    @Test
    @DisplayName("OperatorCredentialContext: absent X-Tenant-Id → hasTenant() false")
    void operatorCredentialContext_absentTenant_hasTenantFalse() {
        withRequestContext("op-tok", null, gapOidcJwt, () -> {
            OperatorCredentialContext ctx = applicationContext
                    .getBean(OperatorCredentialContext.class);
            assertThat(ctx.hasTenant()).isFalse();
        });
    }

    // ---- Helper ----

    private void withRequestContext(String operatorToken, String tenantId,
                                    String bearerToken, Runnable block) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (operatorToken != null) {
            request.addHeader("X-Operator-Token", operatorToken);
        }
        if (tenantId != null) {
            request.addHeader("X-Tenant-Id", tenantId);
        }
        if (bearerToken != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            block.run();
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
