package com.example.erp.masterdata.integration;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-MONO-018 D5 — federation isolation regression, erp slice
 * (TASK-ERP-BE-004).
 *
 * <p>HTTP-layer integration test (Testcontainers MySQL + Kafka + JWKS
 * MockWebServer via {@link AbstractMasterdataIntegrationTest}, real
 * {@code SecurityFilterChain} + {@code TenantClaimValidator} decode check +
 * {@code SecurityConfig.onAuthenticationFailure} 403 mapping +
 * {@code TenantClaimEnforcer} defense-in-depth filter). Asserts the fail-closed
 * tenant gate end-to-end on the {@code /api/erp/masterdata/employees} read
 * surface:
 *
 * <ul>
 *   <li>foreign {@code tenant_id=scm} → 403 {@code TENANT_FORBIDDEN};</li>
 *   <li>{@code tenant_id=*} (platform-scope wildcard) → 2xx (accepted —
 *       erp-specific edge, mirrors {@code TenantClaimValidator} unit test);</li>
 *   <li>matching {@code tenant_id=erp} → 2xx (sanity);</li>
 *   <li>no token → 401.</li>
 * </ul>
 *
 * <p>Mirrors the finance {@code CrossTenantHttpIntegrationTest} pattern; the
 * sibling producer ITs (wms {@code OidcAuthIntegrationTest} / scm
 * {@code MultiTenantIsolationIntegrationTest} / finance) + the console-bff
 * {@code CrossTenantDenyIntegrationTest} (TASK-PC-BE-006) complete the D5
 * isolation surface.
 */
@AutoConfigureMockMvc
class CrossTenantHttpIntegrationTest extends AbstractMasterdataIntegrationTest {

    private static final String EMPLOYEES = "/api/erp/masterdata/employees";

    private static RSAKey rsaKey;

    @Autowired
    MockMvc mockMvc;

    @BeforeAll
    static void publishSigningKey() throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID("test-key-erp-d5").generate();
        // Publish the public JWK into the base-owned JWKS server (allowed-issuers
        // = http://test-issuer is set by the base @DynamicPropertySource).
        publishJwks("{\"keys\":[" + rsaKey.toPublicJWK().toJSONString() + "]}");
    }

    private String token(String tenant) throws Exception {
        // scope "erp.read" satisfies RoleScopeAuthorizationAdapter READ
        // (fail-closed: requires erp.read / erp.write / operator). The
        // employees list passes targetDepartmentId=null, so no data-scope check.
        // Cross-tenant tokens are still rejected at the JWT decode tenant gate
        // (TenantClaimValidator) BEFORE authorization, so scm → 403
        // TENANT_FORBIDDEN regardless of scope.
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
                new JWTClaimsSet.Builder()
                        .subject("user-d5")
                        .issuer("http://test-issuer")
                        .claim("tenant_id", tenant)
                        .claim("scope", "erp.read")
                        .issueTime(new Date())
                        .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                        .build());
        jwt.sign(new RSASSASigner(rsaKey));
        return jwt.serialize();
    }

    @Test
    @DisplayName("cross-tenant token (tenant_id=scm) → 403 TENANT_FORBIDDEN")
    void crossTenantForbidden() throws Exception {
        mockMvc.perform(get(EMPLOYEES)
                        .header("Authorization", "Bearer " + token("scm")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_FORBIDDEN"));
    }

    @Test
    @DisplayName("wildcard token (tenant_id=*) → 2xx (platform-scope accepted)")
    void wildcardTenantAccepted() throws Exception {
        mockMvc.perform(get(EMPLOYEES)
                        .header("Authorization", "Bearer " + token("*")))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("matching tenant token (tenant_id=erp) → 2xx")
    void matchingTenantAccepted() throws Exception {
        mockMvc.perform(get(EMPLOYEES)
                        .header("Authorization", "Bearer " + token("erp")))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("no token → 401")
    void missingToken() throws Exception {
        mockMvc.perform(get(EMPLOYEES))
                .andExpect(status().isUnauthorized());
    }
}
