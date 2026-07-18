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
        return token(TENANT_FINANCE, scopes, roles, null);
    }

    /** A signed token with an explicit tenant, scopes, roles, and entitled_domains (any may be null). */
    private String token(String tenantId, List<String> scopes, List<String> roles,
                         List<String> entitledDomains) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject("user-1")
                .issuer("http://test-issuer")
                .claim("tenant_id", tenantId)
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(300)));
        if (scopes != null) claims.claim("scope", scopes);
        if (roles != null) claims.claim("roles", roles);
        if (entitledDomains != null) claims.claim("entitled_domains", entitledDomains);
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

    // ---- entitlement-trust READ authority (TASK-FIN-BE-048) --------------------------------------
    // The runtime-confirmed scenario (federation run 29632072800): a platform-console-federated
    // customer operator carries tenant_id=acme, entitled_domains=[finance,wms], NO finance scope,
    // NO roles. Layer-1 (the tenant gate) admits it via trustEntitledDomains(); before this fix
    // layer-2 (this authz gate) 403'd its READS because it held none of readAuthorities. The
    // converter now grants ROLE_FINANCE_VIEWER (READ only), so its READS pass while its WRITES stay
    // gated — the finance analogue of the WMS ROLE_WMS_VIEWER synthesis (TASK-MONO-162).

    @Test
    @DisplayName("entitled operator (tenant=acme, entitled_domains=[finance], no scope/role) → GET → 404 (read gate open)")
    void entitledNoScopeNoRoleCanRead() throws Exception {
        mockMvc.perform(get("/api/finance/accounts/does-not-exist")
                        .header("Authorization", "Bearer "
                                + token("acme", null, null, List.of("finance", "wms"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    @DisplayName("entitled operator (no scope/role) → POST /accounts → 403 (write gate intact — VIEWER is read-only)")
    void entitledNoScopeNoRoleCannotWrite() throws Exception {
        mockMvc.perform(post("/api/finance/accounts")
                        .header("Authorization", "Bearer "
                                + token("acme", null, null, List.of("finance", "wms")))
                        .header("Idempotency-Key", "entitled-viewer-open")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OPEN_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("entitled operator (no scope/role) → POST transfer → 403 (fund movement blocked; entitlement widens READ only)")
    void entitledNoScopeNoRoleCannotTransfer() throws Exception {
        mockMvc.perform(post("/api/finance/accounts/acc-x/transfers")
                        .header("Authorization", "Bearer "
                                + token("acme", null, null, List.of("finance", "wms")))
                        .header("Idempotency-Key", "entitled-viewer-xfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toAccountId\":\"acc-y\",\"money\":{\"amount\":\"100\",\"currency\":\"KRW\"}}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
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
