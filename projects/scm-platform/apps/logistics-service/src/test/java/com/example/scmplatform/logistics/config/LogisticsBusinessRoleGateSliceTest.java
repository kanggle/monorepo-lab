package com.example.scmplatform.logistics.config;

import com.example.scmplatform.logistics.adapter.inbound.web.advice.GlobalExceptionHandler;
import com.example.scmplatform.logistics.adapter.inbound.web.controller.DispatchController;
import com.example.scmplatform.logistics.application.port.outbound.DispatchPersistencePort;
import com.example.scmplatform.logistics.application.usecase.RetryDispatchUseCase;
import com.example.scmplatform.logistics.domain.error.DispatchNotFoundException;
import com.example.security.oauth2.TenantClaimValidator;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-792 (ADR-MONO-083 P5 · owner decision Q3 — strict) — logistics' business-role gate
 * through the real {@link SecurityConfig} chain with really-signed JWTs.
 *
 * <p>The use cases are mocked to «not found», so a request the gate lets through ends in a
 * non-auth status (404); a refusal is {@code 403 PERMISSION_DENIED}.
 */
@WebMvcTest(DispatchController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "scmplatform.test.slice=logistics-business-role-gate")
@DisplayName("logistics — TASK-MONO-792 business-role gate (real chain, real JWT)")
class LogisticsBusinessRoleGateSliceTest {

    private static final String ISSUER = "http://iam.local";
    private static final UUID DISPATCH_ID = UUID.fromString("0192dddd-0000-0000-0000-000000000792");
    private static final String GET_PATH = "/api/logistics/dispatches/" + DISPATCH_ID;
    private static final String BY_SHIPMENT = "/api/logistics/dispatches/by-shipment/" + DISPATCH_ID;
    private static final String RETRY_PATH = "/api/logistics/dispatches/" + DISPATCH_ID + ":retry";
    private static final KeyPair KEYS = generateRsaKeyPair();

    static final List<String> SCM_BUSINESS_ROLES = List.of(
            "SCM_PROCUREMENT_READ", "SCM_PROCUREMENT_WRITE",
            "SCM_DEMAND_PLANNING_READ", "SCM_DEMAND_PLANNING_WRITE",
            "SCM_LOGISTICS_READ", "SCM_LOGISTICS_WRITE",
            "SCM_INVENTORY_VISIBILITY_READ", "SCM_INVENTORY_VISIBILITY_WRITE");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    DispatchPersistencePort persistencePort;

    @MockitoBean
    RetryDispatchUseCase retryDispatchUseCase;

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

    @BeforeEach
    void notFound() {
        when(retryDispatchUseCase.retry(any())).thenThrow(new DispatchNotFoundException(DISPATCH_ID));
    }

    @Test
    @DisplayName("logistics read → both GETs pass the gate")
    void readSet_get_allowed() throws Exception {
        expectAllowed(get(GET_PATH), List.of("SCM_LOGISTICS_READ"));
        expectAllowed(get(BY_SHIPMENT), List.of("SCM_LOGISTICS_READ"));
    }

    @Test
    @DisplayName("🔴 AC-1 logistics read → retry 403")
    void readSet_retry_denied() throws Exception {
        expectDenied(post(RETRY_PATH), List.of("SCM_LOGISTICS_READ"));
    }

    @Test
    @DisplayName("logistics write → retry passes the gate")
    void writeSet_retry_allowed() throws Exception {
        expectAllowed(post(RETRY_PATH), List.of("SCM_LOGISTICS_WRITE"));
    }

    @Test
    @DisplayName("🔴 AC-1 SCM_OPERATOR only → 403")
    void entryRoleOnly_denied() throws Exception {
        expectDenied(get(GET_PATH), List.of("SCM_OPERATOR"));
        expectDenied(post(RETRY_PATH), List.of("SCM_OPERATOR"));
    }

    @Test
    @DisplayName("🔴 other SCM business sets (procurement + demand-planning) → logistics 403")
    void otherScmSets_denied() throws Exception {
        List<String> other = List.of("SCM_PROCUREMENT_READ", "SCM_PROCUREMENT_WRITE",
                "SCM_DEMAND_PLANNING_READ", "SCM_DEMAND_PLANNING_WRITE");
        expectDenied(get(BY_SHIPMENT), other);
        expectDenied(post(RETRY_PATH), other);
    }

    @Test
    @DisplayName("🔴 AC-3 / Q3 WMS outbound set only → dispatch lookup and retry 403 (strict — no cross-domain acceptance)")
    void wmsOutboundSetOnly_denied() throws Exception {
        List<String> outbound = List.of("OUTBOUND_READ", "OUTBOUND_WRITE");
        expectDenied(get(BY_SHIPMENT), outbound);
        expectDenied(post(RETRY_PATH), outbound);
    }

    @Test
    @DisplayName("AC-2 legacy-compat operator → every logistics route passes the gate")
    void legacyOperator_allowed() throws Exception {
        List<String> legacy = new ArrayList<>(List.of("SCM_OPERATOR"));
        legacy.addAll(SCM_BUSINESS_ROLES);
        expectAllowed(get(GET_PATH), legacy);
        expectAllowed(get(BY_SHIPMENT), legacy);
        expectAllowed(post(RETRY_PATH), legacy);
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    private void expectDenied(MockHttpServletRequestBuilder request, List<String> roles) throws Exception {
        mockMvc.perform(request.header("Authorization", "Bearer " + token(roles)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    private void expectAllowed(MockHttpServletRequestBuilder request, List<String> roles) throws Exception {
        mockMvc.perform(request.header("Authorization", "Bearer " + token(roles)))
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
