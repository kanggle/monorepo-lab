package com.example.scmplatform.demandplanning.config;

import com.example.scmplatform.demandplanning.adapter.inbound.web.advice.GlobalExceptionHandler;
import com.example.scmplatform.demandplanning.adapter.inbound.web.controller.PolicyController;
import com.example.scmplatform.demandplanning.application.usecase.PolicyManagementUseCase;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-792 (ADR-MONO-083 P5) — demand-planning's business-role gate through the real
 * {@link SecurityConfig} chain with really-signed JWTs.
 *
 * <p>Only {@link PolicyController} is in the slice. «Allowed» probes target the suggestion routes,
 * whose controller is NOT loaded — the gate's pass shows as a non-auth status (404/405, no handler);
 * a refusal is {@code 403 PERMISSION_DENIED}. Refusal probes may target any route (the gate runs
 * before dispatch).
 */
@WebMvcTest(PolicyController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "scmplatform.test.slice=demand-planning-business-role-gate")
@DisplayName("demand-planning — TASK-MONO-792 business-role gate (real chain, real JWT)")
class DemandPlanningBusinessRoleGateSliceTest {

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
    PolicyManagementUseCase policyManagementUseCase;

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
    @DisplayName("AC-1 demand-planning set → read and approve pass the gate")
    void demandPlanningSet_allowed() throws Exception {
        List<String> set = List.of("SCM_DEMAND_PLANNING_READ", "SCM_DEMAND_PLANNING_WRITE");
        expectAllowed(get("/api/demand-planning/suggestions"), set);
        expectAllowed(post("/api/demand-planning/suggestions/s-1/approve"), set);
        expectAllowed(post("/api/demand-planning/suggestions/s-1/dismiss"), set);
    }

    @Test
    @DisplayName("AC-1 demand-planning read → GET passes the gate")
    void readSet_get_allowed() throws Exception {
        expectAllowed(get("/api/demand-planning/suggestions/s-1"), List.of("SCM_DEMAND_PLANNING_READ"));
    }

    @Test
    @DisplayName("🔴 AC-1 demand-planning read → approve / dismiss / policy PUT / mapping PUT 403")
    void readSet_writes_denied() throws Exception {
        List<String> read = List.of("SCM_DEMAND_PLANNING_READ");
        expectDenied(post("/api/demand-planning/suggestions/s-1/approve"), read);
        expectDenied(post("/api/demand-planning/suggestions/s-1/dismiss"), read);
        expectDenied(put("/api/demand-planning/policies/SKU-001"), read);
        expectDenied(put("/api/demand-planning/sku-supplier-map/SKU-001"), read);
    }

    @Test
    @DisplayName("🔴 procurement set only → demand-planning read and write 403")
    void procurementSet_denied() throws Exception {
        List<String> procurement = List.of("SCM_PROCUREMENT_READ", "SCM_PROCUREMENT_WRITE");
        expectDenied(get("/api/demand-planning/suggestions"), procurement);
        expectDenied(post("/api/demand-planning/suggestions/s-1/approve"), procurement);
    }

    @Test
    @DisplayName("🔴 AC-1 SCM_OPERATOR only → 403")
    void entryRoleOnly_denied() throws Exception {
        expectDenied(get("/api/demand-planning/suggestions"), List.of("SCM_OPERATOR"));
        expectDenied(post("/api/demand-planning/suggestions/s-1/approve"), List.of("SCM_OPERATOR"));
    }

    @Test
    @DisplayName("AC-2 legacy-compat operator → every demand-planning route passes the gate")
    void legacyOperator_allowed() throws Exception {
        List<String> legacy = new ArrayList<>(List.of("SCM_OPERATOR"));
        legacy.addAll(SCM_BUSINESS_ROLES);
        expectAllowed(get("/api/demand-planning/suggestions"), legacy);
        expectAllowed(get("/api/demand-planning/suggestions/s-1"), legacy);
        expectAllowed(post("/api/demand-planning/suggestions/s-1/approve"), legacy);
        expectAllowed(post("/api/demand-planning/suggestions/s-1/dismiss"), legacy);
    }

    @Test
    @DisplayName("🔴 AC-3 WMS roles only → 403")
    void wmsRolesOnly_denied() throws Exception {
        List<String> wms = List.of("WMS_OPERATOR", "OUTBOUND_READ", "OUTBOUND_WRITE", "INVENTORY_READ");
        expectDenied(get("/api/demand-planning/suggestions"), wms);
        expectDenied(put("/api/demand-planning/policies/SKU-001"), wms);
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
