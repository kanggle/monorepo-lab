package com.example.security.servlet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Promoted from membership-service by TASK-MONO-521, plus the cases the shared shape makes
 * newly assertable (policy isolation, blank-scope wiring error).
 *
 * <p>Scopes here are deliberately generic placeholders. Naming a real service's scope would put
 * project-specific content in {@code libs/} (HARDSTOP-03) — and would also hide the property under
 * test, which is that the converter matches <em>whatever</em> scope it was constructed with.
 */
class WorkloadIdentityAuthoritiesConverterTest {

    private static final String REQUIRED = "svc.read";
    private static final String OTHER_SERVICE_SCOPE = "othersvc.read";

    private final WorkloadIdentityAuthoritiesConverter converter =
            new WorkloadIdentityAuthoritiesConverter(REQUIRED);

    private static Jwt.Builder base() {
        return Jwt.withTokenValue("t").header("alg", "RS256")
                .issuer("http://test-issuer")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300));
    }

    @Nested
    @DisplayName("grants ROLE_INTERNAL — the three scope shapes an issuer may emit")
    class Grants {

        @Test
        @DisplayName("real machine token (tenant_id present + scope JSON array) → ROLE_INTERNAL")
        void jsonArrayScope() {
            // Faithful to the live IAM shape: sub == aud == client_id, tenant-scoped
            // (tenant_id PRESENT), scope as a JSON array. TASK-FAN-BE-029: the token MUST be
            // accepted despite carrying tenant_id — that is the whole point of the positive
            // discriminator, and the reason no tenant_id check appears in the converter.
            Jwt jwt = base().subject("some-service-client")
                    .audience(List.of("some-service-client"))
                    .claim("tenant_id", "some-tenant")
                    .claim("tenant_type", "B2C")
                    .claim("scope", List.of("account.read", REQUIRED))
                    .build();
            assertThat(converter.convert(jwt).getAuthorities())
                    .extracting("authority")
                    .contains(WorkloadIdentityAuthoritiesConverter.ROLE_INTERNAL);
        }

        @Test
        @DisplayName("scope as a space-delimited string is also accepted")
        void spaceDelimitedScope() {
            Jwt jwt = base().subject("some-service-client")
                    .claim("tenant_id", "some-tenant")
                    .claim("scope", "account.read " + REQUIRED)
                    .build();
            assertThat(converter.convert(jwt).getAuthorities())
                    .extracting("authority")
                    .contains(WorkloadIdentityAuthoritiesConverter.ROLE_INTERNAL);
        }

        @Test
        @DisplayName("the scp array claim is also honored")
        void scpArray() {
            Jwt jwt = base().subject("some-service-client")
                    .claim("tenant_id", "some-tenant")
                    .claim("scp", List.of(REQUIRED))
                    .build();
            assertThat(converter.convert(jwt).getAuthorities())
                    .extracting("authority")
                    .contains(WorkloadIdentityAuthoritiesConverter.ROLE_INTERNAL);
        }
    }

    @Nested
    @DisplayName("withholds ROLE_INTERNAL")
    class Withholds {

        @Test
        @DisplayName("end-user token (tenant_id + user scopes, not the workload scope) → no authority")
        void endUserToken() {
            Jwt jwt = base().subject("9ab12f7c-account")
                    .claim("tenant_id", "some-tenant")
                    .claim("roles", List.of("SOME_ROLE"))
                    .claim("scope", List.of("openid", "profile", "email", "tenant.read"))
                    .build();
            assertThat(converter.convert(jwt).getAuthorities()).isEmpty();
        }

        @Test
        @DisplayName("token with no scope claim at all → no authority")
        void bareToken() {
            assertThat(converter.convert(base().subject("acc1").build()).getAuthorities()).isEmpty();
        }

        @Test
        @DisplayName("POLICY IS NOT SHARED: another service's workload scope does not open this one")
        void anotherServicesWorkloadScope() {
            // The reason the scope is a constructor parameter rather than a constant on this class.
            // Both scopes are machine-only, so a mechanism that merely asked "is this a machine
            // token?" would pass this token — and one service's internal surface would be reachable
            // with any sibling service's credential. Promotion must not buy that.
            Jwt jwt = base().subject("other-service-client")
                    .claim("tenant_id", "some-tenant")
                    .claim("scope", List.of("account.read", OTHER_SERVICE_SCOPE))
                    .build();
            assertThat(converter.convert(jwt).getAuthorities()).isEmpty();
        }

        @Test
        @DisplayName("a scope that merely contains the required one as a prefix does not match")
        void prefixIsNotAMatch() {
            Jwt jwt = base().subject("some-service-client")
                    .claim("scope", List.of(REQUIRED + ".extra", "x" + REQUIRED))
                    .build();
            assertThat(converter.convert(jwt).getAuthorities()).isEmpty();
        }
    }

    @Nested
    @DisplayName("wiring errors surface at construction, not as a silent 403")
    class Wiring {

        @Test
        @DisplayName("blank or null required scope is rejected when the converter is built")
        void blankScopeRejected() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new WorkloadIdentityAuthoritiesConverter("  "));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new WorkloadIdentityAuthoritiesConverter(""));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new WorkloadIdentityAuthoritiesConverter(null));
        }

        @Test
        @DisplayName("the configured scope is readable back")
        void scopeIsReadable() {
            assertThat(converter.requiredWorkloadScope()).isEqualTo(REQUIRED);
        }
    }

    @Test
    @DisplayName("the returned token always carries the JWT as principal, granted or not")
    void principalIsAlwaysTheJwt() {
        Jwt granted = base().claim("scope", List.of(REQUIRED)).build();
        Jwt denied = base().claim("scope", List.of(OTHER_SERVICE_SCOPE)).build();
        AbstractAuthenticationToken grantedToken = converter.convert(granted);
        AbstractAuthenticationToken deniedToken = converter.convert(denied);
        assertThat(grantedToken.getPrincipal()).isSameAs(granted);
        assertThat(deniedToken.getPrincipal()).isSameAs(denied);
    }
}
