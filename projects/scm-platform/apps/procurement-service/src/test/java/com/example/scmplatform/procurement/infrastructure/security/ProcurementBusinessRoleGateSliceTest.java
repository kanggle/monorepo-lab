package com.example.scmplatform.procurement.infrastructure.security;

import com.example.scmplatform.procurement.application.PurchaseOrderApplicationService;
import com.example.scmplatform.procurement.presentation.advice.GlobalExceptionHandler;
import com.example.scmplatform.procurement.presentation.controller.SupplierAckWebhookController;
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
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-792 (ADR-MONO-083 P5) — procurement's business-role gate, driven through the
 * <strong>real</strong> {@link SecurityConfig} chain with really-signed JWTs (the
 * {@code ActorContextAuthPathSliceTest} harness).
 *
 * <p>The slice deliberately loads only {@link SupplierAckWebhookController} (a public webhook path),
 * so no PO / supplier handler exists: a request the gate <em>lets through</em> ends in a non-auth
 * status (404/405 — no handler), a request the gate <em>refuses</em> ends in
 * {@code 403 PERMISSION_DENIED}. The assertion is therefore about the gate alone, never about a
 * mocked use case.
 *
 * <p>Canonical table: scm {@code iam-integration.md} § 업무 역할.
 */
@WebMvcTest(SupplierAckWebhookController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "scmplatform.test.slice=procurement-business-role-gate")
@DisplayName("procurement — TASK-MONO-792 business-role gate (real chain, real JWT)")
class ProcurementBusinessRoleGateSliceTest {

    private static final String ISSUER = "http://iam.local";
    private static final KeyPair KEYS = generateRsaKeyPair();

    static final List<String> SCM_BUSINESS_ROLES = List.of(
            "SCM_PROCUREMENT_READ", "SCM_PROCUREMENT_WRITE",
            "SCM_DEMAND_PLANNING_READ", "SCM_DEMAND_PLANNING_WRITE",
            "SCM_LOGISTICS_READ", "SCM_LOGISTICS_WRITE",
            "SCM_INVENTORY_VISIBILITY_READ", "SCM_INVENTORY_VISIBILITY_WRITE");

    /** What auth issues to a backfilled (legacy-compat) operator after TASK-MONO-792: entry + every business role. */
    static final List<String> LEGACY_OPERATOR_ROLES = concat(List.of("SCM_OPERATOR"), SCM_BUSINESS_ROLES);

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PurchaseOrderApplicationService service;

    @TestConfiguration
    static class RealJwtChainConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            ServiceLevelOAuth2Config config = new ServiceLevelOAuth2Config();
            ReflectionTestUtils.setField(config, "requiredTenantId", "scm");
            ReflectionTestUtils.setField(config, "allowedIssuersCsv", ISSUER);
            ReflectionTestUtils.setField(config, "jwkSetUri", ISSUER + "/oauth2/jwks");
            NimbusJwtDecoder decoder =
                    NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build();
            decoder.setJwtValidator(config.jwtTokenValidator());
            return decoder;
        }
    }

    // ── AC-1: demand-planning set only ────────────────────────────────────────────

    @Test
    @DisplayName("🔴 AC-1 demand-planning set only → PO create 403")
    void demandPlanningSet_poCreate_denied() throws Exception {
        expectDenied(post("/api/procurement/po"), List.of("SCM_DEMAND_PLANNING_READ", "SCM_DEMAND_PLANNING_WRITE"));
    }

    @Test
    @DisplayName("🔴 AC-1 demand-planning set only → PO confirm 403")
    void demandPlanningSet_poConfirm_denied() throws Exception {
        expectDenied(post("/api/procurement/po/po-1/confirm"),
                List.of("SCM_DEMAND_PLANNING_READ", "SCM_DEMAND_PLANNING_WRITE"));
    }

    @Test
    @DisplayName("🔴 AC-1 demand-planning set only → PO read 403")
    void demandPlanningSet_poRead_denied() throws Exception {
        expectDenied(get("/api/procurement/po/po-1"),
                List.of("SCM_DEMAND_PLANNING_READ", "SCM_DEMAND_PLANNING_WRITE"));
    }

    @Test
    @DisplayName("AC-1 (Q2) demand-planning write → POST /po/from-suggestion passes the gate (DRAFT only)")
    void demandPlanningWrite_fromSuggestion_allowed() throws Exception {
        expectAllowed(post("/api/procurement/po/from-suggestion"), List.of("SCM_DEMAND_PLANNING_WRITE"));
    }

    @Test
    @DisplayName("🔴 Q2 demand-planning READ alone → from-suggestion 403 (write role only)")
    void demandPlanningRead_fromSuggestion_denied() throws Exception {
        expectDenied(post("/api/procurement/po/from-suggestion"), List.of("SCM_DEMAND_PLANNING_READ"));
    }

    // ── AC-1: read set ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-1 procurement read → GET /po passes the gate")
    void readSet_get_allowed() throws Exception {
        expectAllowed(get("/api/procurement/po"), List.of("SCM_PROCUREMENT_READ"));
        expectAllowed(get("/api/procurement/suppliers"), List.of("SCM_PROCUREMENT_READ"));
    }

    @Test
    @DisplayName("🔴 AC-1 procurement read → submit / cancel / supplier registration 403")
    void readSet_writes_denied() throws Exception {
        expectDenied(post("/api/procurement/po/po-1/submit"), List.of("SCM_PROCUREMENT_READ"));
        expectDenied(post("/api/procurement/po/po-1/cancel"), List.of("SCM_PROCUREMENT_READ"));
        expectDenied(post("/api/procurement/suppliers"), List.of("SCM_PROCUREMENT_READ"));
    }

    // ── TASK-MONO-810: GET /po/metrics — the same gate as every other GET ─────────

    @Test
    @DisplayName("MONO-810 procurement read (or write) → GET /po/metrics passes the gate")
    void metrics_readOrWrite_allowed() throws Exception {
        expectAllowed(get("/api/procurement/po/metrics").param("period", "week"), List.of("SCM_PROCUREMENT_READ"));
        expectAllowed(get("/api/procurement/po/metrics").param("period", "week"), List.of("SCM_PROCUREMENT_WRITE"));
        expectAllowed(get("/api/procurement/po").param("submittedFrom", "2026-10-18T15:00:00Z"),
                List.of("SCM_PROCUREMENT_READ"));
    }

    @Test
    @DisplayName("🔴 MONO-810 no procurement role (entry role / demand-planning / WMS) → GET /po/metrics 403 PERMISSION_DENIED")
    void metrics_noRole_denied() throws Exception {
        expectDenied(get("/api/procurement/po/metrics").param("period", "week"), List.of("SCM_OPERATOR"));
        expectDenied(get("/api/procurement/po/metrics").param("period", "week"),
                List.of("SCM_DEMAND_PLANNING_READ", "SCM_DEMAND_PLANNING_WRITE"));
        expectDenied(get("/api/procurement/po/metrics").param("period", "week"), List.of("WMS_OPERATOR"));
    }

    @Test
    @DisplayName("🔴 MONO-810 GET /po/metrics: no token → 401 UNAUTHORIZED; foreign tenant → 403 TENANT_FORBIDDEN")
    void metrics_unauthenticatedAndForeignTenant() throws Exception {
        mockMvc.perform(get("/api/procurement/po/metrics").param("period", "week"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/procurement/po/metrics").param("period", "week")
                        .header("Authorization", "Bearer " + token(List.of("SCM_PROCUREMENT_READ"), "wms")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_FORBIDDEN"));
    }

    // ── AC-1: entry role alone ────────────────────────────────────────────────────

    @Test
    @DisplayName("🔴 AC-1 SCM_OPERATOR only (no business role) → read and write 403")
    void entryRoleOnly_denied() throws Exception {
        expectDenied(get("/api/procurement/po/po-1"), List.of("SCM_OPERATOR"));
        expectDenied(post("/api/procurement/po"), List.of("SCM_OPERATOR"));
    }

    @Test
    @DisplayName("🔴 generic OPERATOR role alone opens no business API (actor model only)")
    void genericOperatorOnly_denied() throws Exception {
        expectDenied(get("/api/procurement/po/po-1"), List.of("OPERATOR"));
    }

    // ── AC-2: the legacy (backfilled) operator ────────────────────────────────────

    @Test
    @DisplayName("AC-2 legacy-compat operator (entry + every SCM business role) → every procurement API passes")
    void legacyOperator_allAllowed() throws Exception {
        expectAllowed(get("/api/procurement/po/po-1"), LEGACY_OPERATOR_ROLES);
        expectAllowed(post("/api/procurement/po"), LEGACY_OPERATOR_ROLES);
        expectAllowed(post("/api/procurement/po/from-suggestion"), LEGACY_OPERATOR_ROLES);
        expectAllowed(post("/api/procurement/po/po-1/submit"), LEGACY_OPERATOR_ROLES);
        expectAllowed(post("/api/procurement/po/po-1/confirm"), LEGACY_OPERATOR_ROLES);
        expectAllowed(post("/api/procurement/po/po-1/cancel"), LEGACY_OPERATOR_ROLES);
        expectAllowed(post("/api/procurement/suppliers"), LEGACY_OPERATOR_ROLES);
        expectAllowed(get("/api/procurement/suppliers"), LEGACY_OPERATOR_ROLES);
    }

    // ── AC-3: cross-domain ────────────────────────────────────────────────────────

    @Test
    @DisplayName("🔴 AC-3 WMS roles only → SCM procurement 403")
    void wmsRolesOnly_denied() throws Exception {
        List<String> wms = List.of("WMS_OPERATOR", "OUTBOUND_READ", "OUTBOUND_WRITE",
                "INBOUND_READ", "INBOUND_WRITE", "INVENTORY_READ", "INVENTORY_WRITE", "MASTER_READ");
        expectDenied(get("/api/procurement/po/po-1"), wms);
        expectDenied(post("/api/procurement/po"), wms);
    }

    // ── unchanged edges ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("webhook prefix stays public — no token, never 401/403")
    void webhookStaysPublic() throws Exception {
        mockMvc.perform(get("/api/procurement/webhooks/asn"))
                .andExpect(notAnAuthRefusal());
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    private void expectDenied(MockHttpServletRequestBuilder request, List<String> roles) throws Exception {
        mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header("Authorization", "Bearer " + token(roles)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    private void expectAllowed(MockHttpServletRequestBuilder request, List<String> roles) throws Exception {
        mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header("Authorization", "Bearer " + token(roles)))
                .andExpect(notAnAuthRefusal());
    }

    private static ResultMatcher notAnAuthRefusal() {
        return result -> assertThat(result.getResponse().getStatus())
                .as("the gate lets it through (no handler in this slice → 404/405), body=%s",
                        result.getResponse().getContentAsString())
                .isNotIn(401, 403);
    }

    private static String token(List<String> roles) throws Exception {
        return token(roles, "scm");
    }

    private static String token(List<String> roles, String tenantId) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("console-operator-792")
                .issuer(ISSUER)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .claim(TenantClaimValidator.CLAIM_TENANT_ID, tenantId)
                .claim("roles", roles)
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).build(), claims);
        jwt.sign(new RSASSASigner((RSAPrivateKey) KEYS.getPrivate()));
        return jwt.serialize();
    }

    private static List<String> concat(List<String> a, List<String> b) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>(a);
        out.addAll(b);
        return List.copyOf(out);
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
