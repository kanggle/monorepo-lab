package com.example.scmplatform.procurement.infrastructure.security;

import com.example.scmplatform.procurement.application.ActorContext;
import com.example.scmplatform.procurement.application.IdempotencyExecutor;
import com.example.scmplatform.procurement.application.IdempotencyHasher;
import com.example.scmplatform.procurement.application.PurchaseOrderApplicationService;
import com.example.scmplatform.procurement.application.PurchaseOrderView;
import com.example.scmplatform.procurement.domain.po.PoOrigin;
import com.example.scmplatform.procurement.domain.po.status.ActorType;
import com.example.scmplatform.procurement.domain.po.status.PoStatus;
import com.example.scmplatform.procurement.presentation.advice.GlobalExceptionHandler;
import com.example.scmplatform.procurement.presentation.controller.PurchaseOrderController;
import com.example.security.oauth2.TenantClaimValidator;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <strong>procurement's auth path, driven end-to-end</strong> — TASK-SCM-BE-054
 * (ADR-MONO-058 § D1 + § D4).
 *
 * <h2>Why this is not a unit test</h2>
 *
 * A converter that lifts claims correctly but is never wired, a resolver that reads a principal the
 * chain never installs, or an {@code anyRequest()} tail that quietly flipped from {@code denyAll()}
 * to {@code authenticated()} all pass every unit test in this module. So nothing here is
 * hand-constructed: the request goes through the <strong>real</strong> {@link SecurityConfig} filter
 * chain, carrying a <strong>really RSA-signed</strong> JWT, verified by a <strong>real</strong>
 * {@link NimbusJwtDecoder} whose validator chain is the one {@link ServiceLevelOAuth2Config} builds
 * in production (real {@code AllowedIssuersValidator}, real {@link TenantClaimValidator}).
 *
 * <p>The actor and the authorities are read off the <strong>live {@code SecurityContext} at
 * controller-invocation time</strong> (a Mockito {@code Answer} on the use case captures both), so
 * every assertion below is about the running chain rather than about a fixture.
 *
 * <p>The decoder is keyed on a locally-generated RSA keypair rather than a JWKS endpoint: the JWKS
 * fetch is transport, not policy, and stubbing it would add a socket without adding a claim. Every
 * decision this test is about — signature verification, issuer allow-list, tenant gate, claim
 * lifting, authority prefixing, path routing — runs for real.
 *
 * <p>The distinct {@link TestPropertySource} is a deliberate context-cache key: the other slice
 * tests in this module run with {@code addFilters = false} and no {@link JwtDecoder}, and sharing a
 * cached context with them would silently decide which decoder this class verifies against.
 */
@WebMvcTest(PurchaseOrderController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "scmplatform.test.slice=procurement-actor-auth-path")
@DisplayName("procurement — the actor/JWT auth path through the real filter chain")
class ActorContextAuthPathSliceTest {

    private static final String ISSUER = "http://iam.local";
    private static final String PO_PATH = "/api/procurement/po/po-1";

    /** scm's documented machine caller — {@code sub == client_id}, 37 chars (iam-integration E1). */
    private static final String CLIENT_CREDENTIALS_SUB = "scm-platform-internal-services-client";

    private static final KeyPair KEYS = generateRsaKeyPair();

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PurchaseOrderApplicationService service;

    @MockitoBean
    IdempotencyExecutor idempotency;

    @MockitoBean
    IdempotencyHasher hasher;

    private ActorContext capturedActor;
    private Authentication capturedAuthentication;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * The real decoder + the real validator chain, keyed on this class's RSA keypair.
     *
     * <p>{@link ServiceLevelOAuth2Config} is instantiated exactly as the production context builds
     * it and asked for its own {@code jwtTokenValidator()} — so deleting
     * {@code .allowSuperAdminWildcard()}, {@code .trustEntitledDomains()} or the issuer allow-list
     * from that class turns this suite red. A test that assembled its own validator chain here
     * would be asserting a chain it wrote itself.
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

    // =====================================================================================
    // D1 — claim lifting, both wire forms, measured on the live SecurityContext
    // =====================================================================================

    @Test
    @DisplayName("array-form roles:[…] → ROLE_-prefixed authorities and a bound ActorContext")
    void arrayFormRolesLiftIntoActorAndAuthorities() throws Exception {
        stubCapturingGet();

        mockMvc.perform(get(PO_PATH).header("Authorization", bearer(token(
                        "operator-001", "scm", Map.of("roles", List.of("OPERATOR"))))))
                .andExpect(status().isOk());

        assertThat(authorities()).containsExactly("ROLE_OPERATOR");
        assertThat(capturedAuthentication.getName()).isEqualTo("operator-001");
        assertThat(capturedActor).isInstanceOf(ActorContext.class);
        assertThat(capturedActor.accountId()).isEqualTo("operator-001");
        assertThat(capturedActor.tenantId()).isEqualTo("scm");
        assertThat(capturedActor.roles()).containsExactly("OPERATOR");
        // scm's own policy, still local, still reading off the shared record's plain role set.
        assertThat(capturedActor.isOperator()).isTrue();
        assertThat(capturedActor.actorType()).isEqualTo(ActorType.OPERATOR);
    }

    @Test
    @DisplayName("space-delimited role:\"A B\" (no roles claim) lifts both roles")
    void spaceDelimitedRoleStringLiftsBothRoles() throws Exception {
        stubCapturingGet();

        mockMvc.perform(get(PO_PATH).header("Authorization", bearer(token(
                        "operator-002", "scm", Map.of("role", "OPERATOR BUYER")))))
                .andExpect(status().isOk());

        assertThat(authorities()).containsExactlyInAnyOrder("ROLE_OPERATOR", "ROLE_BUYER");
        assertThat(capturedActor.roles()).containsExactlyInAnyOrder("OPERATOR", "BUYER");
        assertThat(capturedActor.actorType()).isEqualTo(ActorType.OPERATOR);
    }

    @Test
    @DisplayName("comma-delimited role:\"A,B\" lifts both roles; ADMIN still maps to OPERATOR")
    void commaDelimitedRoleStringLiftsBothRoles() throws Exception {
        stubCapturingGet();

        mockMvc.perform(get(PO_PATH).header("Authorization", bearer(token(
                        "operator-003", "scm", Map.of("role", "BUYER,ADMIN")))))
                .andExpect(status().isOk());

        assertThat(authorities()).containsExactlyInAnyOrder("ROLE_BUYER", "ROLE_ADMIN");
        assertThat(capturedActor.roles()).containsExactlyInAnyOrder("BUYER", "ADMIN");
        assertThat(capturedActor.isOperator()).isTrue();
    }

    @Test
    @DisplayName("assume-tenant SCM_OPERATOR survives the shared resolver (TASK-MONO-417)")
    void assumeTenantOperatorRoleStillMapsToOperator() throws Exception {
        stubCapturingGet();

        mockMvc.perform(get(PO_PATH).header("Authorization", bearer(token(
                        "console-operator-001", "scm", Map.of("roles", List.of("SCM_OPERATOR"))))))
                .andExpect(status().isOk());

        assertThat(capturedActor.actorType()).isEqualTo(ActorType.OPERATOR);
    }

    @Test
    @DisplayName("37-char client-credentials sub survives the shared resolver (TASK-SCM-BE-050)")
    void clientCredentialsSubSurvivesTheSharedResolver() throws Exception {
        stubCapturingGet();

        // No roles/role claim at all — a client-credentials token, exactly as iam mints it.
        mockMvc.perform(get(PO_PATH).header("Authorization", bearer(token(
                        CLIENT_CREDENTIALS_SUB, "scm", Map.of()))))
                .andExpect(status().isOk());

        assertThat(capturedActor.accountId()).isEqualTo(CLIENT_CREDENTIALS_SUB).hasSize(37);
        assertThat(capturedActor.roles()).isEmpty();
        assertThat(capturedActor.actorType()).isEqualTo(ActorType.BUYER);
        assertThat(authorities()).isEmpty();
        assertThat(capturedAuthentication.isAuthenticated()).isTrue();
    }

    // =====================================================================================
    // D4 — the chain tail: who is refused, and with which envelope
    // =====================================================================================

    @Test
    @DisplayName("no bearer token → 401 UNAUTHORIZED")
    void noTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get(PO_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("cross-tenant token → 403 TENANT_FORBIDDEN (never 401, never 200)")
    void crossTenantTokenIsForbidden() throws Exception {
        mockMvc.perform(get(PO_PATH).header("Authorization", bearer(token(
                        "operator-001", "wms", Map.of("roles", List.of("OPERATOR"))))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_FORBIDDEN"));
    }

    /**
     * The {@code anyRequest()} tail, pinned. procurement ends {@code denyAll()}, measured against
     * the tree before the D4 adoption and preserved by it — so an authenticated caller on a path
     * outside the permit/authenticate lists is refused, not merely 404'd. Flipping the tail to
     * {@code anyRequestAuthenticated()} turns this 403 into a 404, which is why the assertion is on
     * the status and not on class presence.
     */
    @Test
    @DisplayName("authenticated caller on an unlisted path → 403 (the denyAll tail, not 404)")
    void authenticatedCallerOnUnlistedPathIsDenied() throws Exception {
        mockMvc.perform(get("/api/somewhere-else").header("Authorization", bearer(token(
                        "operator-001", "scm", Map.of("roles", List.of("OPERATOR"))))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("actuator probe is permitted unauthenticated (404 from the slice, never 401)")
    void publicActuatorProbeIsPermitted() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isNotFound());
    }

    /**
     * The webhook prefix must keep bypassing the JWT chain entirely — suppliers hold no OIDC
     * client, and their requests authenticate against {@code WebhookSignatureFilter} instead. A D4
     * adoption that routed them through the decoder would 401 every supplier callback.
     */
    @Test
    @DisplayName("webhook prefix bypasses the JWT chain (404 from the slice, never 401)")
    void webhookPrefixBypassesTheJwtChain() throws Exception {
        mockMvc.perform(get("/api/procurement/webhooks/asn"))
                .andExpect(status().isNotFound());
    }

    // =====================================================================================
    // helpers
    // =====================================================================================

    private void stubCapturingGet() {
        when(service.get(anyString(), any(ActorContext.class))).thenAnswer(invocation -> {
            capturedActor = invocation.getArgument(1);
            capturedAuthentication = SecurityContextHolder.getContext().getAuthentication();
            return view();
        });
    }

    private List<String> authorities() {
        return capturedAuthentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    private static PurchaseOrderView view() {
        Instant now = Instant.now();
        return new PurchaseOrderView(
                "po-1", "scm", "PO-0001", "sup-001", "buyer-001",
                PoStatus.DRAFT, PoOrigin.OPERATOR, null, BigDecimal.TEN, "USD",
                null, null, null, null, now, now, List.of());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    /** Signs a real RS256 JWT with this class's private key. */
    private static String token(String subject, String tenantId, Map<String, Object> extraClaims)
            throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(ISSUER)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .claim(TenantClaimValidator.CLAIM_TENANT_ID, tenantId);
        extraClaims.forEach(claims::claim);

        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).build(), claims.build());
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
