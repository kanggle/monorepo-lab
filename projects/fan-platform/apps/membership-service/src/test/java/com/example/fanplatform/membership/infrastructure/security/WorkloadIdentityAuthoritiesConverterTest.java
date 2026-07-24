package com.example.fanplatform.membership.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkloadIdentityAuthoritiesConverterTest {

    private final WorkloadIdentityAuthoritiesConverter converter = new WorkloadIdentityAuthoritiesConverter();

    private static Jwt.Builder base() {
        return Jwt.withTokenValue("t").header("alg", "RS256")
                .issuer("http://test-issuer")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300));
    }

    @Test
    @DisplayName("real IAM cc token (tenant_id present + scope=[membership.read]) → ROLE_INTERNAL")
    void realWorkloadTokenGetsInternalRole() {
        // Faithful to the live IAM shape: sub==aud==client_id, tenant-scoped
        // (tenant_id present), scope as a JSON array. TASK-FAN-BE-029: the token
        // MUST be accepted despite carrying tenant_id.
        Jwt jwt = base().subject("community-service-client")
                .audience(List.of("community-service-client"))
                .claim("tenant_id", "fan-platform")
                .claim("tenant_type", "B2C")
                .claim("scope", List.of("account.read", "membership.read"))
                .build();
        AbstractAuthenticationToken token = converter.convert(jwt);
        assertThat(token.getAuthorities())
                .extracting("authority")
                .contains(WorkloadIdentityAuthoritiesConverter.ROLE_INTERNAL);
    }

    @Test
    @DisplayName("scope as space-delimited string is also accepted")
    void spaceDelimitedScopeAccepted() {
        Jwt jwt = base().subject("community-service-client")
                .claim("tenant_id", "fan-platform")
                .claim("scope", "account.read membership.read")
                .build();
        assertThat(converter.convert(jwt).getAuthorities())
                .extracting("authority")
                .contains(WorkloadIdentityAuthoritiesConverter.ROLE_INTERNAL);
    }

    @Test
    @DisplayName("scp array claim is also honored")
    void scpArrayAccepted() {
        Jwt jwt = base().subject("community-service-client")
                .claim("tenant_id", "fan-platform")
                .claim("scp", List.of("membership.read"))
                .build();
        assertThat(converter.convert(jwt).getAuthorities())
                .extracting("authority")
                .contains(WorkloadIdentityAuthoritiesConverter.ROLE_INTERNAL);
    }

    @Test
    @DisplayName("end-user token (tenant_id + user scopes, no membership.read) → NO ROLE_INTERNAL")
    void endUserGetsNoRole() {
        // A fan end-user token carries user scopes, never the membership.read
        // workload scope — so it is rejected even though it is tenant-scoped.
        Jwt jwt = base().subject("9ab12f7c-account")
                .claim("tenant_id", "fan-platform")
                .claim("roles", List.of("FAN"))
                .claim("scope", List.of("openid", "profile", "email", "tenant.read"))
                .build();
        AbstractAuthenticationToken token = converter.convert(jwt);
        assertThat(token.getAuthorities()).isEmpty();
    }

    @Test
    @DisplayName("token without the required workload scope → NO ROLE_INTERNAL")
    void bareTokenGetsNoRole() {
        Jwt jwt = base().subject("acc1").build();
        AbstractAuthenticationToken token = converter.convert(jwt);
        assertThat(token.getAuthorities()).isEmpty();
    }
}
