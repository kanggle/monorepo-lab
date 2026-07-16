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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-layer integration test pinning OAuth2 <strong>scope</strong> enforcement (TASK-FIN-BE-046).
 * Real {@code SecurityConfig} + validator chain + MockWebServer JWKS, valid {@code tenant_id=finance}
 * tokens throughout — so the tenant gate is out of the way and the only variable is the caller's
 * {@code scope}/{@code roles}.
 *
 * <p>The gap this closes: before FIN-BE-046 a {@code finance.read}-only token could perform every
 * write (open account, hold, transfer), because the chain required only {@code .authenticated()}.
 * These assertions are the regression coverage that was missing — no test asserted a scope-based
 * denial, so the gap survived green CI.
 *
 * <p>Read-OR-scope is deliberate: an operator-role token with no finance scope must still read
 * (the platform-console operator read consumer, ADR-MONO-013), and a write-scope token must still
 * read (write implies read). Both are pinned below alongside the deny cases.
 */
@AutoConfigureMockMvc
class ScopeEnforcementHttpIntegrationTest extends AbstractAccountIntegrationTest {

    private static RSAKey rsaKey;

    @Autowired
    MockMvc mockMvc;

    @BeforeAll
    static void publishSigningKey() throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID("scope-test-key").generate();
        publishJwks("{\"keys\":[" + rsaKey.toPublicJWK().toJSONString() + "]}");
    }

    /** A signed finance-tenant token carrying exactly the given scopes and roles (either may be null). */
    private String token(List<String> scopes, List<String> roles) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject("user-1")
                .issuer("http://test-issuer")
                .claim("tenant_id", TENANT_FINANCE)
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(300)));
        if (scopes != null) claims.claim("scope", scopes);
        if (roles != null) claims.claim("roles", roles);
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
                claims.build());
        jwt.sign(new RSASSASigner(rsaKey));
        return jwt.serialize();
    }

    private static final String OPEN_BODY =
            "{\"ownerRef\":\"scope-probe\",\"currency\":\"KRW\"}";

    // ---- deny: read scope cannot write (the fix) -------------------------------------------------

    @Test
    @DisplayName("finance.read token → POST /accounts → 403 PERMISSION_DENIED (write requires finance.write)")
    void readScopeCannotOpenAccount() throws Exception {
        mockMvc.perform(post("/api/finance/accounts")
                        .header("Authorization", "Bearer " + token(List.of("finance.read"), null))
                        .header("Idempotency-Key", "scope-read-open")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OPEN_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("finance.read token → POST /accounts/{id}/transfers → 403 (fund movement blocked at authz)")
    void readScopeCannotTransfer() throws Exception {
        mockMvc.perform(post("/api/finance/accounts/acc-x/transfers")
                        .header("Authorization", "Bearer " + token(List.of("finance.read"), null))
                        .header("Idempotency-Key", "scope-read-xfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toAccountId\":\"acc-y\",\"money\":{\"amount\":\"100\",\"currency\":\"KRW\"}}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    // ---- allow: read scope can read --------------------------------------------------------------

    @Test
    @DisplayName("finance.read token → GET /accounts/{unknown} → 404 (read admitted, gate open)")
    void readScopeCanRead() throws Exception {
        mockMvc.perform(get("/api/finance/accounts/does-not-exist")
                        .header("Authorization", "Bearer " + token(List.of("finance.read"), null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    // ---- allow: write scope can write (and read) -------------------------------------------------

    @Test
    @DisplayName("finance.write token → POST /accounts → 201 (write admitted)")
    void writeScopeCanOpenAccount() throws Exception {
        mockMvc.perform(post("/api/finance/accounts")
                        .header("Authorization", "Bearer " + token(List.of("finance.write"), null))
                        .header("Idempotency-Key", "scope-write-open")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OPEN_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accountId").exists())
                .andExpect(jsonPath("$.data.status").value("PENDING_KYC"));
    }

    @Test
    @DisplayName("finance.write token → GET /accounts/{unknown} → 404 (write implies read)")
    void writeScopeCanRead() throws Exception {
        mockMvc.perform(get("/api/finance/accounts/does-not-exist")
                        .header("Authorization", "Bearer " + token(List.of("finance.write"), null)))
                .andExpect(status().isNotFound());
    }

    // ---- allow: operator role admits without any finance scope (console read consumer) -----------

    @Test
    @DisplayName("operator role, NO finance scope → GET → 404 (role-OR-scope; console read consumer preserved)")
    void operatorRoleCanReadWithoutScope() throws Exception {
        mockMvc.perform(get("/api/finance/accounts/does-not-exist")
                        .header("Authorization", "Bearer " + token(null, List.of("OPERATOR"))))
                .andExpect(status().isNotFound());
    }

    // ---- deny: no scope and no role is fully unprivileged ----------------------------------------

    @Test
    @DisplayName("finance token with NO scope and NO role → POST → 403, GET → 403 (defense in depth)")
    void unprivilegedFinanceTokenDenied() throws Exception {
        String bare = token(null, null);
        mockMvc.perform(post("/api/finance/accounts")
                        .header("Authorization", "Bearer " + bare)
                        .header("Idempotency-Key", "scope-bare-open")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OPEN_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/finance/accounts/does-not-exist")
                        .header("Authorization", "Bearer " + bare))
                .andExpect(status().isForbidden());
    }
}
