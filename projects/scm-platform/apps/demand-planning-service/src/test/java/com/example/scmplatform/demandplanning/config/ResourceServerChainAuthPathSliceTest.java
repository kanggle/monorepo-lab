package com.example.scmplatform.demandplanning.config;

import com.example.scmplatform.demandplanning.adapter.inbound.web.advice.GlobalExceptionHandler;
import com.example.scmplatform.demandplanning.adapter.inbound.web.controller.PolicyController;
import com.example.scmplatform.demandplanning.application.usecase.PolicyManagementUseCase;
import com.example.scmplatform.demandplanning.domain.model.ReorderPolicy;
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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <strong>demand-planning's auth path, driven end-to-end</strong> — TASK-SCM-BE-054
 * (ADR-MONO-058 § D4).
 *
 * <h2>Why this is not a unit test</h2>
 *
 * An {@code anyRequest()} tail that quietly flipped from {@code denyAll()} to
 * {@code authenticated()}, a permit list that stopped being applied, or a resource-server wiring
 * that lost its entry point all pass every unit test in this module and change who can call this
 * service. So the request goes through the <strong>real</strong> {@link SecurityConfig} filter
 * chain — now assembled by {@code ResourceServerChainAssembler} — carrying a <strong>really
 * RSA-signed</strong> JWT verified by a <strong>real</strong> {@link NimbusJwtDecoder} whose
 * validator chain is the one {@link ServiceLevelOAuth2Config} builds in production.
 *
 * <p>The decoder is keyed on a locally-generated RSA keypair rather than a JWKS endpoint: the JWKS
 * fetch is transport, not policy. Every decision this test is about — signature verification,
 * issuer allow-list, tenant gate, path routing, the chain tail — runs for real.
 *
 * <p>The distinct {@link TestPropertySource} is a deliberate context-cache key, so this class
 * cannot end up sharing a cached context (and therefore a {@link JwtDecoder}) with
 * {@code PolicyControllerSliceTest}, which imports the same {@link SecurityConfig}.
 */
@WebMvcTest(PolicyController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "scmplatform.test.slice=demand-planning-auth-path")
@DisplayName("demand-planning — the resource-server chain, driven through real JWTs")
class ResourceServerChainAuthPathSliceTest {

    private static final String ISSUER = "http://iam.local";
    private static final String API_PATH = "/api/demand-planning/policies/SKU-001";

    private static final KeyPair KEYS = generateRsaKeyPair();

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PolicyManagementUseCase policyManagementUseCase;

    /**
     * The real decoder + the real validator chain, keyed on this class's RSA keypair.
     *
     * <p>{@link ServiceLevelOAuth2Config} is asked for its own {@code jwtTokenValidator()}, so
     * deleting {@code .allowSuperAdminWildcard()}, {@code .trustEntitledDomains()} or the issuer
     * allow-list from that class turns this suite red.
     */
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
    @DisplayName("a really-signed in-tenant token reaches the controller — 200")
    void inTenantTokenReachesTheController() throws Exception {
        when(policyManagementUseCase.getPolicy("SKU-001"))
                .thenReturn(new ReorderPolicy("SKU-001", 10, 5, 100, "scm", 0, Instant.now()));

        mockMvc.perform(get(API_PATH).header("Authorization", bearer(token("operator-001", "scm"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skuCode").value("SKU-001"));
    }

    @Test
    @DisplayName("no bearer token → 401 UNAUTHORIZED")
    void noTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get(API_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("cross-tenant token → 403 TENANT_FORBIDDEN (never 401, never 200)")
    void crossTenantTokenIsForbidden() throws Exception {
        mockMvc.perform(get(API_PATH).header("Authorization", bearer(token("operator-001", "wms"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_FORBIDDEN"));
    }

    /**
     * The {@code anyRequest()} tail, pinned. demand-planning ends {@code denyAll()}, measured
     * against the tree before the D4 adoption and preserved by it. Flipping the tail to
     * {@code anyRequestAuthenticated()} turns this 403 into a 404.
     */
    @Test
    @DisplayName("authenticated caller on an unlisted path → 403 (the denyAll tail, not 404)")
    void authenticatedCallerOnUnlistedPathIsDenied() throws Exception {
        mockMvc.perform(get("/api/somewhere-else")
                        .header("Authorization", bearer(token("operator-001", "scm"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("actuator probe is permitted unauthenticated (404 from the slice, never 401)")
    void publicActuatorProbeIsPermitted() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isNotFound());
    }

    // =====================================================================================
    // helpers
    // =====================================================================================

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    /** Signs a real RS256 JWT with this class's private key. */
    private static String token(String subject, String tenantId) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(ISSUER)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .claim(TenantClaimValidator.CLAIM_TENANT_ID, tenantId)
                .claim("roles", List.of("OPERATOR"))
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).build(), claims);
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
