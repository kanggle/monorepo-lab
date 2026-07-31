package com.example.fanplatform.artist.adapter.in.web.controller;

import com.example.fanplatform.artist.adapter.in.web.advice.GlobalExceptionHandler;
import com.example.fanplatform.artist.application.ActorContext;
import com.example.fanplatform.artist.application.port.in.SearchArtistDirectoryUseCase;
import com.example.fanplatform.artist.application.port.in.SearchArtistDirectoryUseCase.DirectorySearchResult;
import com.example.fanplatform.artist.application.port.in.SearchArtistDirectoryUseCase.SearchArtistDirectoryQuery;
import com.example.fanplatform.artist.testsupport.JwtTestHelper;
import com.example.fanplatform.artist.testsupport.SliceTestSecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-MONO-058 § D1 — the actor/JWT-claim path, exercised end to end.
 *
 * <p>artist-service is the one fan-platform service with an <strong>HTTP-level role gate</strong>
 * ({@code hasAnyRole(ADMIN_ROLES)} on the mutating routes), so it is where the {@code ROLE_}-prefixed
 * authorities the promoted converter produces are actually <em>consumed</em> by Spring Security rather
 * than only observed. Every assertion goes through the real filter chain with a really RSA-signed JWT.
 *
 * <p>fan-platform declares no {@code @PreAuthorize} anywhere; this chain rule is its equivalent.
 */
@WebMvcTest(controllers = ArtistDirectoryController.class)
@Import({SliceTestSecurityConfig.class, GlobalExceptionHandler.class})
// Forces a context-cache key distinct from ArtistDirectoryControllerSliceTest's — see the same comment
// in the sibling fan services' copies: a shared cached context would bind the JwtDecoder to one class's
// JwtTestHelper keypair and turn the other class's assertions red for an unrelated reason.
@TestPropertySource(properties = "fanplatform.test.context=actor-auth-path")
@DisplayName("artist-service — actor/JWT claim path through the real filter chain (ADR-MONO-058 D1)")
class ActorContextAuthPathSliceTest {

    private static final JwtTestHelper JWT;

    static {
        JWT = new JwtTestHelper();
        SliceTestSecurityConfig.useFixture(JWT);
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    SearchArtistDirectoryUseCase searchUseCase;

    /** The actor the controller method actually received, captured inside the filter chain. */
    private final AtomicReference<ActorContext> boundActor = new AtomicReference<>();

    /** The live SecurityContext authentication at controller-invocation time. */
    private final AtomicReference<Authentication> liveAuthentication = new AtomicReference<>();

    @BeforeEach
    void captureOnInvocation() {
        when(searchUseCase.search(any(SearchArtistDirectoryQuery.class))).thenAnswer(invocation -> {
            SearchArtistDirectoryQuery query = invocation.getArgument(0);
            boundActor.set(query.actor());
            liveAuthentication.set(SecurityContextHolder.getContext().getAuthentication());
            return new DirectorySearchResult(List.of(), 0, 20, 0L, 0);
        });
    }

    private List<String> capturedAuthorities() {
        return liveAuthentication.get().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    private String bearer(String sub, Map<String, Object> claims) {
        return "Bearer " + JWT.sign(sub, null, JwtTestHelper.DEFAULT_TENANT_ID, 300, claims);
    }

    @Test
    @DisplayName("array-form roles claim -> ROLE_-prefixed authorities + bound actor")
    void arrayFormRolesClaim() throws Exception {
        mockMvc.perform(get("/api/artists")
                        .header("Authorization", bearer("fan-1", Map.of("roles", List.of("FAN", "ARTIST")))))
                .andExpect(status().isOk());

        assertThat(boundActor.get().accountId()).isEqualTo("fan-1");
        assertThat(boundActor.get().tenantId()).isEqualTo(JwtTestHelper.DEFAULT_TENANT_ID);
        assertThat(boundActor.get().roles()).containsExactlyInAnyOrder("FAN", "ARTIST");
        assertThat(capturedAuthorities()).containsExactlyInAnyOrder("ROLE_FAN", "ROLE_ARTIST");
        assertThat(liveAuthentication.get().getName()).isEqualTo("fan-1");
    }

    @Test
    @DisplayName("delimited-string role claim (no roles claim) -> the same authorities + bound actor")
    void spaceDelimitedStringRoleClaim() throws Exception {
        mockMvc.perform(get("/api/artists")
                        .header("Authorization", bearer("fan-2", Map.of("role", "FAN ARTIST"))))
                .andExpect(status().isOk());

        assertThat(boundActor.get().accountId()).isEqualTo("fan-2");
        assertThat(boundActor.get().roles()).containsExactlyInAnyOrder("FAN", "ARTIST");
        assertThat(capturedAuthorities()).containsExactlyInAnyOrder("ROLE_FAN", "ROLE_ARTIST");
    }

    @Test
    @DisplayName("comma-delimited role claim -> the same authorities")
    void commaDelimitedStringRoleClaim() throws Exception {
        mockMvc.perform(get("/api/artists")
                        .header("Authorization", bearer("fan-3", Map.of("role", "FAN,ARTIST"))))
                .andExpect(status().isOk());

        assertThat(boundActor.get().roles()).containsExactlyInAnyOrder("FAN", "ARTIST");
        assertThat(capturedAuthorities()).containsExactlyInAnyOrder("ROLE_FAN", "ROLE_ARTIST");
    }

    @Test
    @DisplayName("this service's own admin policy still reads off the bound actor — isAdmin()")
    void servicePolicyStillAppliesToBoundActor() throws Exception {
        // ADR-MONO-058 § D1's Ownership-Rule boundary: isAdmin() and the ADMIN/SUPER_ADMIN/OPERATOR/
        // FAN_OPERATOR literals are artist-service's, and did NOT move to the shared library.
        mockMvc.perform(get("/api/artists")
                        .header("Authorization", bearer("op-1", Map.of("roles", List.of("FAN_OPERATOR")))))
                .andExpect(status().isOk());

        assertThat(boundActor.get().isAdmin()).isTrue();

        mockMvc.perform(get("/api/artists")
                        .header("Authorization", bearer("fan-9", Map.of("roles", List.of("FAN")))))
                .andExpect(status().isOk());

        assertThat(boundActor.get().isAdmin()).isFalse();
    }

    @Test
    @DisplayName("insufficient role: POST /api/artists with a FAN token -> 403 FORBIDDEN (hasAnyRole gate)")
    void insufficientRoleIs403() throws Exception {
        // The chain rule consumes exactly the ROLE_-prefixed authorities this task moved into the
        // shared converter. If the prefix or the role normalisation regressed, this flips.
        mockMvc.perform(post("/api/artists")
                        .header("Authorization", bearer("fan-1", Map.of("roles", List.of("FAN"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"artistType\":\"SOLO\",\"stageName\":\"STAGE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("sufficient role: POST /api/artists with an ADMIN token passes the gate (not 401/403)")
    void sufficientRolePassesTheGate() throws Exception {
        // ArtistController is not in this slice, so the request 404s AFTER authorization — which is
        // exactly the point: the ROLE_ADMIN authority satisfied hasAnyRole(ADMIN_ROLES).
        int status = mockMvc.perform(post("/api/artists")
                        .header("Authorization", bearer("admin-1", Map.of("roles", List.of("ADMIN"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"artistType\":\"SOLO\",\"stageName\":\"STAGE\"}"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isNotIn(401, 403);
    }

    @Test
    @DisplayName("no bearer token -> 401, the controller is never reached")
    void noTokenIs401() throws Exception {
        mockMvc.perform(get("/api/artists"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        assertThat(boundActor.get()).isNull();
    }

    @Test
    @DisplayName("cross-tenant token -> 403 TENANT_FORBIDDEN, the controller is never reached")
    void crossTenantIs403() throws Exception {
        mockMvc.perform(get("/api/artists")
                        .header("Authorization", "Bearer " + JWT.signCrossTenantToken("op-1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_FORBIDDEN"));

        assertThat(boundActor.get()).isNull();
    }
}
