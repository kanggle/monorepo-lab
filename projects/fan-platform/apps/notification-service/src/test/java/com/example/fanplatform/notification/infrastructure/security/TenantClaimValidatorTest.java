package com.example.fanplatform.notification.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TenantClaimValidatorTest {

    private final TenantClaimValidator validator = new TenantClaimValidator("fan-platform");

    private static Jwt jwtWithTenant(String tenantId) {
        Jwt.Builder b = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("acc-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        if (tenantId != null) {
            b.claim("tenant_id", tenantId);
        }
        return b.build();
    }

    @Test
    void acceptsMatchingTenant() {
        assertThat(validator.validate(jwtWithTenant("fan-platform")).hasErrors()).isFalse();
    }

    @Test
    void acceptsWildcardTenant() {
        assertThat(validator.validate(jwtWithTenant("*")).hasErrors()).isFalse();
    }

    @Test
    void rejectsMissingTenant() {
        assertThat(validator.validate(jwtWithTenant(null)).hasErrors()).isTrue();
    }

    @Test
    void rejectsForeignTenant() {
        assertThat(validator.validate(jwtWithTenant("wms")).hasErrors()).isTrue();
    }
}
