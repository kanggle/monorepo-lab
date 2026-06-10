package com.example.admin.application;

import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.persistence.rbac.AdminGrantScopeEvaluator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ADR-MONO-024 D2 (TASK-BE-345) — unit coverage for the central confinement gate:
 * in-scope passes silently; out-of-scope writes a best-effort cross-tenant DENIED
 * row and throws {@link TenantScopeDeniedException}.
 */
@ExtendWith(MockitoExtension.class)
class TenantScopeGuardTest {

    @Mock AdminGrantScopeEvaluator grantScopeEvaluator;
    @Mock AdminActionAuditor auditor;

    @InjectMocks TenantScopeGuard guard;

    private final OperatorContext actor = new OperatorContext("actor-uuid", "jti-1");

    @Test
    @DisplayName("in-scope target → returns silently, no DENIED audit row")
    void inScope_passes() {
        when(grantScopeEvaluator.isTenantInAdminScope("actor-uuid", Permission.OPERATOR_MANAGE, "acme"))
                .thenReturn(true);

        assertThatCode(() -> guard.requireTenantInScope(
                actor, Permission.OPERATOR_MANAGE, "acme", ActionCode.OPERATOR_ROLE_CHANGE))
                .doesNotThrowAnyException();

        verify(auditor, never()).recordCrossTenantDenied(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("out-of-scope target → DENIED audit row + TenantScopeDeniedException")
    void outOfScope_deniesAndAudits() {
        when(grantScopeEvaluator.isTenantInAdminScope("actor-uuid", Permission.OPERATOR_MANAGE, "globex"))
                .thenReturn(false);

        assertThatThrownBy(() -> guard.requireTenantInScope(
                actor, Permission.OPERATOR_MANAGE, "globex", ActionCode.OPERATOR_ROLE_CHANGE))
                .isInstanceOf(TenantScopeDeniedException.class);

        verify(auditor).recordCrossTenantDenied(
                eq(actor), isNull(), eq(ActionCode.OPERATOR_ROLE_CHANGE),
                eq(Permission.OPERATOR_MANAGE), eq("globex"));
    }
}
