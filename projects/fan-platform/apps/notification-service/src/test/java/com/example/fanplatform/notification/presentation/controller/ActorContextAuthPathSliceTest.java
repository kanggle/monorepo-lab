package com.example.fanplatform.notification.presentation.controller;

import com.example.fanplatform.notification.application.ActorContext;
import com.example.fanplatform.notification.application.ListNotificationsUseCase;
import com.example.fanplatform.notification.application.MarkNotificationReadUseCase;
import com.example.fanplatform.notification.domain.notification.NotificationPage;
import com.example.fanplatform.notification.presentation.advice.GlobalExceptionHandler;
import com.example.fanplatform.notification.testsupport.JwtTestHelper;
import com.example.fanplatform.notification.testsupport.SliceTestSecurityConfig;
import org.junit.jupiter.api.BeforeAll;
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
import java.util.Set;
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
 * {@code AllowedIssuersValidator} + {@code TenantClaimValidator}, the real
 * {@code TenantClaimEnforcer}) with a <strong>really RSA-signed</strong> JWT. Nothing here constructs an
 * {@code ActorContext} or a {@code Jwt} by hand — a hand-built fixture would keep passing if the shared
 * converter were never wired, or if the {@code @CurrentActor} argument resolver never registered.
 *
 * <p>What it pins, after the mechanism moved to {@code libs:java-security-servlet}:
 * <ul>
 *   <li>both wire forms of the role claim — {@code roles: [...]} and a delimited {@code role} string —
 *       still produce the same {@code ROLE_}-prefixed authorities on the live {@code SecurityContext};</li>
 *   <li>{@code @CurrentActor ActorContext} still receives the right {@code accountId}/{@code tenantId}/
 *       {@code roles};</li>
 *   <li>the rejection paths are unchanged: no token → 401, cross-tenant token → 403
 *       {@code TENANT_FORBIDDEN}.</li>
 * </ul>
 */
@WebMvcTest(controllers = NotificationInboxController.class)
@Import({SliceTestSecurityConfig.class, GlobalExceptionHandler.class})
// Forces a context-cache key distinct from NotificationInboxControllerSliceTest's. Both classes
// declare the same @WebMvcTest configuration, so without this they would SHARE one cached
// ApplicationContext — whose JwtDecoder bean is built once, from whichever class's JwtTestHelper
// happened to be in SliceTestSecurityConfig's static field at that moment. The other class's
// (differently-keyed) tokens then fail to verify and its assertions turn red for a reason that has
// nothing to do with the code under test. Measured, not theorised: adding this class without the
// property turned 4 pre-existing NotificationInboxControllerSliceTest cases red.
@TestPropertySource(properties = "fanplatform.test.context=actor-auth-path")
@DisplayName("notification-service — actor/JWT claim path through the real filter chain (ADR-MONO-058 D1)")
class ActorContextAuthPathSliceTest {

    private static final JwtTestHelper JWT = new JwtTestHelper();

    @BeforeAll
    static void wireFixture() {
        SliceTestSecurityConfig.useFixture(JWT);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListNotificationsUseCase listNotifications;

    @MockitoBean
    private MarkNotificationReadUseCase markNotificationRead;

    /** The actor the controller method actually received, captured inside the filter chain. */
    private final AtomicReference<ActorContext> boundActor = new AtomicReference<>();

    /** The live SecurityContext authentication at controller-invocation time. */
    private final AtomicReference<Authentication> liveAuthentication = new AtomicReference<>();

    @BeforeEach
    void captureOnInvocation() {
        when(listNotifications.list(any(), any(), anyInt(), anyInt())).thenAnswer(invocation -> {
            boundActor.set(invocation.getArgument(0));
            liveAuthentication.set(SecurityContextHolder.getContext().getAuthentication());
            return new NotificationPage(List.of(), 0, 20, 0);
        });
    }

    private List<String> capturedAuthorities() {
        return liveAuthentication.get().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    @Test
    @DisplayName("array-form roles claim -> ROLE_-prefixed authorities + bound actor")
    void arrayFormRolesClaim() throws Exception {
        String token = JWT.signEndUser("acc-1", JwtTestHelper.DEFAULT_TENANT_ID,
                Map.of("roles", List.of("FAN", "ARTIST")));

        mockMvc.perform(get("/api/fan/notifications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(boundActor.get().accountId()).isEqualTo("acc-1");
        assertThat(boundActor.get().tenantId()).isEqualTo(JwtTestHelper.DEFAULT_TENANT_ID);
        assertThat(boundActor.get().roles()).containsExactlyInAnyOrder("FAN", "ARTIST");
        assertThat(boundActor.get().hasRole("FAN")).isTrue();
        assertThat(capturedAuthorities()).containsExactlyInAnyOrder("ROLE_FAN", "ROLE_ARTIST");
        assertThat(liveAuthentication.get().getName()).isEqualTo("acc-1");
    }

    @Test
    @DisplayName("delimited-string role claim (no roles claim) -> the same authorities + bound actor")
    void delimitedStringRoleClaim() throws Exception {
        String token = JWT.signEndUser("acc-2", JwtTestHelper.DEFAULT_TENANT_ID,
                Map.of("role", "FAN ARTIST"));

        mockMvc.perform(get("/api/fan/notifications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(boundActor.get().accountId()).isEqualTo("acc-2");
        assertThat(boundActor.get().roles()).containsExactlyInAnyOrder("FAN", "ARTIST");
        assertThat(capturedAuthorities()).containsExactlyInAnyOrder("ROLE_FAN", "ROLE_ARTIST");
    }

    @Test
    @DisplayName("comma-delimited role claim -> the same authorities")
    void commaDelimitedRoleClaim() throws Exception {
        String token = JWT.signEndUser("acc-3", JwtTestHelper.DEFAULT_TENANT_ID,
                Map.of("role", "FAN,ARTIST"));

        mockMvc.perform(get("/api/fan/notifications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(boundActor.get().roles()).containsExactlyInAnyOrder("FAN", "ARTIST");
        assertThat(capturedAuthorities()).containsExactlyInAnyOrder("ROLE_FAN", "ROLE_ARTIST");
    }

    @Test
    @DisplayName("no role claim at all -> authenticated actor with zero roles and zero authorities")
    void noRoleClaim() throws Exception {
        String token = JWT.signEndUser("acc-4", JwtTestHelper.DEFAULT_TENANT_ID, Map.of());

        mockMvc.perform(get("/api/fan/notifications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(boundActor.get().accountId()).isEqualTo("acc-4");
        assertThat(boundActor.get().roles()).isEmpty();
        assertThat(boundActor.get().hasRole("FAN")).isFalse();
        assertThat(capturedAuthorities()).isEmpty();
    }

    @Test
    @DisplayName("the bound actor's role set is the actual service ActorContext type, not a shared one")
    void boundActorIsTheServicesOwnType() throws Exception {
        String token = JWT.signFanToken("acc-5");

        mockMvc.perform(get("/api/fan/notifications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // The library never constructs this type; the service's own record constructor does
        // (ActorContext::new, passed as the ActorContextFactory).
        assertThat(boundActor.get()).isExactlyInstanceOf(ActorContext.class);
        assertThat(Set.copyOf(boundActor.get().roles())).containsExactly("FAN");
    }

    @Test
    @DisplayName("no bearer token -> 401, the controller is never reached")
    void noTokenIs401() throws Exception {
        mockMvc.perform(get("/api/fan/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        assertThat(boundActor.get()).isNull();
    }

    @Test
    @DisplayName("cross-tenant token -> 403 TENANT_FORBIDDEN, the controller is never reached")
    void crossTenantIs403() throws Exception {
        mockMvc.perform(get("/api/fan/notifications")
                        .header("Authorization", "Bearer " + JWT.signCrossTenantToken("op-1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_FORBIDDEN"));

        assertThat(boundActor.get()).isNull();
    }
}
