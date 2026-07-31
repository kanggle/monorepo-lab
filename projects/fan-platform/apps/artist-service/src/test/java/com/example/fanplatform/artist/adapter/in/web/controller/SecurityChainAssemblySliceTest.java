package com.example.fanplatform.artist.adapter.in.web.controller;

import com.example.fanplatform.artist.application.port.in.ArchiveArtistUseCase;
import com.example.fanplatform.artist.application.port.in.GetArtistUseCase;
import com.example.fanplatform.artist.application.port.in.PublishArtistUseCase;
import com.example.fanplatform.artist.application.port.in.RegisterArtistUseCase;
import com.example.fanplatform.artist.application.port.in.UpdateArtistUseCase;
import com.example.fanplatform.artist.config.SecurityConfig;
import com.example.fanplatform.artist.config.ServiceLevelOAuth2Config;
import com.example.fanplatform.artist.testsupport.JwtTestHelper;
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
import org.springframework.security.oauth2.jwt.JwtDecoder;
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
 * ADR-MONO-058 § D4 — artist-service's <strong>production</strong> chain assembly, pinned.
 *
 * <p>Deliberately not built on {@code SliceTestSecurityConfig}: that config declares its own filter
 * chain resembling production's, so a chain-assembly refactor could change {@link SecurityConfig} in
 * any way at all and every test built on it would stay green. What is imported here is the real
 * {@link SecurityConfig} bean and the real {@link ServiceLevelOAuth2Config#jwtTokenValidator()} chain;
 * the only substitution is the signature-verification source (local test keypair instead of a live
 * JWKS endpoint), which is the one thing a slice cannot reach.
 *
 * <h2>artist-service is the fan service with rules the shared builder must not re-order</h2>
 *
 * {@code authorizeHttpRequests} is first-match-wins, and this chain interleaves method-scoped rules
 * the other three do not have: {@code POST}/{@code PATCH}/{@code DELETE} role gates registered
 * <em>before</em> the {@code GET … authenticated()} rules that would otherwise shadow them. Both the
 * gate and the read rule are asserted below on the same paths, so a re-ordering would show up as a
 * 422/403 flip rather than as a silent widening.
 *
 * <h2>What makes the 403/405 probes discriminating, and where the honest limit is</h2>
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
 *   <li><em>CSRF.</em> {@code CsrfFilter} rejects with 403; the admin-role POST below is answered
 *       <strong>422</strong> by the dispatcher, which is only reachable by having passed it.</li>
 *   <li><em>Session.</em> No internal control. A characterization pin: identical before and after the
 *       D4 adoption, red if a session ever starts being created.</li>
 * </ul>
 */
@WebMvcTest(controllers = ArtistController.class)
@Import({SecurityConfig.class, SecurityChainAssemblySliceTest.ProductionDecoders.class})
@TestPropertySource(properties = "fanplatform.test.context=security-chain-assembly")
@DisplayName("artist-service — production security-chain assembly (ADR-MONO-058 D4)")
class SecurityChainAssemblySliceTest {

    static final JwtTestHelper JWT = new JwtTestHelper();

    /**
     * The production decoder's validator chain, verified against the local test keypair. The chain
     * comes from {@link ServiceLevelOAuth2Config} itself rather than being restated: a restated chain
     * would keep passing while the real one changed underneath it.
     */
    @TestConfiguration
    static class ProductionDecoders {

        @Bean
        JwtDecoder jwtDecoder() {
            ServiceLevelOAuth2Config production = new ServiceLevelOAuth2Config();
            ReflectionTestUtils.setField(production, "jwkSetUri", "http://jwks.invalid/oauth2/jwks");
            ReflectionTestUtils.setField(production, "allowedIssuersCsv",
                    JwtTestHelper.SAS_ISSUER + "," + JwtTestHelper.LEGACY_ISSUER);
            ReflectionTestUtils.setField(production, "requiredTenantId", JwtTestHelper.DEFAULT_TENANT_ID);

            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey()).build();
            decoder.setJwtValidator(production.jwtTokenValidator());
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

    @MockitoBean RegisterArtistUseCase registerUseCase;
    @MockitoBean UpdateArtistUseCase updateUseCase;
    @MockitoBean PublishArtistUseCase publishUseCase;
    @MockitoBean ArchiveArtistUseCase archiveUseCase;
    @MockitoBean GetArtistUseCase getUseCase;

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    // ---- the decoder's validator chain -------------------------------------------------------

    @Test
    @DisplayName("no bearer token on the API surface -> 401 UNAUTHORIZED")
    void noTokenIs401() throws Exception {
        mockMvc.perform(get("/api/artists/a-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("issuer outside the allow-list -> 401 (AllowedIssuersValidator arm still installed)")
    void foreignIssuerIs401() throws Exception {
        mockMvc.perform(get("/api/artists/a-1")
                        .header("Authorization", bearer(JWT.signForeignIssuer("fan-1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("cross-tenant token -> 403 TENANT_FORBIDDEN (TenantClaimValidator arm still installed)")
    void crossTenantIs403() throws Exception {
        mockMvc.perform(get("/api/artists/a-1")
                        .header("Authorization", bearer(JWT.signCrossTenantToken("op-1"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_FORBIDDEN"));
    }

    // ---- the authorize rules, in registration order -------------------------------------------

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
    @DisplayName("the admin role gate still precedes the GET rule: a plain FAN POSTing is 403 FORBIDDEN")
    void mutatingRouteRequiresAnAdminTierRole() throws Exception {
        // artist-service's 403 body says FORBIDDEN, not the PERMISSION_DENIED its three siblings
        // write. That divergence is pinned, not tidied.
        mockMvc.perform(post("/api/artists")
                        .header("Authorization", bearer(JWT.signFanToken("fan-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("the assume-tenant FAN_OPERATOR role is still admitted on the mutating route")
    void fanOperatorIsAdmittedOnTheMutatingRoute() throws Exception {
        // Past the gate, so the dispatcher answers — 422 on an empty body (fan's validation-failure
        // status, TASK-FAN-BE-038 / ADR-MONO-058 § D2), never 403.
        mockMvc.perform(post("/api/artists")
                        .header("Authorization", bearer(JWT.signFanOperatorToken("op-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("a GET is admitted for any authenticated caller in the tenant")
    void readRouteAdmitsAnyAuthenticatedCaller() throws Exception {
        // Reaches the controller (the mocked use case returns null -> 500 from the dispatcher, but the
        // status that matters is that it is neither 401 nor 403).
        MvcResult result = mockMvc.perform(get("/api/artists/a-1")
                        .header("Authorization", bearer(JWT.signFanToken("fan-1"))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }

    @Test
    @DisplayName("anyRequest() tail is denyAll(): a VALID token on an unlisted path is still 403")
    void anyRequestTailIsDenyAll() throws Exception {
        mockMvc.perform(get("/some/unlisted/path")
                        .header("Authorization", bearer(JWT.signAdminToken("admin-1"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
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
        // 422 (empty body -> fan's validation-failure status) proves the request got past CsrfFilter
        // with an admin-tier token. With CSRF enabled this would be 403 from the AccessDeniedHandler.
        mockMvc.perform(post("/api/artists")
                        .header("Authorization", bearer(JWT.signAdminToken("admin-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());
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
