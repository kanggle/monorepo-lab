package com.example.finance.account.infrastructure.security;

import com.example.security.oauth2.TenantClaimValidator;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-794 (ADR-MONO-083 P7 · owner decisions Q1–Q3, 2026-10-16 UTC) — account-service's business-role gate
 * through the REAL {@link SecurityConfig} chain with really-signed JWTs (finance {@code iam-integration.md}
 * § 업무 역할).
 *
 * <p>The gate is the filter chain alone, so the controllers are replaced by one probe that answers {@code 204} on
 * every {@code /api/finance/**} path: a request the chain lets through ends in 204, a refusal is
 * {@code 403 PERMISSION_DENIED}. The account paths below are the real ones (AC-0 § 2 — 3 reads · 7 writes).
 * The KYC-upgrade / top-up app-level check is pinned separately in {@code AccountApplicationServiceTest}.
 */
@WebMvcTest(controllers = AccountBusinessRoleGateSliceTest.GateProbe.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class, AccountBusinessRoleGateSliceTest.GateProbe.class})
@DisplayName("account — TASK-MONO-794 business-role gate (real chain, real JWT)")
class AccountBusinessRoleGateSliceTest {

    private static final String ISSUER = "http://iam.local";
    private static final KeyPair KEYS = generateRsaKeyPair();
    private static final String ACC = "/api/finance/accounts/0192ffff-0000-7000-8000-000000000794";

    static final List<String> FINANCE_BUSINESS_ROLES = List.of(
            "FINANCE_ACCOUNT_READ", "FINANCE_ACCOUNT_WRITE", "FINANCE_LEDGER_READ", "FINANCE_LEDGER_WRITE",
            "FINANCE_LEDGER_CONTROL");

    /** The three account reads. */
    static List<MockHttpServletRequestBuilder> reads() {
        return List.of(get(ACC), get(ACC + "/balances"), get(ACC + "/transactions"));
    }

    /** The seven account writes. */
    static List<MockHttpServletRequestBuilder> writes() {
        return List.of(post("/api/finance/accounts"), post(ACC + "/kyc/upgrade"), post(ACC + "/topups"),
                post(ACC + "/holds"), post(ACC + "/holds/h-1/capture"), post(ACC + "/holds/h-1/release"),
                post(ACC + "/transfers"));
    }

    @Autowired
    MockMvc mockMvc;

    @RestController
    static class GateProbe {
        @RequestMapping("/api/finance/**")
        ResponseEntity<Void> any() {
            return ResponseEntity.noContent().build();
        }
    }

    @TestConfiguration
    static class RealJwtChainConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            ServiceLevelOAuth2Config config = new ServiceLevelOAuth2Config();
            ReflectionTestUtils.setField(config, "requiredTenantId", "finance");
            ReflectionTestUtils.setField(config, "allowedIssuersCsv", ISSUER);
            ReflectionTestUtils.setField(config, "jwkSetUri", ISSUER + "/oauth2/jwks");
            NimbusJwtDecoder decoder =
                    NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build();
            decoder.setJwtValidator(config.jwtTokenValidator());
            return decoder;
        }
    }

    // ── reads ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("account read set → every account GET passes the gate")
    void readSet_reads_allowed() throws Exception {
        for (MockHttpServletRequestBuilder r : reads()) {
            expectAllowed(r, roles("FINANCE_ACCOUNT_READ"));
        }
    }

    @Test
    @DisplayName("🔴 AC-1 account read set → every account write 403")
    void readSet_writes_denied() throws Exception {
        for (MockHttpServletRequestBuilder r : writes()) {
            expectDenied(r, roles("FINANCE_ACCOUNT_READ"));
        }
    }

    @Test
    @DisplayName("account write set → every account route passes the gate (write implies read)")
    void writeSet_allowed() throws Exception {
        for (MockHttpServletRequestBuilder r : reads()) {
            expectAllowed(r, roles("FINANCE_ACCOUNT_WRITE"));
        }
        for (MockHttpServletRequestBuilder r : writes()) {
            expectAllowed(r, roles("FINANCE_ACCOUNT_WRITE"));
        }
    }

    @Test
    @DisplayName("🔴 AC-1 ledger sets only (read · write · control) → account reads and writes 403")
    void ledgerSetsOnly_denied() throws Exception {
        Consumer<JWTClaimsSet.Builder> ledger = roles("FINANCE_OPERATOR",
                "FINANCE_LEDGER_READ", "FINANCE_LEDGER_WRITE", "FINANCE_LEDGER_CONTROL");
        for (MockHttpServletRequestBuilder r : reads()) {
            expectDenied(r, ledger);
        }
        for (MockHttpServletRequestBuilder r : writes()) {
            expectDenied(r, ledger);
        }
    }

    @Test
    @DisplayName("🔴 AC-1 FINANCE_OPERATOR (entry role) only → 403 on reads and writes")
    void entryRoleOnly_denied() throws Exception {
        expectDenied(get(ACC), roles("FINANCE_OPERATOR"));
        expectDenied(post("/api/finance/accounts"), roles("FINANCE_OPERATOR"));
    }

    @Test
    @DisplayName("🔴 Failure 1: the never-issued generic OPERATOR / ADMIN / SUPER_ADMIN roles open nothing")
    void genericRoles_denied() throws Exception {
        for (String generic : List.of("OPERATOR", "ADMIN", "SUPER_ADMIN")) {
            expectDenied(get(ACC), roles(generic));
            expectDenied(post(ACC + "/transfers"), roles(generic));
        }
    }

    @Test
    @DisplayName("🔴 subscription alone (entitled_domains ∋ finance, no role, no scope) → account read 403 (FINANCE_VIEWER removed)")
    void entitledWithoutRole_denied() throws Exception {
        expectDenied(get(ACC), c -> c.claim(TenantClaimValidator.CLAIM_TENANT_ID, "acme")
                .claim("entitled_domains", List.of("finance", "wms")));
    }

    @Test
    @DisplayName("platform wildcard (tenant_id='*', no role) → reads pass, writes 403 (unchanged)")
    void wildcard_readOnly() throws Exception {
        Consumer<JWTClaimsSet.Builder> wildcard = c -> c.claim(TenantClaimValidator.CLAIM_TENANT_ID, "*");
        expectAllowed(get(ACC), wildcard);
        expectDenied(post(ACC + "/transfers"), wildcard);
    }

    @Test
    @DisplayName("workload scopes keep working: finance.read → reads only · finance.write → reads and writes")
    void workloadScopes_unchanged() throws Exception {
        Consumer<JWTClaimsSet.Builder> read = c -> c.claim("scope", List.of("finance.read"));
        Consumer<JWTClaimsSet.Builder> write = c -> c.claim("scope", List.of("finance.write"));
        expectAllowed(get(ACC), read);
        expectDenied(post(ACC + "/transfers"), read);
        expectAllowed(get(ACC), write);
        expectAllowed(post(ACC + "/transfers"), write);
    }

    @Test
    @DisplayName("AC-2 legacy-compat operator (FINANCE_OPERATOR + all five roles) → every account route passes")
    void legacyOperator_allowed() throws Exception {
        List<String> legacy = new ArrayList<>(List.of("FINANCE_OPERATOR"));
        legacy.addAll(FINANCE_BUSINESS_ROLES);
        Consumer<JWTClaimsSet.Builder> c = roles(legacy.toArray(String[]::new));
        for (MockHttpServletRequestBuilder r : reads()) {
            expectAllowed(r, c);
        }
        for (MockHttpServletRequestBuilder r : writes()) {
            expectAllowed(r, c);
        }
    }

    /**
     * TASK-MONO-812 — {@code GET /api/finance/accounts/metrics} (the overview «실적» read) sits behind
     * exactly the account read gate: the same authorities admit it, the same ones are refused.
     */
    @Test
    @DisplayName("🔴 812 AC-4: metrics read = the account read gate — read/write roles, scopes, wildcard pass; ledger-only, entry-only, generic, subscription-only 403")
    void metrics_sameGateAsAccountReads() throws Exception {
        String metrics = "/api/finance/accounts/metrics?period=week";
        expectAllowed(get(metrics), roles("FINANCE_OPERATOR", "FINANCE_ACCOUNT_READ"));
        expectAllowed(get(metrics), roles("FINANCE_ACCOUNT_WRITE"));
        expectAllowed(get(metrics), c -> c.claim("scope", List.of("finance.read")));
        // The URL gate still admits the wildcard; TransactionMetricsController then refuses it (TASK-MONO-817,
        // pinned in TransactionMetricsControllerSliceTest). This probe measures the gate only.
        expectAllowed(get(metrics), c -> c.claim(TenantClaimValidator.CLAIM_TENANT_ID, "*"));
        expectDenied(get(metrics), roles("FINANCE_OPERATOR", "FINANCE_LEDGER_READ", "FINANCE_LEDGER_WRITE",
                "FINANCE_LEDGER_CONTROL"));
        expectDenied(get(metrics), roles("FINANCE_OPERATOR"));
        expectDenied(get(metrics), roles("OPERATOR"));
        expectDenied(get(metrics), c -> c.claim(TenantClaimValidator.CLAIM_TENANT_ID, "acme")
                .claim("entitled_domains", List.of("finance")));
    }

    @Test
    @DisplayName("812: metrics read without a token → 401 (never a 200 of zeros)")
    void metrics_noToken_401() throws Exception {
        mockMvc.perform(get("/api/finance/accounts/metrics?period=week"))
                .andExpect(status().isUnauthorized());
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private static Consumer<JWTClaimsSet.Builder> roles(String... roles) {
        return c -> c.claim("roles", List.of(roles));
    }

    private void expectDenied(MockHttpServletRequestBuilder request, Consumer<JWTClaimsSet.Builder> claims)
            throws Exception {
        mockMvc.perform(request.header("Authorization", "Bearer " + token(claims)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    private void expectAllowed(MockHttpServletRequestBuilder request, Consumer<JWTClaimsSet.Builder> claims)
            throws Exception {
        mockMvc.perform(request.header("Authorization", "Bearer " + token(claims)))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("the gate lets it through, body=%s", result.getResponse().getContentAsString())
                        .isEqualTo(204));
    }

    private static String token(Consumer<JWTClaimsSet.Builder> extra) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet.Builder b = new JWTClaimsSet.Builder()
                .subject("console-operator-794")
                .issuer(ISSUER)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .claim(TenantClaimValidator.CLAIM_TENANT_ID, "finance");
        extra.accept(b);
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).build(), b.build());
        jwt.sign(new RSASSASigner((RSAPrivateKey) KEYS.getPrivate()));
        return jwt.serialize();
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("RSA keypair generation failed", e);
        }
    }
}
