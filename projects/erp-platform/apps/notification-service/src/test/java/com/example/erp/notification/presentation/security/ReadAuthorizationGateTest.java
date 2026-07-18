package com.example.erp.notification.presentation.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * READ authorization gate (E6, fail-closed): READ = erp.read ∨ operator ∨
 * entitled.
 */
class ReadAuthorizationGateTest {

    private final ReadAuthorizationGate gate = new ReadAuthorizationGate("erp");

    private static Jwt jwt(Map<String, Object> claims) {
        return new Jwt("t", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "RS256"), claims);
    }

    @Test
    void erpReadScopeAllowed() {
        assertThatCode(() -> gate.requireRead(jwt(Map.of("scope", "erp.read", "sub", "u"))))
                .doesNotThrowAnyException();
    }

    @Test
    void operatorRoleAllowed() {
        assertThatCode(() -> gate.requireRead(jwt(Map.of("roles", List.of("ERP_OPERATOR"), "sub", "u"))))
                .doesNotThrowAnyException();
    }

    @Test
    void entitledAllowed() {
        assertThatCode(() -> gate.requireRead(jwt(Map.of("entitled_domains", List.of("erp"), "sub", "u"))))
                .doesNotThrowAnyException();
    }

    @Test
    void noScopeRoleOrEntitlementDenied() {
        assertThatThrownBy(() -> gate.requireRead(jwt(Map.of("sub", "u"))))
                .isInstanceOf(ReadAccessDeniedException.class);
    }

    @Test
    void nullJwtDenied() {
        assertThatThrownBy(() -> gate.requireRead(null))
                .isInstanceOf(ReadAccessDeniedException.class);
    }

    // ── platform-super-admin wildcard READ authority (TASK-ERP-BE-031, erp analogue of FIN-BE-049) ──

    @Test
    void superAdminWildcardTenantReadAllowed() {
        // The platform-console super-admin's forwarded base OIDC domain token:
        // tenant_id='*', no erp scope, no domain roles, no entitled_domains. Admitted
        // for the READ gate; the only POST (mark-read) is a self-scoped, recipient-owned
        // read-adjacent action, so this never widens an org-wide mutation.
        assertThatCode(() -> gate.requireRead(jwt(Map.of("tenant_id", "*", "sub", "u"))))
                .doesNotThrowAnyException();
    }

    @Test
    void nonWildcardTenantWithoutScopeStillDenied() {
        // Strict keying: admission is on the '*' literal, not on "authenticated" — a
        // concrete non-wildcard tenant with no scope/role/entitlement still 403s.
        assertThatThrownBy(() -> gate.requireRead(jwt(Map.of("tenant_id", "erp", "sub", "u"))))
                .isInstanceOf(ReadAccessDeniedException.class);
    }
}
