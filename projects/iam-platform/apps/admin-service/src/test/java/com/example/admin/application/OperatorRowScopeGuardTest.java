package com.example.admin.application;

import com.example.admin.application.exception.OperatorHasOtherMembershipsException;
import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.persistence.rbac.AdminGrantScopeEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Set;

import static com.example.admin.application.OperatorUseCaseTestSupport.operator;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-784 S4 — the row-wide rule (rider R3 · D-8 · admin-api.md § 행동 테넌트 «행 전체 규칙»), cell by cell.
 * 🔴 failing cells first in each test (784 AC-4).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class OperatorRowScopeGuardTest {

    @Mock AdminGrantScopeEvaluator grantScopeEvaluator;
    @Mock TenantScopeResolver tenantScopeResolver;
    @Mock AdminActionAuditor auditor;

    OperatorRowScopeGuard guard;

    static final OperatorContext ACTOR = new OperatorContext("actor-uuid", "jti");
    static final String PERM = Permission.OPERATOR_MANAGE;
    static final ActionCode CODE = ActionCode.OPERATOR_STATUS_CHANGE;

    @BeforeEach
    void init() {
        guard = new OperatorRowScopeGuard(grantScopeEvaluator, tenantScopeResolver, auditor);
    }

    private void actorScope(String... tenants) {
        when(grantScopeEvaluator.effectiveAdminScope("actor-uuid", PERM)).thenReturn(Set.of(tenants));
    }

    @Test
    @DisplayName("🔴 AC-4: two memberships {alpha, beta} — company admin of alpha → 403 OPERATOR_HAS_OTHER_MEMBERSHIPS + DENIED row / '*' → passes")
    void twoMemberships_companyAdminRefused_platformPasses() {
        AdminOperatorPort.OperatorView target = operator(7L, "t", "t@x", "ACTIVE", "alpha");
        when(tenantScopeResolver.resolveEffectiveTenantScope(7L, "alpha")).thenReturn(Set.of("alpha", "beta"));
        actorScope("alpha");

        assertThatThrownBy(() -> guard.requireRowAdministrable(ACTOR, PERM, target, CODE))
                .isInstanceOf(OperatorHasOtherMembershipsException.class);
        verify(auditor).recordCrossTenantDenied(eq(ACTOR), isNull(), eq(CODE), eq(PERM), eq("alpha"));

        // control — the '*' grant scope passes, without even reading the target's memberships again
        when(grantScopeEvaluator.effectiveAdminScope("actor-uuid", PERM)).thenReturn(Set.of("*"));
        assertThatCode(() -> guard.requireRowAdministrable(ACTOR, PERM, target, CODE)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("two memberships, actor administers neither → 403 TENANT_SCOPE_DENIED (count not disclosed — S1-2)")
    void twoMemberships_strangerGetsPlainScopeDenied() {
        AdminOperatorPort.OperatorView target = operator(7L, "t", "t@x", "ACTIVE", "alpha");
        when(tenantScopeResolver.resolveEffectiveTenantScope(7L, "alpha")).thenReturn(Set.of("alpha", "beta"));
        actorScope("gamma");

        assertThatThrownBy(() -> guard.requireRowAdministrable(ACTOR, PERM, target, CODE))
                .isInstanceOf(TenantScopeDeniedException.class);
    }

    @Test
    @DisplayName("🔴 F11: SUPER_ADMIN as a COMPANY row (scope {alpha}) is not the platform — two-company target refused")
    void companyScopedSuperAdmin_isNotPlatform() {
        AdminOperatorPort.OperatorView target = operator(7L, "t", "t@x", "ACTIVE", "alpha");
        when(tenantScopeResolver.resolveEffectiveTenantScope(7L, "alpha")).thenReturn(Set.of("alpha", "beta"));
        actorScope("alpha"); // what SUPER_ADMIN@alpha resolves to — the role NAME is never read

        assertThatThrownBy(() -> guard.requireRowAdministrable(ACTOR, PERM, target, CODE))
                .isInstanceOf(OperatorHasOtherMembershipsException.class);
    }

    @Test
    @DisplayName("control (AC-4 «소속 하나 = 지금과 같다»): one membership {beta} — admin of beta passes; admin of alpha (target's HOME) refused")
    void oneMembership_thatCompanysAdmin_notTheHomes() {
        // R1: the home (alpha) is not read — the person left alpha and belongs to beta only.
        AdminOperatorPort.OperatorView target = operator(7L, "t", "t@x", "ACTIVE", "alpha");
        when(tenantScopeResolver.resolveEffectiveTenantScope(7L, "alpha")).thenReturn(Set.of("beta"));

        actorScope("alpha");
        assertThatThrownBy(() -> guard.requireRowAdministrable(ACTOR, PERM, target, CODE))
                .isInstanceOf(TenantScopeDeniedException.class);
        verify(auditor).recordCrossTenantDenied(eq(ACTOR), isNull(), eq(CODE), eq(PERM), eq("beta"));

        actorScope("beta");
        assertThatCode(() -> guard.requireRowAdministrable(ACTOR, PERM, target, CODE)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("zero memberships (non-'*', removed from every company) → '*' only")
    void zeroMemberships_platformOnly() {
        AdminOperatorPort.OperatorView target = operator(7L, "t", "t@x", "ACTIVE", "alpha");
        when(tenantScopeResolver.resolveEffectiveTenantScope(7L, "alpha")).thenReturn(Set.of());
        actorScope("alpha");

        assertThatThrownBy(() -> guard.requireRowAdministrable(ACTOR, PERM, target, CODE))
                .isInstanceOf(TenantScopeDeniedException.class);
    }

    @Test
    @DisplayName("'*' target → '*' grant scope only (a company admin is refused)")
    void platformTarget_platformOnly() {
        AdminOperatorPort.OperatorView target = operator(7L, "t", "t@x", "ACTIVE", "*");
        when(tenantScopeResolver.resolveEffectiveTenantScope(7L, "*")).thenReturn(Set.of("*"));
        actorScope("alpha");

        assertThatThrownBy(() -> guard.requireRowAdministrable(ACTOR, PERM, target, CODE))
                .isInstanceOf(TenantScopeDeniedException.class);
        verify(auditor, never()).recordCrossTenantDenied(any(), any(), any(), anyString(), eq("alpha"));
        verify(tenantScopeResolver).resolveEffectiveTenantScope(anyLong(), anyString());
    }
}
