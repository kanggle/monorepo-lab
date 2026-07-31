package com.example.fanplatform.membership.presentation.controller;

import com.example.fanplatform.membership.application.ActorContext;
import com.example.fanplatform.membership.application.CancelMembershipUseCase;
import com.example.fanplatform.membership.application.GetMembershipUseCase;
import com.example.fanplatform.membership.application.ListMembershipsUseCase;
import com.example.fanplatform.membership.application.QuoteUpgradeUseCase;
import com.example.fanplatform.membership.application.RenewMembershipUseCase;
import com.example.fanplatform.membership.application.SubscribeUseCase;
import com.example.fanplatform.membership.presentation.advice.GlobalExceptionHandler;
import com.example.fanplatform.membership.testsupport.JwtTestHelper;
import com.example.fanplatform.membership.testsupport.SliceTestSecurityConfig;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-MONO-058 § D1 — the actor/JWT-claim path, exercised end to end.
 *
 * <p>membership-service is the only fan service with <strong>two</strong> filter chains: the Order(1)
 * {@code /internal/**} workload-identity chain (a different converter, {@code ROLE_INTERNAL}, a plain
 * JWT principal — explicitly out of D1's scope) and the Order(2) end-user chain that the promoted
 * {@code ActorContextJwtAuthenticationConverter} serves. Both are exercised here so the promotion is
 * shown not to have leaked across that boundary.
 *
 * <p>Every assertion goes through the real filter chain with a really RSA-signed JWT.
 */
@WebMvcTest(controllers = MembershipController.class)
@Import({SliceTestSecurityConfig.class, GlobalExceptionHandler.class})
// Forces a context-cache key distinct from MembershipControllerSliceTest's — see the same comment in
// the sibling fan services' copies: a shared cached context would bind the JwtDecoder to one class's
// JwtTestHelper keypair and turn the other class's assertions red for an unrelated reason.
@TestPropertySource(properties = "fanplatform.test.context=actor-auth-path")
@DisplayName("membership-service — actor/JWT claim path through the real filter chain (ADR-MONO-058 D1)")
class ActorContextAuthPathSliceTest {

    private static final JwtTestHelper JWT;

    static {
        JWT = new JwtTestHelper();
        SliceTestSecurityConfig.useFixture(JWT);
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean SubscribeUseCase subscribeUseCase;
    @MockitoBean RenewMembershipUseCase renewMembershipUseCase;
    @MockitoBean CancelMembershipUseCase cancelMembershipUseCase;
    @MockitoBean ListMembershipsUseCase listMembershipsUseCase;
    @MockitoBean GetMembershipUseCase getMembershipUseCase;
    @MockitoBean QuoteUpgradeUseCase quoteUpgradeUseCase;

    /** The actor the controller method actually received, captured inside the filter chain. */
    private final AtomicReference<ActorContext> boundActor = new AtomicReference<>();

    /** The live SecurityContext authentication at controller-invocation time. */
    private final AtomicReference<Authentication> liveAuthentication = new AtomicReference<>();

    @BeforeEach
    void captureOnInvocation() {
        when(listMembershipsUseCase.execute(any())).thenAnswer(invocation -> {
            boundActor.set(invocation.getArgument(0));
            liveAuthentication.set(SecurityContextHolder.getContext().getAuthentication());
            return List.of();
        });
    }

    private List<String> capturedAuthorities() {
        return liveAuthentication.get().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    private String bearer(String sub, Map<String, Object> claims) {
        return "Bearer " + JWT.signEndUser(sub, JwtTestHelper.DEFAULT_TENANT_ID, claims);
    }

    @Test
    @DisplayName("array-form roles claim -> ROLE_-prefixed authorities + bound actor")
    void arrayFormRolesClaim() throws Exception {
        mockMvc.perform(get("/api/fan/memberships")
                        .header("Authorization", bearer("acc-1", Map.of("roles", List.of("FAN", "ARTIST")))))
                .andExpect(status().isOk());

        assertThat(boundActor.get().accountId()).isEqualTo("acc-1");
        assertThat(boundActor.get().tenantId()).isEqualTo(JwtTestHelper.DEFAULT_TENANT_ID);
        assertThat(boundActor.get().roles()).containsExactlyInAnyOrder("FAN", "ARTIST");
        assertThat(capturedAuthorities()).containsExactlyInAnyOrder("ROLE_FAN", "ROLE_ARTIST");
        assertThat(liveAuthentication.get().getName()).isEqualTo("acc-1");
    }

    @Test
    @DisplayName("delimited-string role claim (no roles claim) -> the same authorities + bound actor")
    void spaceDelimitedStringRoleClaim() throws Exception {
        mockMvc.perform(get("/api/fan/memberships")
                        .header("Authorization", bearer("acc-2", Map.of("role", "FAN ARTIST"))))
                .andExpect(status().isOk());

        assertThat(boundActor.get().accountId()).isEqualTo("acc-2");
        assertThat(boundActor.get().roles()).containsExactlyInAnyOrder("FAN", "ARTIST");
        assertThat(capturedAuthorities()).containsExactlyInAnyOrder("ROLE_FAN", "ROLE_ARTIST");
    }

    @Test
    @DisplayName("comma-delimited role claim -> the same authorities")
    void commaDelimitedStringRoleClaim() throws Exception {
        mockMvc.perform(get("/api/fan/memberships")
                        .header("Authorization", bearer("acc-3", Map.of("role", "FAN,ARTIST"))))
                .andExpect(status().isOk());

        assertThat(boundActor.get().roles()).containsExactlyInAnyOrder("FAN", "ARTIST");
        assertThat(capturedAuthorities()).containsExactlyInAnyOrder("ROLE_FAN", "ROLE_ARTIST");
    }

    @Test
    @DisplayName("no role claim at all -> authenticated actor with zero roles and zero authorities")
    void noRoleClaim() throws Exception {
        mockMvc.perform(get("/api/fan/memberships")
                        .header("Authorization", bearer("acc-4", Map.of())))
                .andExpect(status().isOk());

        assertThat(boundActor.get().accountId()).isEqualTo("acc-4");
        assertThat(boundActor.get().roles()).isEmpty();
        assertThat(capturedAuthorities()).isEmpty();
    }

    @Test
    @DisplayName("the bound actor is this service's own ActorContext type")
    void boundActorIsTheServicesOwnType() throws Exception {
        mockMvc.perform(get("/api/fan/memberships")
                        .header("Authorization", "Bearer " + JWT.signFanToken("acc-5")))
                .andExpect(status().isOk());

        // The library never constructs this type; the service's own record constructor does
        // (ActorContext::new, passed as the ActorContextFactory).
        assertThat(boundActor.get()).isExactlyInstanceOf(ActorContext.class);
        assertThat(boundActor.get().roles()).containsExactly("FAN");
    }

    @Test
    @DisplayName("no bearer token -> 401, the controller is never reached")
    void noTokenIs401() throws Exception {
        mockMvc.perform(get("/api/fan/memberships"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        assertThat(boundActor.get()).isNull();
    }

    @Test
    @DisplayName("cross-tenant token -> 403 TENANT_FORBIDDEN, the controller is never reached")
    void crossTenantIs403() throws Exception {
        mockMvc.perform(get("/api/fan/memberships")
                        .header("Authorization", "Bearer " + JWT.signCrossTenantToken("op-1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_FORBIDDEN"));

        assertThat(boundActor.get()).isNull();
    }

    @Test
    @DisplayName("insufficient credential: an end-user token on /internal/** -> 403 (ROLE_INTERNAL chain)")
    void endUserTokenOnInternalChainIs403() throws Exception {
        // The Order(1) chain is NOT served by the promoted converter — it keeps its own
        // WorkloadIdentityAuthoritiesConverter and a plain Jwt principal (TASK-FAN-BE-029). Asserted
        // here so the D1 promotion is shown not to have leaked across the two-chain boundary.
        mockMvc.perform(get("/internal/membership/access-check")
                        .header("Authorization", "Bearer " + JWT.signFanToken("acc-1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("no credential on /internal/** -> 401")
    void noTokenOnInternalChainIs401() throws Exception {
        mockMvc.perform(get("/internal/membership/access-check"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
