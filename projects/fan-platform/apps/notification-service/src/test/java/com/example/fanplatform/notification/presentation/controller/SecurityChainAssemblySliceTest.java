package com.example.fanplatform.notification.presentation.controller;

import com.example.fanplatform.notification.application.ListNotificationsUseCase;
import com.example.fanplatform.notification.application.MarkNotificationReadUseCase;
import com.example.fanplatform.notification.infrastructure.security.SecurityConfig;
import com.example.fanplatform.notification.infrastructure.security.ServiceLevelOAuth2Config;
import com.example.fanplatform.notification.testsupport.JwtTestHelper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
 * ADR-MONO-058 § D4 — notification-service's <strong>production</strong> chain assembly, pinned.
 *
 * <p>This class is deliberately NOT built on {@code SliceTestSecurityConfig}. That config declares its
 * <em>own</em> filter chain that happens to resemble the production one; a chain-assembly refactor could
 * therefore change {@link SecurityConfig} in any way at all and every test built on it would stay green.
 * What is imported here is the real {@link SecurityConfig} bean and the real
 * {@link ServiceLevelOAuth2Config#endUserTokenValidator()} validator chain. The only substitution is the
 * signature-verification source: a {@link NimbusJwtDecoder} keyed off the local test keypair instead of
 * a live JWKS endpoint, which is the one thing a slice test cannot reach.
 *
 * <p>Every axis D4 touches is asserted as an observable HTTP outcome, so "no behaviour change" is a
 * measurement rather than a claim:
 *
 * <ul>
 *   <li>the decoder's validator chain — foreign issuer → 401, cross-tenant → 403 {@code TENANT_FORBIDDEN}</li>
 *   <li>the {@code anyRequest()} tail — measured as {@code denyAll()}, not assumed
 *       (a valid token on an unlisted path is 403, which is what separates {@code denyAll()} from
 *       {@code authenticated()})</li>
 *   <li>the public-path routing fed from this service's own {@code PublicPaths}</li>
 *   <li>the CSRF-disabled posture — a state-changing request with no CSRF token still reaches the
 *       dispatcher</li>
 *   <li>the {@code STATELESS} session posture — no {@code HttpSession} is created for an
 *       authenticated request</li>
 * </ul>
 *
 * <h2>What makes the 403/405 probes discriminating, and where the honest limit is</h2>
 *
 * The usual way to show a probe can fail is to weaken the chain and watch it go red. That mutation is
 * not performed here — an edit that opens an authentication path should not be committable even
 * transiently, and this repo's tooling refuses to author one. Two of the three probes carry an internal
 * control instead:
 *
 * <ul>
 *   <li><em>{@code anyRequest()} tail.</em> {@code publicExactPathNeedsNoToken} shows that a request
 *       this chain admits and the dispatcher cannot serve comes back <strong>404</strong>. So the
 *       <strong>403</strong> on {@code /some/unlisted/path} is the authorization decision, not an
 *       artefact of the path being unmapped — which is exactly the {@code denyAll()} vs
 *       {@code authenticated()} distinction.</li>
 *   <li><em>CSRF.</em> {@code CsrfFilter} rejects with 403 and can never produce a
 *       <strong>405</strong>; a 405 is only reachable by having passed it.</li>
 *   <li><em>Session.</em> No internal control exists for this one. It is a characterization pin:
 *       identical before and after the D4 adoption, and it fails if a session ever starts being
 *       created.</li>
 * </ul>
 */
@WebMvcTest(controllers = NotificationInboxController.class)
@Import({SecurityConfig.class, SecurityChainAssemblySliceTest.ProductionDecoders.class})
// Distinct context-cache key: the JwtDecoder bean is built once per cached context from whichever
// class's keypair was in scope at construction time (see the sibling slice tests' copies of this note).
@TestPropertySource(properties = "fanplatform.test.context=security-chain-assembly")
@DisplayName("notification-service — production security-chain assembly (ADR-MONO-058 D4)")
class SecurityChainAssemblySliceTest {

    static final JwtTestHelper JWT = new JwtTestHelper();

    /**
     * The production decoder's validator chain, verified against the local test keypair.
     *
     * <p>The chain comes from {@link ServiceLevelOAuth2Config} itself rather than being restated here:
     * a restated chain would keep passing while the real one changed underneath it, which is precisely
     * the failure mode a chain-assembly refactor can produce.
     */
    @TestConfiguration
    static class ProductionDecoders {

        @Bean
        NimbusJwtDecoder endUserJwtDecoder() {
            ServiceLevelOAuth2Config production = new ServiceLevelOAuth2Config();
            ReflectionTestUtils.setField(production, "endUserJwkSetUri", "http://jwks.invalid/oauth2/jwks");
            ReflectionTestUtils.setField(production, "allowedIssuersCsv",
                    JwtTestHelper.SAS_ISSUER + "," + JwtTestHelper.LEGACY_ISSUER);
            ReflectionTestUtils.setField(production, "requiredTenantId", JwtTestHelper.DEFAULT_TENANT_ID);

            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey()).build();
            decoder.setJwtValidator(production.endUserTokenValidator());
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

    @MockitoBean ListNotificationsUseCase listNotificationsUseCase;
    @MockitoBean MarkNotificationReadUseCase markNotificationReadUseCase;

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    // ---- the decoder's validator chain -------------------------------------------------------

    @Test
    @DisplayName("no bearer token on the inbox surface -> 401 UNAUTHORIZED")
    void noTokenIs401() throws Exception {
        mockMvc.perform(get("/api/fan/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("issuer outside the allow-list -> 401 (AllowedIssuersValidator arm still installed)")
    void foreignIssuerIs401() throws Exception {
        mockMvc.perform(get("/api/fan/notifications")
                        .header("Authorization", bearer(JWT.signForeignIssuer("fan-1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("cross-tenant token -> 403 TENANT_FORBIDDEN (TenantClaimValidator arm still installed)")
    void crossTenantIs403() throws Exception {
        mockMvc.perform(get("/api/fan/notifications")
                        .header("Authorization", bearer(JWT.signCrossTenantToken("op-1"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_FORBIDDEN"));
    }

    // ---- the authorize rules -----------------------------------------------------------------

    @Test
    @DisplayName("public exact path is reachable with no token at all")
    void publicExactPathNeedsNoToken() throws Exception {
        // 404, not 401/403: the request passed the filter chain and reached the dispatcher, which has
        // no actuator handler registered in a @WebMvcTest slice. 401 here would mean permitAll() was
        // lost; 404 is the only status that proves it survived.
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
    @DisplayName("anyRequest() tail is denyAll(): a VALID token on an unlisted path is still 403")
    void anyRequestTailIsDenyAll() throws Exception {
        // The measurement that separates the two tails the fleet uses. Under denyAll() an authenticated
        // caller on an unlisted path is refused (403); under authenticated() the same request would be
        // admitted and answered 404 by the dispatcher.
        mockMvc.perform(get("/some/unlisted/path")
                        .header("Authorization", bearer(JWT.signFanToken("fan-1"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("an unlisted path with no token -> 401, not 403")
    void unlistedPathWithoutTokenIs401() throws Exception {
        mockMvc.perform(get("/some/unlisted/path"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // ---- the CSRF / session posture ----------------------------------------------------------

    @Test
    @DisplayName("CSRF is disabled: a POST with no CSRF token reaches the dispatcher")
    void csrfIsDisabledForTheApiSurface() throws Exception {
        // 405 (no POST handler on the collection route) proves the request got past CsrfFilter. With
        // CSRF enabled this would be 403 from the AccessDeniedHandler, never 405.
        mockMvc.perform(post("/api/fan/notifications")
                        .header("Authorization", bearer(JWT.signFanToken("fan-1")))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("sessions are STATELESS: an authenticated request creates no HttpSession")
    void authenticatedRequestCreatesNoSession() throws Exception {
        MvcResult result = mockMvc.perform(get("/some/unlisted/path")
                        .header("Authorization", bearer(JWT.signFanToken("fan-1"))))
                .andExpect(status().isForbidden())
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .noneMatch(cookie -> cookie.startsWith("JSESSIONID"));
    }
}
