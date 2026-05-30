package com.example.finance.account.integration;

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
 * HTTP-layer integration test (Testcontainers MySQL + Redis + Kafka, real
 * SecurityConfig + the validator chain, MockWebServer JWKS). Asserts the
 * fail-closed tenant gate: a valid RS256 token with {@code tenant_id != finance}
 * is rejected with 403 {@code TENANT_FORBIDDEN} (architecture.md Failure #3).
 */
@AutoConfigureMockMvc
class CrossTenantHttpIntegrationTest extends AbstractAccountIntegrationTest {

    private static RSAKey rsaKey;

    @Autowired
    MockMvc mockMvc;

    @BeforeAll
    static void publishSigningKey() throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        // Publish the public JWK into the base-owned JWKS server. The single
        // jwk-set-uri registration lives in AbstractAccountIntegrationTest
        // (a duplicate @DynamicPropertySource here was non-deterministically
        // shadowed by the base — the original failure). @BeforeAll runs before
        // the resource server lazily fetches the JWK set on the first request.
        // allowed-issuers (http://test-issuer) is set by the base; required-
        // tenant-id (finance) by application-test.yml — both already cover this.
        publishJwks("{\"keys\":[" + rsaKey.toPublicJWK().toJSONString() + "]}");
    }

    private String token(String tenant) throws Exception {
        return token(tenant, null);
    }

    private String token(String tenant, java.util.List<String> entitledDomains)
            throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject("user-1")
                .issuer("http://test-issuer")
                .claim("tenant_id", tenant)
                .claim("roles", java.util.List.of("HOLDER"))
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(300)));
        if (entitledDomains != null) {
            claims.claim("entitled_domains", entitledDomains);
        }
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(rsaKey.getKeyID()).build(),
                claims.build());
        jwt.sign(new RSASSASigner(rsaKey));
        return jwt.serialize();
    }

    @Test
    @DisplayName("cross-tenant token (tenant_id=wms) → 403 TENANT_FORBIDDEN")
    void crossTenantForbidden() throws Exception {
        mockMvc.perform(get("/api/finance/accounts/acc-x")
                        .header("Authorization", "Bearer " + token("wms")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_FORBIDDEN"));
    }

    @Test
    @DisplayName("no token → 401 (resource server)")
    void missingToken() throws Exception {
        mockMvc.perform(get("/api/finance/accounts/acc-x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("entitlement-trust: signed entitled_domains=[finance] on tenant_id=acme "
            + "→ gate passes (NOT 403 TENANT_FORBIDDEN)")
    void entitledCrossTenantPassesGate() throws Exception {
        // The dual-accept gate must let this through; the request then resolves
        // against the account repository. acc-x does not exist → 404 (the gate
        // is open). The load-bearing assertion is: NOT 403 and NOT
        // TENANT_FORBIDDEN — the entitlement branch admitted a non-finance slug.
        mockMvc.perform(get("/api/finance/accounts/acc-x")
                        .header("Authorization", "Bearer "
                                + token("acme", java.util.List.of("finance"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("non-entitled: signed entitled_domains=[wms] on tenant_id=acme "
            + "→ 403 TENANT_FORBIDDEN (legacy AND entitlement both fail)")
    void nonEntitledCrossTenantForbidden() throws Exception {
        mockMvc.perform(get("/api/finance/accounts/acc-x")
                        .header("Authorization", "Bearer "
                                + token("acme", java.util.List.of("wms"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_FORBIDDEN"));
    }
}
