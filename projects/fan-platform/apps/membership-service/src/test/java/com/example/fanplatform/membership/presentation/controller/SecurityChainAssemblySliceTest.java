package com.example.fanplatform.membership.presentation.controller;

import com.example.fanplatform.membership.application.CancelMembershipUseCase;
import com.example.fanplatform.membership.application.GetMembershipUseCase;
import com.example.fanplatform.membership.application.ListMembershipsUseCase;
import com.example.fanplatform.membership.application.QuoteUpgradeUseCase;
import com.example.fanplatform.membership.application.RenewMembershipUseCase;
import com.example.fanplatform.membership.application.SubscribeUseCase;
import com.example.fanplatform.membership.infrastructure.security.SecurityConfig;
import com.example.fanplatform.membership.infrastructure.security.ServiceLevelOAuth2Config;
import com.example.fanplatform.membership.testsupport.JwtTestHelper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.security.interfaces.RSAPublicKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-MONO-058 § D4 — membership-service's <strong>production</strong> chain assembly, pinned.
 *
 * <p>Deliberately not built on {@code SliceTestSecurityConfig}: that config declares its own copies of
 * both filter chains, so a chain-assembly refactor could change {@link SecurityConfig} in any way at
 * all and every test built on it would stay green. What is imported here is the real
 * {@link SecurityConfig} — <em>both</em> its {@code @Order}ed beans — and the real
 * {@link ServiceLevelOAuth2Config} validator chain. The only substitution is the signature
 * verification source (local test keypair instead of a live JWKS endpoint), which is the one thing a
 * slice cannot reach.
 *
 * <h2>membership-service is the one fan service where "which chain answered?" is a security question</h2>
 *
 * It runs two: {@code @Order(1)} {@code securityMatcher("/internal/**")} on workload identity, and
 * {@code @Order(2)} — no {@code securityMatcher}, therefore the catch-all — on end-user tokens. Only
 * the second is assembled by the shared builder; the first stays hand-written (a different converter,
 * a different decoder, no tenant pin). If the builder's chain ever became the one matching
 * {@code /internal/**}, the failure would be an authorization bypass rather than a test error, so the
 * {@code Ordering} nest below identifies the answering chain by its <em>message</em>, which the two
 * chains do not share:
 *
 * <ul>
 *   <li>internal chain 401 → {@code "Missing or invalid internal credentials"}</li>
 *   <li>end-user chain 401 → {@code "Authentication required"}</li>
 *   <li>internal chain 403 → {@code "Workload identity required for /internal/**"}</li>
 *   <li>end-user chain 403 → {@code "Access denied"} / {@code TENANT_FORBIDDEN}</li>
 * </ul>
 *
 * A status-only assertion would pass under a swap; the message is what makes it a chain-identity test.
 *
 * <h2>What makes the 403/400 probes discriminating, and where the honest limit is</h2>
 *
 * The usual proof that a probe can fail is to weaken the chain and watch it go red. That mutation is
 * not performed — an edit that opens an authentication path should not be committable even
 * transiently. Two of the three posture probes carry an internal control instead:
 *
 * <ul>
 *   <li><em>{@code anyRequest()} tail.</em> {@code publicExactPathNeedsNoToken} shows a request this
 *       chain admits but the dispatcher cannot serve comes back <strong>404</strong>. So the
 *       <strong>403</strong> on an unlisted path is the authorization decision, not an artefact of the
 *       path being unmapped — which is exactly the {@code denyAll()} vs {@code authenticated()}
 *       distinction.</li>
 *   <li><em>CSRF.</em> {@code CsrfFilter} rejects with 403; the POST below is answered
 *       <strong>400</strong> by the dispatcher, which is only reachable by having passed it.</li>
 *   <li><em>Session.</em> No internal control. A characterization pin: identical before and after the
 *       D4 adoption, red if a session ever starts being created.</li>
 * </ul>
 */
@WebMvcTest(controllers = MembershipController.class)
@Import({SecurityConfig.class, SecurityChainAssemblySliceTest.ProductionDecoders.class})
@TestPropertySource(properties = "fanplatform.test.context=security-chain-assembly")
@DisplayName("membership-service — production security-chain assembly (ADR-MONO-058 D4)")
class SecurityChainAssemblySliceTest {

    static final JwtTestHelper JWT = new JwtTestHelper();

    /**
     * Both production decoders, verified against the local test keypair. The end-user validator chain
     * comes from {@link ServiceLevelOAuth2Config} itself rather than being restated: a restated chain
     * would keep passing while the real one changed underneath it.
     *
     * <p>The bean <em>names</em> matter — {@link SecurityConfig}'s two chain beans take their decoder
     * by parameter name, which is how each chain gets the right one out of two same-typed candidates.
     */
    @TestConfiguration
    static class ProductionDecoders {

        private static ServiceLevelOAuth2Config wiredProductionConfig() {
            ServiceLevelOAuth2Config production = new ServiceLevelOAuth2Config();
            ReflectionTestUtils.setField(production, "endUserJwkSetUri", "http://jwks.invalid/oauth2/jwks");
            ReflectionTestUtils.setField(production, "allowedIssuersCsv",
                    JwtTestHelper.SAS_ISSUER + "," + JwtTestHelper.LEGACY_ISSUER);
            ReflectionTestUtils.setField(production, "requiredTenantId", JwtTestHelper.DEFAULT_TENANT_ID);
            ReflectionTestUtils.setField(production, "internalJwkSetUri", "http://jwks.invalid/oauth2/jwks");
            ReflectionTestUtils.setField(production, "internalIssuer", JwtTestHelper.SAS_ISSUER);
            return production;
        }

        @Bean
        NimbusJwtDecoder endUserJwtDecoder() {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey()).build();
            decoder.setJwtValidator(wiredProductionConfig().endUserTokenValidator());
            return decoder;
        }

        @Bean
        NimbusJwtDecoder internalJwtDecoder() {
            // The workload decoder deliberately does NOT pin tenant_id (its own Javadoc says so);
            // keeping it issuer-only here preserves that asymmetry between the two chains.
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey()).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(JwtTestHelper.SAS_ISSUER));
            return decoder;
        }

        private static RSAPublicKey publicKey() {
            try {
                RSAKey jwk = (RSAKey) JWKSet.parse(JWT.jwksJson()).getKeys().get(0);
                return jwk.toRSAPublicKey();
            } catch (java.text.ParseException | JOSEException e) {
                throw new IllegalStateException("Failed to build the slice-test public key", e);
            }
        }
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean SubscribeUseCase subscribeUseCase;
    @MockitoBean RenewMembershipUseCase renewMembershipUseCase;
    @MockitoBean CancelMembershipUseCase cancelMembershipUseCase;
    @MockitoBean ListMembershipsUseCase listMembershipsUseCase;
    @MockitoBean GetMembershipUseCase getMembershipUseCase;
    @MockitoBean QuoteUpgradeUseCase quoteUpgradeUseCase;

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    // ---- the end-user chain's decoder --------------------------------------------------------

    @Test
    @DisplayName("no bearer token on the end-user surface -> 401 UNAUTHORIZED")
    void noTokenIs401() throws Exception {
        mockMvc.perform(get("/api/fan/memberships"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("issuer outside the allow-list -> 401 (AllowedIssuersValidator arm still installed)")
    void foreignIssuerIs401() throws Exception {
        mockMvc.perform(get("/api/fan/memberships")
                        .header("Authorization", bearer(JWT.signForeignIssuer("acc-1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("cross-tenant token -> 403 TENANT_FORBIDDEN (TenantClaimValidator arm still installed)")
    void crossTenantIs403() throws Exception {
        mockMvc.perform(get("/api/fan/memberships")
                        .header("Authorization", bearer(JWT.signCrossTenantToken("op-1"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_FORBIDDEN"));
    }

    // ---- the end-user chain's authorize rules ------------------------------------------------

    @Test
    @DisplayName("public exact path is reachable with no token at all")
    void publicExactPathNeedsNoToken() throws Exception {
        // 404, not 401/403: the request passed the filter chain and reached the dispatcher, which has
        // no actuator handler in a @WebMvcTest slice. 401 here would mean permitAll() was lost.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("public prefix subtree is reachable with no token at all")
    void publicPrefixSubtreeNeedsNoToken() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("the PortOne webhook — this service's only non-actuator public path — needs no token")
    void portOneWebhookNeedsNoToken() throws Exception {
        // membership-service is the only fan service whose PublicPaths carries a business route
        // (TASK-FAN-BE-033: PortOne cannot present a fan JWT; its own auth is the HMAC signature
        // checked inside the controller). If the D4 adoption ever stopped feeding PublicPaths into
        // the chain, this would turn 401 and every webhook delivery would be dropped.
        mockMvc.perform(post("/webhooks/portone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("anyRequest() tail is denyAll(): a VALID token on an unlisted path is still 403")
    void anyRequestTailIsDenyAll() throws Exception {
        mockMvc.perform(get("/some/unlisted/path")
                        .header("Authorization", bearer(JWT.signFanToken("acc-1"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("an unlisted path with no token -> 401 from the END-USER chain, not the internal one")
    void unlistedPathWithoutTokenIs401FromTheEndUserChain() throws Exception {
        mockMvc.perform(get("/some/unlisted/path"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    // ---- the CSRF / session posture ----------------------------------------------------------

    @Test
    @DisplayName("CSRF is disabled: a POST with no CSRF token reaches the dispatcher")
    void csrfIsDisabledForTheApiSurface() throws Exception {
        // 400 (the subscribe body is rejected before validation runs) proves the request got past
        // CsrfFilter. With CSRF enabled this would be 403 from the AccessDeniedHandler, never 400.
        mockMvc.perform(post("/api/fan/memberships")
                        .header("Authorization", bearer(JWT.signFanToken("acc-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("sessions are STATELESS: an authenticated request creates no HttpSession")
    void authenticatedRequestCreatesNoSession() throws Exception {
        MvcResult result = mockMvc.perform(get("/some/unlisted/path")
                        .header("Authorization", bearer(JWT.signFanToken("acc-1"))))
                .andExpect(status().isForbidden())
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .noneMatch(cookie -> cookie.startsWith("JSESSIONID"));
    }

    // ---- which chain answers which path ------------------------------------------------------

    @Nested
    @DisplayName("chain ordering: /internal/** is the Order(1) workload chain, everything else Order(2)")
    class Ordering {

        @Test
        @DisplayName("/internal/** with no credential -> the INTERNAL chain's 401, by its own message")
        void internalPathWithNoTokenIsAnsweredByTheInternalChain() throws Exception {
            mockMvc.perform(get("/internal/membership/access"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.message").value("Missing or invalid internal credentials"));
        }

        @Test
        @DisplayName("/internal/** with an END-USER token -> the INTERNAL chain's 403, by its own message")
        void endUserTokenOnInternalPathIsRefusedByTheInternalChain() throws Exception {
            mockMvc.perform(get("/internal/membership/access")
                            .header("Authorization", bearer(JWT.signFanToken("acc-1"))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                    .andExpect(jsonPath("$.message")
                            .value("Workload identity required for /internal/**"));
        }

        @Test
        @DisplayName("/internal/** with a WORKLOAD token clears ROLE_INTERNAL and reaches the dispatcher")
        void workloadTokenOnInternalPathIsAdmitted() throws Exception {
            // The positive half. A refusal-only suite would still pass if the internal chain had
            // become unreachable — 404 (InternalAccessController is not in this @WebMvcTest slice)
            // is what proves the ROLE_INTERNAL gate was cleared rather than never evaluated.
            mockMvc.perform(get("/internal/membership/access")
                            .header("Authorization", bearer(JWT.signWorkloadToken("community-service-client"))))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("a WORKLOAD token on /api/fan/** is answered by the END-USER chain, not the internal one")
        void workloadTokenOnTheEndUserSurfaceIsNotPrivileged() throws Exception {
            // The cc token carries tenant_id=fan-platform, so the end-user chain admits it as an
            // authenticated caller. What matters here is which chain decided: it reaches the
            // controller under the end-user rules rather than meeting the internal chain's
            // ROLE_INTERNAL gate.
            MvcResult result = mockMvc.perform(get("/api/fan/memberships")
                            .header("Authorization", bearer(JWT.signWorkloadToken("community-service-client"))))
                    .andReturn();

            assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
        }
    }
}
