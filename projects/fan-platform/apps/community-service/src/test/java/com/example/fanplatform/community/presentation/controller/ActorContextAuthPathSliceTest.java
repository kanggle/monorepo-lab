package com.example.fanplatform.community.presentation.controller;

import com.example.fanplatform.community.application.ActorContext;
import com.example.fanplatform.community.application.FeedPage;
import com.example.fanplatform.community.application.GetFeedUseCase;
import com.example.fanplatform.community.presentation.advice.GlobalExceptionHandler;
import com.example.fanplatform.community.testsupport.JwtTestHelper;
import com.example.fanplatform.community.testsupport.SliceTestSecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-MONO-058 § D1 — the actor/JWT-claim path, exercised end to end.
 *
 * <p>Every assertion here goes through the <strong>real</strong> Resource Server filter chain
 * ({@link SliceTestSecurityConfig}: a real {@code NimbusJwtDecoder}, the real
 * {@code AllowedIssuersValidator} + {@code TenantClaimValidator}) with a <strong>really RSA-signed</strong>
 * JWT. Nothing here constructs an {@code ActorContext} or a {@code Jwt} by hand — a hand-built fixture
 * would keep passing if the shared converter were never wired, or if the {@code @CurrentActor} argument
 * resolver never registered.
 *
 * <p>Pins, after the mechanism moved to {@code libs:java-security-servlet}: both wire forms of the role
 * claim produce the same {@code ROLE_}-prefixed authorities on the live {@code SecurityContext};
 * {@code @CurrentActor ActorContext} still receives the right values (including this service's own
 * {@code isOperator()}/{@code owns()} policy, which did <em>not</em> move); and the rejection paths are
 * unchanged.
 */
@WebMvcTest(controllers = FeedController.class)
@Import({SliceTestSecurityConfig.class, GlobalExceptionHandler.class})
// Forces a context-cache key distinct from FeedControllerSliceTest's. Both classes declare the same
// @WebMvcTest configuration, so without this they would SHARE one cached ApplicationContext — whose
// JwtDecoder bean is built once, from whichever class's JwtTestHelper happened to be in
// SliceTestSecurityConfig's static field at that moment. The other class's (differently-keyed) tokens
// then fail to verify and its assertions turn red for a reason unrelated to the code under test.
@TestPropertySource(properties = "fanplatform.test.context=actor-auth-path")
@DisplayName("community-service — actor/JWT claim path through the real filter chain (ADR-MONO-058 D1)")
class ActorContextAuthPathSliceTest {

    private static final JwtTestHelper JWT;

    static {
        JWT = new JwtTestHelper();
        SliceTestSecurityConfig.useFixture(JWT);
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    GetFeedUseCase getFeedUseCase;

    /** The actor the controller method actually received, captured inside the filter chain. */
    private final AtomicReference<ActorContext> boundActor = new AtomicReference<>();

    /** The live SecurityContext authentication at controller-invocation time. */
    private final AtomicReference<Authentication> liveAuthentication = new AtomicReference<>();

    @BeforeEach
    void captureOnInvocation() {
        when(getFeedUseCase.execute(any(), anyInt(), anyInt())).thenAnswer(invocation -> {
            boundActor.set(invocation.getArgument(0));
            liveAuthentication.set(SecurityContextHolder.getContext().getAuthentication());
            return new FeedPage(List.of(), 0, 20, 0L, 0, false);
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
        mockMvc.perform(get("/api/community/feed")
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
        mockMvc.perform(get("/api/community/feed")
                        .header("Authorization", bearer("fan-2", Map.of("role", "FAN ARTIST"))))
                .andExpect(status().isOk());

        assertThat(boundActor.get().accountId()).isEqualTo("fan-2");
        assertThat(boundActor.get().roles()).containsExactlyInAnyOrder("FAN", "ARTIST");
        assertThat(capturedAuthorities()).containsExactlyInAnyOrder("ROLE_FAN", "ROLE_ARTIST");
    }

    @Test
    @DisplayName("comma-delimited role claim -> the same authorities")
    void commaDelimitedStringRoleClaim() throws Exception {
        mockMvc.perform(get("/api/community/feed")
                        .header("Authorization", bearer("fan-3", Map.of("role", "FAN,ARTIST"))))
                .andExpect(status().isOk());

        assertThat(boundActor.get().roles()).containsExactlyInAnyOrder("FAN", "ARTIST");
        assertThat(capturedAuthorities()).containsExactlyInAnyOrder("ROLE_FAN", "ROLE_ARTIST");
    }

    @Test
    @DisplayName("this service's own role policy still reads off the bound actor — isOperator()/owns()")
    void servicePolicyStillAppliesToBoundActor() throws Exception {
        // ADR-MONO-058 § D1's Ownership-Rule boundary: the FAN_OPERATOR literal and the owns()
        // authorship predicate are community-service's, and did NOT move to the shared library.
        mockMvc.perform(get("/api/community/feed")
                        .header("Authorization", bearer("op-1", Map.of("roles", List.of("FAN_OPERATOR")))))
                .andExpect(status().isOk());

        assertThat(boundActor.get().isOperator()).isTrue();
        assertThat(boundActor.get().owns("someone-else")).isTrue();
        assertThat(capturedAuthorities()).containsExactly("ROLE_FAN_OPERATOR");
    }

    @Test
    @DisplayName("a plain fan is not an operator and owns only its own content")
    void plainFanIsNotOperator() throws Exception {
        mockMvc.perform(get("/api/community/feed")
                        .header("Authorization", bearer("fan-4", Map.of("roles", List.of("FAN")))))
                .andExpect(status().isOk());

        assertThat(boundActor.get().isOperator()).isFalse();
        assertThat(boundActor.get().owns("someone-else")).isFalse();
        assertThat(boundActor.get().owns("fan-4")).isTrue();
    }

    @Test
    @DisplayName("no role claim at all -> authenticated actor with zero roles and zero authorities")
    void noRoleClaim() throws Exception {
        mockMvc.perform(get("/api/community/feed")
                        .header("Authorization", bearer("fan-5", Map.of())))
                .andExpect(status().isOk());

        assertThat(boundActor.get().roles()).isEmpty();
        assertThat(boundActor.get().hasRole("FAN")).isFalse();
        assertThat(capturedAuthorities()).isEmpty();
    }

    @Test
    @DisplayName("no bearer token -> 401, the controller is never reached")
    void noTokenIs401() throws Exception {
        mockMvc.perform(get("/api/community/feed"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        assertThat(boundActor.get()).isNull();
    }

    @Test
    @DisplayName("cross-tenant token -> 403 TENANT_FORBIDDEN, the controller is never reached")
    void crossTenantIs403() throws Exception {
        mockMvc.perform(get("/api/community/feed")
                        .header("Authorization", "Bearer " + JWT.signCrossTenantToken("op-1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_FORBIDDEN"));

        assertThat(boundActor.get()).isNull();
    }
}
