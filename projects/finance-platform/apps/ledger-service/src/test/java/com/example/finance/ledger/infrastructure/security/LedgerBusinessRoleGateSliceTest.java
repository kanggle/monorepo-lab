package com.example.finance.ledger.infrastructure.security;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-794 (ADR-MONO-083 P7 · owner decisions Q1–Q2, 2026-10-16 UTC) — ledger-service's business-role gate
 * through the REAL {@link SecurityConfig} chain with really-signed JWTs (finance {@code iam-integration.md}
 * § 업무 역할).
 *
 * <p>The gate is the filter chain alone, so the controllers are replaced by one probe that answers {@code 204} on
 * every {@code /api/finance/**} path: let through = 204, refused = {@code 403 PERMISSION_DENIED}. The paths are the
 * real ledger paths (AC-0 § 2): 16 reads, 6 day-to-day writes, 7 control writes.
 */
@WebMvcTest(controllers = LedgerBusinessRoleGateSliceTest.GateProbe.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class, LedgerBusinessRoleGateSliceTest.GateProbe.class})
@DisplayName("ledger — TASK-MONO-794 business-role gate (real chain, real JWT)")
class LedgerBusinessRoleGateSliceTest {

    private static final String ISSUER = "http://iam.local";
    private static final KeyPair KEYS = generateRsaKeyPair();
    private static final String L = "/api/finance/ledger";

    static final List<String> FINANCE_BUSINESS_ROLES = List.of(
            "FINANCE_ACCOUNT_READ", "FINANCE_ACCOUNT_WRITE", "FINANCE_LEDGER_READ", "FINANCE_LEDGER_WRITE",
            "FINANCE_LEDGER_CONTROL");

    /** The 16 ledger reads. */
    static List<MockHttpServletRequestBuilder> reads() {
        return List.of(
                get(L + "/entries/e-1"), get(L + "/accounts/CASH_CLEARING/entries"),
                get(L + "/accounts/CASH_CLEARING/balance"), get(L + "/trial-balance"),
                get(L + "/periods"), get(L + "/periods/p-1"),
                get(L + "/fx-rates"), get(L + "/fx-rates/USD/history"),
                get(L + "/settlements/cost-flow-config"), get(L + "/settlements/cost-flow-config/accounts"),
                get(L + "/settlements/fx-rate-override/USD"), get(L + "/settlements/FX_POSITION/USD/lots"),
                get(L + "/reconciliation/statements/s-1"), get(L + "/reconciliation/discrepancies"),
                get(L + "/reconciliation/discrepancies/d-1"), get(L + "/reconciliation/fx-tolerance"));
    }

    /** The 6 day-to-day ledger writes — FINANCE_LEDGER_WRITE. */
    static List<MockHttpServletRequestBuilder> businessWrites() {
        return List.of(
                post(L + "/entries"), post(L + "/settlements"), post(L + "/revaluations"),
                post(L + "/reconciliation/statements"), post(L + "/reconciliation/discrepancies/d-1/resolve"),
                post(L + "/fx-rates/refresh"));
    }

    /** The 7 control writes — FINANCE_LEDGER_CONTROL (owner decision Q1). */
    static List<MockHttpServletRequestBuilder> controlWrites() {
        return List.of(
                post(L + "/periods"), post(L + "/periods/p-1/close"),
                put(L + "/settlements/fx-rate-override/USD"), put(L + "/reconciliation/fx-tolerance"),
                put(L + "/settlements/cost-flow-config"),
                put(L + "/settlements/cost-flow-config/accounts/FX_POSITION"),
                delete(L + "/settlements/cost-flow-config/accounts/FX_POSITION"));
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

    @Test
    @DisplayName("ledger read set → all 16 GETs pass; all 13 writes 403")
    void readSet() throws Exception {
        Consumer<JWTClaimsSet.Builder> c = roles("FINANCE_OPERATOR", "FINANCE_LEDGER_READ");
        expectAll(reads(), c, true);
        expectAll(businessWrites(), c, false);
        expectAll(controlWrites(), c, false);
    }

    @Test
    @DisplayName("🔴 Q1 ledger write set (finance-ledger-operate) → reads + 6 business writes pass; the 7 control writes 403")
    void writeSet_noControl() throws Exception {
        Consumer<JWTClaimsSet.Builder> c = roles("FINANCE_OPERATOR", "FINANCE_LEDGER_READ", "FINANCE_LEDGER_WRITE");
        expectAll(reads(), c, true);
        expectAll(businessWrites(), c, true);
        expectAll(controlWrites(), c, false);
    }

    @Test
    @DisplayName("🔴 Q2 ledger control only → reads + the 7 control writes pass; the 6 business writes 403 (independent)")
    void controlOnly_independent() throws Exception {
        Consumer<JWTClaimsSet.Builder> c = roles("FINANCE_OPERATOR", "FINANCE_LEDGER_CONTROL");
        expectAll(reads(), c, true);
        expectAll(controlWrites(), c, true);
        expectAll(businessWrites(), c, false);
    }

    @Test
    @DisplayName("🔴 AC-1 account sets only → every ledger route 403")
    void accountSetsOnly_denied() throws Exception {
        Consumer<JWTClaimsSet.Builder> c = roles("FINANCE_OPERATOR", "FINANCE_ACCOUNT_READ", "FINANCE_ACCOUNT_WRITE");
        expectAll(reads(), c, false);
        expectAll(businessWrites(), c, false);
        expectAll(controlWrites(), c, false);
    }

    @Test
    @DisplayName("🔴 AC-1 FINANCE_OPERATOR (entry role) only → 403")
    void entryRoleOnly_denied() throws Exception {
        expectDenied(get(L + "/trial-balance"), roles("FINANCE_OPERATOR"));
        expectDenied(post(L + "/entries"), roles("FINANCE_OPERATOR"));
        expectDenied(post(L + "/periods/p-1/close"), roles("FINANCE_OPERATOR"));
    }

    @Test
    @DisplayName("🔴 Failure 1: the never-issued generic OPERATOR / ADMIN / SUPER_ADMIN roles open nothing")
    void genericRoles_denied() throws Exception {
        for (String generic : List.of("OPERATOR", "ADMIN", "SUPER_ADMIN")) {
            expectDenied(get(L + "/trial-balance"), roles(generic));
            expectDenied(post(L + "/entries"), roles(generic));
            expectDenied(post(L + "/periods/p-1/close"), roles(generic));
        }
    }

    @Test
    @DisplayName("🔴 subscription alone (entitled_domains ∋ finance, no role, no scope) → ledger read 403 (FINANCE_VIEWER removed)")
    void entitledWithoutRole_denied() throws Exception {
        expectDenied(get(L + "/periods"), c -> c.claim(TenantClaimValidator.CLAIM_TENANT_ID, "acme")
                .claim("entitled_domains", List.of("finance", "wms")));
    }

    @Test
    @DisplayName("platform wildcard (tenant_id='*', no role) → reads pass, writes 403 (unchanged)")
    void wildcard_readOnly() throws Exception {
        Consumer<JWTClaimsSet.Builder> wildcard = c -> c.claim(TenantClaimValidator.CLAIM_TENANT_ID, "*");
        expectAll(reads(), wildcard, true);
        expectDenied(post(L + "/entries"), wildcard);
        expectDenied(post(L + "/periods/p-1/close"), wildcard);
    }

    @Test
    @DisplayName("workload scopes keep working: finance.read → reads only · finance.write → every route")
    void workloadScopes_unchanged() throws Exception {
        Consumer<JWTClaimsSet.Builder> read = c -> c.claim("scope", List.of("finance.read"));
        Consumer<JWTClaimsSet.Builder> write = c -> c.claim("scope", List.of("finance.write"));
        expectAll(reads(), read, true);
        expectDenied(post(L + "/entries"), read);
        expectDenied(post(L + "/periods/p-1/close"), read);
        expectAll(reads(), write, true);
        expectAll(businessWrites(), write, true);
        expectAll(controlWrites(), write, true);
    }

    @Test
    @DisplayName("AC-2 legacy-compat operator (FINANCE_OPERATOR + all five roles) → all 29 ledger routes pass")
    void legacyOperator_allowed() throws Exception {
        List<String> legacy = new ArrayList<>(List.of("FINANCE_OPERATOR"));
        legacy.addAll(FINANCE_BUSINESS_ROLES);
        Consumer<JWTClaimsSet.Builder> c = roles(legacy.toArray(String[]::new));
        expectAll(reads(), c, true);
        expectAll(businessWrites(), c, true);
        expectAll(controlWrites(), c, true);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private static Consumer<JWTClaimsSet.Builder> roles(String... roles) {
        return c -> c.claim("roles", List.of(roles));
    }

    private void expectAll(List<MockHttpServletRequestBuilder> requests, Consumer<JWTClaimsSet.Builder> claims,
                           boolean allowed) throws Exception {
        for (MockHttpServletRequestBuilder r : requests) {
            if (allowed) {
                expectAllowed(r, claims);
            } else {
                expectDenied(r, claims);
            }
        }
    }

    private void expectDenied(MockHttpServletRequestBuilder request, Consumer<JWTClaimsSet.Builder> claims)
            throws Exception {
        mockMvc.perform(request.header("Authorization", "Bearer " + token(claims)))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("%s %s must be refused", result.getRequest().getMethod(),
                                result.getRequest().getRequestURI())
                        .isEqualTo(403))
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    private void expectAllowed(MockHttpServletRequestBuilder request, Consumer<JWTClaimsSet.Builder> claims)
            throws Exception {
        mockMvc.perform(request.header("Authorization", "Bearer " + token(claims)))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("%s %s must pass the gate, body=%s", result.getRequest().getMethod(),
                                result.getRequest().getRequestURI(), result.getResponse().getContentAsString())
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
