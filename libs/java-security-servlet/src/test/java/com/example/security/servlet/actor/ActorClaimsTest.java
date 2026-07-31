package com.example.security.servlet.actor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The promoted claim-lifting mechanism, tested in isolation from any project's role policy
 * (ADR-MONO-058 § D1).
 *
 * <p>Role names below are synthetic fixture values. No assertion here encodes what any role
 * <em>means</em> — that is each service's own {@code ActorContext} test's job.
 */
@DisplayName("ActorClaims — sub / tenant_id lifting and roles|role normalisation, mechanism only")
class ActorClaimsTest {

    private static Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder b = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        claims.forEach(b::claim);
        return b.build();
    }

    private static Jwt actorJwt(Map<String, Object> extra) {
        Jwt.Builder b = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("acc-1")
                .claim("tenant_id", "tenant-x")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        extra.forEach(b::claim);
        return b.build();
    }

    @Nested
    @DisplayName("identity claims")
    class Identity {

        @Test
        @DisplayName("sub -> accountId, tenant_id -> tenantId")
        void liftsIdentity() {
            ActorClaims claims = ActorClaims.from(actorJwt(Map.of()));

            assertThat(claims.accountId()).isEqualTo("acc-1");
            assertThat(claims.tenantId()).isEqualTo("tenant-x");
        }

        @Test
        @DisplayName("missing sub -> IllegalStateException with the contractual message")
        void missingSub() {
            Jwt noSub = jwt(Map.of("tenant_id", "tenant-x"));

            assertThatThrownBy(() -> ActorClaims.from(noSub))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("sub claim is missing on the JWT");
        }

        @Test
        @DisplayName("blank sub -> IllegalStateException (not a silently blank actor id)")
        void blankSub() {
            Jwt blankSub = jwt(Map.of("sub", "   ", "tenant_id", "tenant-x"));

            assertThatThrownBy(() -> ActorClaims.from(blankSub))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("sub claim is missing on the JWT");
        }

        @Test
        @DisplayName("missing tenant_id -> IllegalStateException with the contractual message")
        void missingTenant() {
            Jwt noTenant = jwt(Map.of("sub", "acc-1"));

            assertThatThrownBy(() -> ActorClaims.from(noTenant))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("tenant_id claim is missing on the JWT");
        }

        @Test
        @DisplayName("blank tenant_id -> IllegalStateException (fail closed, never an unscoped actor)")
        void blankTenant() {
            Jwt blankTenant = jwt(Map.of("sub", "acc-1", "tenant_id", "  "));

            assertThatThrownBy(() -> ActorClaims.from(blankTenant))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("tenant_id claim is missing on the JWT");
        }
    }

    @Nested
    @DisplayName("role-claim normalisation")
    class Roles {

        @Test
        @DisplayName("array form: roles: [\"A\",\"B\"]")
        void arrayForm() {
            ActorClaims claims = ActorClaims.from(actorJwt(Map.of("roles", List.of("ALPHA", "BETA"))));

            assertThat(claims.roles()).containsExactlyInAnyOrder("ALPHA", "BETA");
        }

        @Test
        @DisplayName("delimited-string form, comma separated: role: \"A,B\"")
        void commaDelimitedStringForm() {
            ActorClaims claims = ActorClaims.from(actorJwt(Map.of("role", "ALPHA,BETA")));

            assertThat(claims.roles()).containsExactlyInAnyOrder("ALPHA", "BETA");
        }

        @Test
        @DisplayName("delimited-string form, space separated: role: \"A B\"")
        void spaceDelimitedStringForm() {
            ActorClaims claims = ActorClaims.from(actorJwt(Map.of("role", "ALPHA BETA")));

            assertThat(claims.roles()).containsExactlyInAnyOrder("ALPHA", "BETA");
        }

        @Test
        @DisplayName("mixed separators and blank parts: \"ALPHA,  BETA ,\" -> two roles, no empty entry")
        void mixedSeparatorsDropBlanks() {
            ActorClaims claims = ActorClaims.from(actorJwt(Map.of("role", "ALPHA,  BETA ,")));

            assertThat(claims.roles()).containsExactlyInAnyOrder("ALPHA", "BETA");
            assertThat(claims.roles()).doesNotContain("");
        }

        @Test
        @DisplayName("single-element string form: role: \"ALPHA\"")
        void singleStringForm() {
            ActorClaims claims = ActorClaims.from(actorJwt(Map.of("role", "ALPHA")));

            assertThat(claims.roles()).containsExactly("ALPHA");
        }

        @Test
        @DisplayName("roles takes precedence over role when both are present")
        void rolesWinsOverRole() {
            ActorClaims claims = ActorClaims.from(actorJwt(Map.of(
                    "roles", List.of("ALPHA"),
                    "role", "BETA")));

            assertThat(claims.roles()).containsExactly("ALPHA");
        }

        @Test
        @DisplayName("non-string collection elements go through String.valueOf")
        void nonStringElements() {
            ActorClaims claims = ActorClaims.from(actorJwt(Map.of("roles", List.of(1, true))));

            assertThat(claims.roles()).containsExactlyInAnyOrder("1", "true");
        }

        @Test
        @DisplayName("neither claim present -> empty role set, still a valid actor")
        void noRoleClaim() {
            ActorClaims claims = ActorClaims.from(actorJwt(Map.of()));

            assertThat(claims.roles()).isEmpty();
            assertThat(claims.accountId()).isEqualTo("acc-1");
        }

        @Test
        @DisplayName("empty array -> empty role set")
        void emptyArray() {
            ActorClaims claims = ActorClaims.from(actorJwt(Map.of("roles", List.of())));

            assertThat(claims.roles()).isEmpty();
        }

        @Test
        @DisplayName("an unsupported claim type (number) -> empty role set, NOT a throw")
        void unsupportedClaimTypeIsSilent() {
            // Every promoted copy degraded silently here. Turning it into a rejection would convert a
            // zero-authority caller into a 500 on the authentication path.
            ActorClaims claims = ActorClaims.from(actorJwt(Map.of("roles", 42)));

            assertThat(claims.roles()).isEmpty();
        }

        @Test
        @DisplayName("an unsupported claim type (object) -> empty role set, NOT a throw")
        void unsupportedObjectClaimTypeIsSilent() {
            ActorClaims claims = ActorClaims.from(actorJwt(Map.of("role", Map.of("k", "v"))));

            assertThat(claims.roles()).isEmpty();
        }

        @Test
        @DisplayName("roles.contains(null) returns false and does not throw (the Set.copyOf trap)")
        void containsNullIsFalseNotThrow() {
            // Consumers' hasRole(role) delegates to roles.contains(role). Set.copyOf(...).contains(null)
            // throws NPE where a HashSet returns false — that swap would turn a would-be `false` into a
            // thrown exception on the auth path.
            Set<String> populated = ActorClaims.from(actorJwt(Map.of("roles", List.of("ALPHA")))).roles();
            Set<String> empty = ActorClaims.from(actorJwt(Map.of())).roles();

            assertThat(populated.contains(null)).isFalse();
            assertThat(empty.contains(null)).isFalse();
        }

        @Test
        @DisplayName("the role set is unmodifiable — a caller cannot grant itself a role")
        void roleSetIsUnmodifiable() {
            Set<String> roles = ActorClaims.from(actorJwt(Map.of("roles", List.of("ALPHA")))).roles();

            assertThatThrownBy(() -> roles.add("BETA"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("authorities()")
    class Authorities {

        @Test
        @DisplayName("every role becomes exactly one ROLE_-prefixed authority")
        void rolePrefixing() {
            ActorClaims claims = ActorClaims.from(actorJwt(Map.of("roles", List.of("ALPHA", "BETA"))));

            assertThat(claims.authorities())
                    .extracting(GrantedAuthority::getAuthority)
                    .containsExactlyInAnyOrder("ROLE_ALPHA", "ROLE_BETA");
        }

        @Test
        @DisplayName("no roles -> no authorities (an authenticated caller with zero authorities)")
        void noRolesNoAuthorities() {
            assertThat(ActorClaims.from(actorJwt(Map.of())).authorities()).isEmpty();
        }

        @Test
        @DisplayName("the string claim form yields the same authorities as the array form")
        void stringFormYieldsSameAuthorities() {
            ActorClaims fromArray = ActorClaims.from(actorJwt(Map.of("roles", List.of("ALPHA", "BETA"))));
            ActorClaims fromString = ActorClaims.from(actorJwt(Map.of("role", "ALPHA BETA")));

            assertThat(fromString.authorities())
                    .extracting(GrantedAuthority::getAuthority)
                    .containsExactlyInAnyOrderElementsOf(
                            fromArray.authorities().stream().map(GrantedAuthority::getAuthority).toList());
        }
    }
}
