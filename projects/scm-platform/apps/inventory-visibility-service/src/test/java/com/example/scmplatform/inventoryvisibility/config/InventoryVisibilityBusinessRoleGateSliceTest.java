package com.example.scmplatform.inventoryvisibility.config;

import com.example.scmplatform.inventoryvisibility.adapter.inbound.web.NodeStalenessController;
import com.example.scmplatform.inventoryvisibility.adapter.inbound.web.advice.GlobalExceptionHandler;
import com.example.scmplatform.inventoryvisibility.application.service.InventoryVisibilityApplicationService;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-792 (ADR-MONO-083 P5 · owner decision Q1) — inventory-visibility's business-role gate
 * through the real {@link SecurityConfig} chain with really-signed JWTs.
 *
 * <p>Only {@link NodeStalenessController} is in the slice; the probed snapshot / node routes have
 * no handler here, so a pass through the gate is a non-auth status (404/405) and a refusal is
 * {@code 403 PERMISSION_DENIED}. {@code /internal/**} stays network-trusted (no JWT).
 */
@WebMvcTest(NodeStalenessController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "scmplatform.test.slice=inventory-visibility-business-role-gate")
@DisplayName("inventory-visibility — TASK-MONO-792 business-role gate (real chain, real JWT)")
class InventoryVisibilityBusinessRoleGateSliceTest {

    private static final String ISSUER = "http://iam.local";
    private static final KeyPair KEYS = generateRsaKeyPair();

    static final List<String> SCM_BUSINESS_ROLES = List.of(
            "SCM_PROCUREMENT_READ", "SCM_PROCUREMENT_WRITE",
            "SCM_DEMAND_PLANNING_READ", "SCM_DEMAND_PLANNING_WRITE",
            "SCM_LOGISTICS_READ", "SCM_LOGISTICS_WRITE",
            "SCM_INVENTORY_VISIBILITY_READ", "SCM_INVENTORY_VISIBILITY_WRITE");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    InventoryVisibilityApplicationService applicationService;

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

    @Test
    @DisplayName("AC-1 visibility read → snapshot / sku / nodes GET pass the gate")
    void readSet_get_allowed() throws Exception {
        List<String> read = List.of("SCM_INVENTORY_VISIBILITY_READ");
        expectAllowed(get("/api/inventory-visibility/snapshot"), read);
        expectAllowed(get("/api/inventory-visibility/sku/SKU-001"), read);
        expectAllowed(get("/api/inventory-visibility/nodes"), read);
    }

    @Test
    @DisplayName("🔴 AC-1 / Q1 visibility read → 3PL node registration and observed-stock 403")
    void readSet_writes_denied() throws Exception {
        List<String> read = List.of("SCM_INVENTORY_VISIBILITY_READ");
        expectDenied(post("/api/inventory-visibility/nodes"), read);
        expectDenied(post("/api/inventory-visibility/nodes/n-1/observed-stock"), read);
    }

    @Test
    @DisplayName("Q1 visibility write → both mutations pass the gate")
    void writeSet_writes_allowed() throws Exception {
        List<String> write = List.of("SCM_INVENTORY_VISIBILITY_WRITE");
        expectAllowed(post("/api/inventory-visibility/nodes"), write);
        expectAllowed(post("/api/inventory-visibility/nodes/n-1/observed-stock"), write);
    }

    @Test
    @DisplayName("🔴 AC-1 SCM_OPERATOR only → 403")
    void entryRoleOnly_denied() throws Exception {
        expectDenied(get("/api/inventory-visibility/snapshot"), List.of("SCM_OPERATOR"));
    }

    @Test
    @DisplayName("🔴 AC-1 demand-planning set only → visibility 403")
    void demandPlanningSet_denied() throws Exception {
        expectDenied(get("/api/inventory-visibility/snapshot"),
                List.of("SCM_DEMAND_PLANNING_READ", "SCM_DEMAND_PLANNING_WRITE"));
    }

    @Test
    @DisplayName("🔴 AC-3 WMS inventory set only → SCM visibility 403")
    void wmsInventorySet_denied() throws Exception {
        expectDenied(get("/api/inventory-visibility/snapshot"), List.of("INVENTORY_READ", "INVENTORY_WRITE"));
    }

    @Test
    @DisplayName("AC-2 legacy-compat operator → every visibility route passes the gate")
    void legacyOperator_allowed() throws Exception {
        List<String> legacy = new ArrayList<>(List.of("SCM_OPERATOR"));
        legacy.addAll(SCM_BUSINESS_ROLES);
        expectAllowed(get("/api/inventory-visibility/snapshot"), legacy);
        expectAllowed(get("/api/inventory-visibility/nodes"), legacy);
        expectAllowed(post("/api/inventory-visibility/nodes"), legacy);
        expectAllowed(post("/api/inventory-visibility/nodes/n-1/observed-stock"), legacy);
    }

    @Test
    @DisplayName("/internal/** stays network-trusted — no token, never 401/403")
    void internalStaysOpen() throws Exception {
        mockMvc.perform(get("/internal/inventory-visibility/snapshot")).andExpect(notAnAuthRefusal());
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
                .as("the gate lets it through, body=%s", result.getResponse().getContentAsString())
                .isNotIn(401, 403);
    }

    private static String token(List<String> roles) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("console-operator-792")
                .issuer(ISSUER)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .claim(TenantClaimValidator.CLAIM_TENANT_ID, "scm")
                .claim("roles", roles)
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).build(), claims);
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
