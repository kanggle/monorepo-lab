package com.example.security.servlet.actor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * The promoted SecurityContext reader (ADR-MONO-058 § D1).
 *
 * <p>Both failure messages are asserted verbatim: consuming services map {@link IllegalStateException}
 * to 422 {@code ILLEGAL_STATE}, and the promoted copies' exact wording is what their own tests and their
 * operators read.
 */
@DisplayName("ActorContextResolver — typed principal read-back, mechanism only")
class ActorContextResolverTest {

    record TestActor(String accountId, String tenantId, Set<String> roles) {
    }

    record OtherActor(String accountId) {
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("acc-1")
                .claim("tenant_id", "tenant-x")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }

    @Test
    @DisplayName("returns the principal, typed, when it matches the requested actor type")
    void returnsTypedPrincipal() {
        TestActor actor = new TestActor("acc-1", "tenant-x", Set.of("ALPHA"));
        SecurityContextHolder.getContext().setAuthentication(
                new ActorAuthenticationToken(jwt(), actor, "acc-1", List.of()));

        TestActor resolved = ActorContextResolver.currentOrThrow(TestActor.class);

        assertThat(resolved).isSameAs(actor);
    }

    @Test
    @DisplayName("no authentication -> IllegalStateException with the contractual message")
    void noAuthentication() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> ActorContextResolver.currentOrThrow(TestActor.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No authenticated actor in SecurityContext");
    }

    @Test
    @DisplayName("an unauthenticated Authentication -> the same 'no authenticated actor' message")
    void unauthenticatedAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("acc-1", "n/a"));  // isAuthenticated() == false

        assertThatThrownBy(() -> ActorContextResolver.currentOrThrow(TestActor.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No authenticated actor in SecurityContext");
    }

    @Test
    @DisplayName("a plain-JWT principal (e.g. a workload-identity chain) -> 'Unexpected principal type'")
    void wrongPrincipalType() {
        // membership-service's Order(1) /internal/** chain installs a plain Jwt principal. An
        // @CurrentActor parameter reached from that chain must fail loudly, not resolve to null.
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt(), List.of(), "client-x"));

        assertThatThrownBy(() -> ActorContextResolver.currentOrThrow(TestActor.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Unexpected principal type: ")
                .hasMessageContaining(Jwt.class.getName());
    }

    @Test
    @DisplayName("an actor of a different service's type -> 'Unexpected principal type'")
    void foreignActorType() {
        SecurityContextHolder.getContext().setAuthentication(
                new ActorAuthenticationToken(jwt(), new OtherActor("acc-1"), "acc-1", List.of()));

        assertThatThrownBy(() -> ActorContextResolver.currentOrThrow(TestActor.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(OtherActor.class.getName());
    }

    @Test
    @DisplayName("a null principal -> 'Unexpected principal type: null', not a NullPointerException")
    void nullPrincipal() {
        SecurityContextHolder.getContext().setAuthentication(
                new ActorAuthenticationToken(jwt(), null, "acc-1", List.of()));

        assertThatThrownBy(() -> ActorContextResolver.currentOrThrow(TestActor.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unexpected principal type: null");
    }
}
