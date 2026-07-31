package com.example.security.servlet.actor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The promoted converter (ADR-MONO-058 § D1) — proves the shared mechanism hands the three lifted
 * claims to a <em>consumer-supplied</em> actor type and never constructs one of its own.
 *
 * <p>{@code TestActor} below stands in for a service's own {@code ActorContext} record, convenience
 * methods and all. Its {@code isPrivileged()} is deliberately present to demonstrate that such a method
 * lives on the consumer's type, not on anything in this library.
 */
@DisplayName("ActorContextJwtAuthenticationConverter — Jwt -> token whose principal is the consumer's actor")
class ActorContextJwtAuthenticationConverterTest {

    /** Stand-in for a consuming service's own actor record — policy lives here, not in the library. */
    record TestActor(String accountId, String tenantId, Set<String> roles) {
        boolean isPrivileged() {
            return roles.contains("ALPHA");
        }
    }

    private final ActorContextJwtAuthenticationConverter<TestActor> converter =
            new ActorContextJwtAuthenticationConverter<>(TestActor::new);

    private static Jwt jwt(Map<String, Object> extra) {
        Jwt.Builder b = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("acc-1")
                .claim("tenant_id", "tenant-x")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        extra.forEach(b::claim);
        return b.build();
    }

    @Test
    @DisplayName("principal is the consumer's actor, carrying the lifted claims")
    void principalIsConsumerActor() {
        AbstractAuthenticationToken token = converter.convert(jwt(Map.of("roles", List.of("ALPHA"))));

        assertThat(token.getPrincipal()).isInstanceOf(TestActor.class);
        TestActor actor = (TestActor) token.getPrincipal();
        assertThat(actor.accountId()).isEqualTo("acc-1");
        assertThat(actor.tenantId()).isEqualTo("tenant-x");
        assertThat(actor.roles()).containsExactly("ALPHA");
        assertThat(actor.isPrivileged()).isTrue();
    }

    @Test
    @DisplayName("authentication name is the sub, and the token is authenticated")
    void nameIsSubAndAuthenticated() {
        AbstractAuthenticationToken token = converter.convert(jwt(Map.of()));

        assertThat(token.getName()).isEqualTo("acc-1");
        assertThat(token.isAuthenticated()).isTrue();
    }

    @Test
    @DisplayName("authorities are ROLE_-prefixed, for the array claim form")
    void authoritiesArrayForm() {
        AbstractAuthenticationToken token = converter.convert(jwt(Map.of("roles", List.of("ALPHA", "BETA"))));

        assertThat(token.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ALPHA", "ROLE_BETA");
    }

    @Test
    @DisplayName("authorities are ROLE_-prefixed, for the delimited-string claim form")
    void authoritiesStringForm() {
        AbstractAuthenticationToken token = converter.convert(jwt(Map.of("role", "ALPHA BETA")));

        assertThat(token.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ALPHA", "ROLE_BETA");
    }

    @Test
    @DisplayName("the underlying Jwt stays reachable as the token's credentials")
    void jwtRetained() {
        Jwt source = jwt(Map.of());

        AbstractAuthenticationToken token = converter.convert(source);

        assertThat(token.getCredentials()).isSameAs(source);
    }

    @Test
    @DisplayName("a missing identity claim propagates the resolver-side IllegalStateException")
    void missingIdentityClaimPropagates() {
        Jwt noTenant = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("acc-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        assertThatThrownBy(() -> converter.convert(noTenant))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("tenant_id claim is missing on the JWT");
    }

    @Test
    @DisplayName("a null factory is rejected at construction, not at the first request")
    void nullFactoryRejected() {
        assertThatThrownBy(() -> new ActorContextJwtAuthenticationConverter<TestActor>(null))
                .isInstanceOf(NullPointerException.class);
    }
}
