package com.example.fanplatform.membership.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class WorkloadIdentityAuthoritiesConverterTest {

    private final WorkloadIdentityAuthoritiesConverter converter = new WorkloadIdentityAuthoritiesConverter();

    private static Jwt.Builder base() {
        return Jwt.withTokenValue("t").header("alg", "RS256")
                .issuer("http://test-issuer")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300));
    }

    @Test
    @DisplayName("client_credentials token (no tenant_id, scope + client_id) → ROLE_INTERNAL")
    void workloadGetsInternalRole() {
        Jwt jwt = base().subject("svc-community")
                .claim("client_id", "svc-community")
                .claim("scope", "internal.membership.read")
                .build();
        AbstractAuthenticationToken token = converter.convert(jwt);
        assertThat(token.getAuthorities())
                .extracting("authority")
                .contains(WorkloadIdentityAuthoritiesConverter.ROLE_INTERNAL);
    }

    @Test
    @DisplayName("end-user token (tenant_id present) → NO ROLE_INTERNAL")
    void endUserGetsNoRole() {
        Jwt jwt = base().subject("acc1")
                .claim("tenant_id", "fan-platform")
                .claim("roles", "FAN")
                .build();
        AbstractAuthenticationToken token = converter.convert(jwt);
        assertThat(token.getAuthorities()).isEmpty();
    }

    @Test
    @DisplayName("token without machine markers and without tenant_id → NO ROLE_INTERNAL")
    void bareTokenGetsNoRole() {
        Jwt jwt = base().subject("acc1").build();
        AbstractAuthenticationToken token = converter.convert(jwt);
        assertThat(token.getAuthorities()).isEmpty();
    }
}
