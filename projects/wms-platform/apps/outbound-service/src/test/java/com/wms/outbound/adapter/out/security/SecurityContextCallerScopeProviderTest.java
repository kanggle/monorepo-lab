package com.wms.outbound.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.outbound.application.security.CallerScope;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Unit tests for {@link SecurityContextCallerScopeProvider} (TASK-MONO-304):
 * scope is derived ONLY from the signed {@code tenant_id} claim.
 */
class SecurityContextCallerScopeProviderTest {

    private final SecurityContextCallerScopeProvider provider =
            new SecurityContextCallerScopeProvider("wms");

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateWithTenant(String tenantId) {
        Jwt.Builder b = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("http://localhost:8081")
                .subject("operator-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("entitled_domains", List.of("wms"));
        if (tenantId != null) {
            b.claim("tenant_id", tenantId);
        }
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(b.build()));
    }

    @Test
    void noAuthentication_isUnrestricted() {
        CallerScope scope = provider.current();
        assertThat(scope.isRestricted()).isFalse();
    }

    @Test
    void nativeWmsTenant_isUnrestricted() {
        authenticateWithTenant("wms");
        assertThat(provider.current().isRestricted()).isFalse();
    }

    @Test
    void platformWildcardTenant_isUnrestricted() {
        authenticateWithTenant("*");
        assertThat(provider.current().isRestricted()).isFalse();
    }

    @Test
    void missingTenantClaim_isUnrestricted() {
        authenticateWithTenant(null);
        assertThat(provider.current().isRestricted()).isFalse();
    }

    @Test
    void customerTenant_isRestrictedToThatTenant() {
        authenticateWithTenant("ecommerce");
        CallerScope scope = provider.current();
        assertThat(scope.isRestricted()).isTrue();
        assertThat(scope.tenantId()).isEqualTo("ecommerce");
    }
}
