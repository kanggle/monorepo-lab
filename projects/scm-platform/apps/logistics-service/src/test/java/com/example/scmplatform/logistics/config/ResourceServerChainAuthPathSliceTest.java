package com.example.scmplatform.logistics.config;

import com.example.scmplatform.logistics.adapter.inbound.web.advice.GlobalExceptionHandler;
import com.example.scmplatform.logistics.adapter.inbound.web.controller.DispatchController;
import com.example.scmplatform.logistics.application.port.outbound.DispatchPersistencePort;
import com.example.scmplatform.logistics.application.usecase.RetryDispatchUseCase;
import com.example.scmplatform.logistics.domain.model.Dispatch;
import com.example.scmplatform.logistics.domain.model.ShipmentId;
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
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <strong>logistics' auth path, driven end-to-end</strong> — TASK-SCM-BE-054 (ADR-MONO-058 § D4).
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
 * {@code DispatchControllerSliceTest}, which imports the same {@link SecurityConfig}.
 */
@WebMvcTest(DispatchController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "scmplatform.test.slice=logistics-auth-path")
@DisplayName("logistics — the resource-server chain, driven through real JWTs")
class ResourceServerChainAuthPathSliceTest {

    private static final String ISSUER = "http://iam.local";
    private static final UUID DISPATCH_ID =
            UUID.fromString("0192dddd-0000-0000-0000-000000000001");
    private static final String API_PATH = "/api/logistics/dispatches/" + DISPATCH_ID;

    private static final KeyPair KEYS = generateRsaKeyPair();

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    DispatchPersistencePort persistencePort;

    @MockitoBean
    RetryDispatchUseCase retryDispatchUseCase;

    /**
     * The real decoder + the real validator chain, keyed on this class's RSA keypair.
     *
     * <p>{@link ServiceLevelOAuth2Config} is asked for its own {@code jwtTokenValidator()}, so
     * deleting {@code .allowSuperAdminWildcard()}, {@code .trustEntitledDomains()} or the issuer
     * allow-list from that class turns this suite red. A chain assembled here instead would be
     * asserting a chain this test wrote itself.
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
        when(persistencePort.findById(DISPATCH_ID)).thenReturn(Optional.of(dispatch()));

        mockMvc.perform(get(API_PATH).header("Authorization", bearer(token("operator-001", "scm"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(DISPATCH_ID.toString()));
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
     * The {@code anyRequest()} tail, pinned. logistics ends {@code denyAll()}, measured against the
     * tree before the D4 adoption and preserved by it — so an authenticated caller on a path
     * outside the permit/authenticate lists is refused, not merely 404'd. Flipping the tail to
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

    private static Dispatch dispatch() {
        return Dispatch.create(DISPATCH_ID, ShipmentId.of(UUID.randomUUID()), "SHP-001",
                UUID.randomUUID(), "ORD-001", "scm", Instant.now());
    }

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
