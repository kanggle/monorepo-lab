package com.example.finance.ledger.integration;

import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-layer integration test pinning OAuth2 <strong>scope</strong> enforcement on ledger-service
 * (TASK-FIN-BE-047). Real {@code SecurityConfig} + validator chain + MockWebServer JWKS, valid
 * {@code tenant_id=finance} tokens throughout — so the tenant gate is out of the way and the only
 * variable is the caller's {@code scope}/{@code roles}.
 *
 * <p>The gap this closes: ledger's chain required only {@code .authenticated()}, so a
 * {@code finance.read}-only token could drive every ledger mutation (post journal entries, override
 * FX rates, close accounting periods, resolve reconciliation discrepancies). No test asserted a
 * scope-based denial — every mutating IT drove writes with {@code financeReadToken()} and asserted
 * success — so the gap survived green CI (the account-service fix FIN-BE-046 had not propagated to
 * this sibling). These assertions are the missing regression coverage.
 *
 * <p>Read-OR-scope is deliberate (mirrors account-service): an operator-role token with no finance
 * scope must still read (the platform-console operator read consumer, ADR-MONO-013), and a
 * write-scope token must still read (write implies read).
 */
@AutoConfigureMockMvc
@DisplayName("ledger /api/finance/** scope enforcement (TASK-FIN-BE-047)")
class LedgerScopeEnforcementHttpIntegrationTest extends AbstractLedgerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    /** A mutating endpoint (POST). Authorization runs before the controller, so the body shape is
     *  irrelevant for the deny cases; for the write-scope case we only assert the authz gate opened. */
    private static final String WRITE_PATH = "/api/finance/ledger/entries";
    /** A read endpoint (GET list) that returns 200 regardless of data. */
    private static final String READ_PATH = "/api/finance/ledger/periods";
    private static final String POST_BODY = "{}";

    private String financeToken(Consumer<JWTClaimsSet.Builder> extra) {
        return token(c -> {
            c.claim("tenant_id", "finance");
            extra.accept(c);
        });
    }

    // ---- deny: read scope cannot write (the fix) -------------------------------------------------

    @Test
    @DisplayName("finance.read token → POST entries → 403 PERMISSION_DENIED (write requires finance.write)")
    void readScopeCannotPostJournal() throws Exception {
        mockMvc.perform(post(WRITE_PATH)
                        .header("Authorization", "Bearer " + financeToken(c -> c.claim("scope", List.of("finance.read"))))
                        .header("Idempotency-Key", "scope-read-post")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(POST_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    // ---- allow: read scope can read --------------------------------------------------------------

    @Test
    @DisplayName("finance.read token → GET periods → not 403 (read admitted, gate open)")
    void readScopeCanRead() throws Exception {
        MvcResult r = mockMvc.perform(get(READ_PATH)
                        .header("Authorization", "Bearer " + financeToken(c -> c.claim("scope", List.of("finance.read")))))
                .andReturn();
        assertThat(r.getResponse().getStatus())
                .as("read scope admits reads")
                .isNotEqualTo(403);
    }

    // ---- allow: write scope can write (and read) -------------------------------------------------

    @Test
    @DisplayName("finance.write token → POST entries → not 403 (write admitted; authz gate opened)")
    void writeScopeCanWrite() throws Exception {
        MvcResult r = mockMvc.perform(post(WRITE_PATH)
                        .header("Authorization", "Bearer " + financeToken(c -> c.claim("scope", List.of("finance.write"))))
                        .header("Idempotency-Key", "scope-write-post")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(POST_BODY))
                .andReturn();
        assertThat(r.getResponse().getStatus())
                .as("write scope admits writes at the authz gate (403 would mean the scope gate rejected it)")
                .isNotEqualTo(403);
    }

    @Test
    @DisplayName("finance.write token → GET periods → not 403 (write implies read)")
    void writeScopeCanRead() throws Exception {
        MvcResult r = mockMvc.perform(get(READ_PATH)
                        .header("Authorization", "Bearer " + financeToken(c -> c.claim("scope", List.of("finance.write")))))
                .andReturn();
        assertThat(r.getResponse().getStatus()).isNotEqualTo(403);
    }

    // ---- allow: operator role admits without any finance scope (console read consumer) -----------

    @Test
    @DisplayName("operator role, NO finance scope → GET → not 403 (role-OR-scope; console read consumer preserved)")
    void operatorRoleCanReadWithoutScope() throws Exception {
        MvcResult r = mockMvc.perform(get(READ_PATH)
                        .header("Authorization", "Bearer " + financeToken(c -> c.claim("roles", List.of("OPERATOR")))))
                .andReturn();
        assertThat(r.getResponse().getStatus())
                .as("operator role admits reads without a finance scope")
                .isNotEqualTo(403);
    }

    // ---- entitlement-trust READ authority (TASK-FIN-BE-048) --------------------------------------
    // A platform-console-federated customer operator carries tenant_id=acme,
    // entitled_domains=[finance,wms], NO finance scope, NO roles. Layer-1 (the tenant gate) admits
    // it via trustEntitledDomains(); the converter now grants ROLE_FINANCE_VIEWER (READ only), so its
    // READS pass this gate while its WRITES stay gated — mirrors the WMS ROLE_WMS_VIEWER synthesis
    // (TASK-MONO-162) and the account-service sibling.

    private String entitledOperatorToken() {
        return token(c -> c
                .claim("tenant_id", "acme")
                .claim("entitled_domains", List.of("finance", "wms")));
    }

    @Test
    @DisplayName("entitled operator (entitled_domains=[finance], no scope/role) → GET periods → not 403 (read gate open)")
    void entitledNoScopeNoRoleCanRead() throws Exception {
        MvcResult r = mockMvc.perform(get(READ_PATH)
                        .header("Authorization", "Bearer " + entitledOperatorToken()))
                .andReturn();
        assertThat(r.getResponse().getStatus())
                .as("entitlement-trust VIEWER admits reads for a scopeless/roleless entitled operator")
                .isNotEqualTo(403);
    }

    @Test
    @DisplayName("entitled operator (no scope/role) → POST entries → 403 PERMISSION_DENIED (write gate intact — VIEWER is read-only)")
    void entitledNoScopeNoRoleCannotWrite() throws Exception {
        mockMvc.perform(post(WRITE_PATH)
                        .header("Authorization", "Bearer " + entitledOperatorToken())
                        .header("Idempotency-Key", "entitled-viewer-post")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(POST_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    // ---- platform super-admin wildcard READ authority (TASK-FIN-BE-049) --------------------------
    // A platform super-admin's base OIDC domain-facing token carries tenant_id="*", NO finance scope,
    // NO domain role (ADR-033 S2 / ADR-034 U5 keep SUPER_ADMIN off the domain token), and
    // entitled_domains=[]. Layer-1 (the tenant gate) admits it via allowSuperAdminWildcard(); the
    // converter now grants ROLE_FINANCE_SUPERADMIN_READ (READ only), so its READS pass this gate while
    // its WRITES stay gated — the wildcard sibling of FIN-BE-048 (nightly-e2e run 29635409302, console
    // super-admin persona: finance overview card forbidden, reason=PERMISSION_DENIED).

    private String wildcardSuperadminToken() {
        return token(c -> c.claim("tenant_id", "*"));
    }

    @Test
    @DisplayName("super-admin wildcard (tenant_id='*', no scope/role) → GET periods → not 403 (read gate open)")
    void wildcardSuperadminCanRead() throws Exception {
        MvcResult r = mockMvc.perform(get(READ_PATH)
                        .header("Authorization", "Bearer " + wildcardSuperadminToken()))
                .andReturn();
        assertThat(r.getResponse().getStatus())
                .as("super-admin wildcard READ role admits reads for a scopeless/roleless platform token")
                .isNotEqualTo(403);
    }

    @Test
    @DisplayName("super-admin wildcard (no scope/role) → POST entries → 403 PERMISSION_DENIED (write gate intact — wildcard READ is read-only)")
    void wildcardSuperadminCannotWrite() throws Exception {
        mockMvc.perform(post(WRITE_PATH)
                        .header("Authorization", "Bearer " + wildcardSuperadminToken())
                        .header("Idempotency-Key", "wildcard-superadmin-post")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(POST_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    // ---- deny: no scope and no role is fully unprivileged ----------------------------------------

    @Test
    @DisplayName("finance token with NO scope and NO role → POST 403 + GET 403 (defense in depth)")
    void unprivilegedFinanceTokenDenied() throws Exception {
        String bare = financeToken(c -> { });
        mockMvc.perform(post(WRITE_PATH)
                        .header("Authorization", "Bearer " + bare)
                        .header("Idempotency-Key", "scope-bare-post")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(POST_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(READ_PATH)
                        .header("Authorization", "Bearer " + bare))
                .andExpect(status().isForbidden());
    }
}
