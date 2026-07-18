package com.example.erp.readmodel.presentation.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the READ authorization gate (E6, fail-closed):
 * READ = erp.read ∨ operator ∨ entitled (no WRITE/data-scope gate — read-only).
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
        assertThatCode(() -> gate.requireRead(
                jwt(Map.of("roles", List.of("ERP_OPERATOR"), "sub", "u"))))
                .doesNotThrowAnyException();
    }

    @Test
    void entitledAllowed() {
        assertThatCode(() -> gate.requireRead(
                jwt(Map.of("entitled_domains", List.of("erp"), "sub", "u"))))
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
        // for READ (this gate guards read-model, which has no mutating endpoint — the
        // read-only invariant is structural).
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

    // ── org_scope extraction (TASK-ERP-BE-008) ──

    @Test
    void absentOrgScopeIsPlatformNetZero() {
        OrgScope scope = gate.orgScope(jwt(Map.of("scope", "erp.read", "sub", "u")));
        assertThat(scope.isPlatform()).isTrue();
    }

    @Test
    void wildcardOrgScopeIsPlatformNetZero() {
        OrgScope scope = gate.orgScope(
                jwt(Map.of("org_scope", List.of("*"), "sub", "u")));
        assertThat(scope.isPlatform()).isTrue();
    }

    @Test
    void boundedOrgScopeCarriesRoots() {
        OrgScope scope = gate.orgScope(
                jwt(Map.of("org_scope", List.of("sales-root", "eng-root"), "sub", "u")));
        assertThat(scope.isPlatform()).isFalse();
        assertThat(scope.roots()).containsExactlyInAnyOrder("sales-root", "eng-root");
    }

    @Test
    void explicitEmptyOrgScopeIsZeroScopeNotPlatform() {
        OrgScope scope = gate.orgScope(
                jwt(Map.of("org_scope", List.of(), "sub", "u")));
        assertThat(scope.isPlatform()).isFalse();
        assertThat(scope.roots()).isEmpty();
    }

    @Test
    void nullJwtOrgScopeIsPlatform() {
        assertThat(gate.orgScope(null).isPlatform()).isTrue();
    }
}
