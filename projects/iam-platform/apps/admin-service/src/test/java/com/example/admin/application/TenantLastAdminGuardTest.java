package com.example.admin.application;

import com.example.admin.application.exception.TenantLastAdminRequiredException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.example.admin.application.OperatorUseCaseTestSupport.operator;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-MONO-784 S4 — the «a company keeps an administrator» predicate, over real rows ({@link InMemoryRoleRowsPort}).
 */
class TenantLastAdminGuardTest {

    private InMemoryRoleRowsPort port() {
        return new InMemoryRoleRowsPort().role(5L, "TENANT_ADMIN").role(2L, "SUPPORT_READONLY")
                .operator(operator(1L, "a", "a@x", "ACTIVE", "alpha"))
                .operator(operator(2L, "b", "b@x", "ACTIVE", "alpha"))
                .operator(operator(3L, "c", "c@x", "SUSPENDED", "alpha"));
    }

    @Test
    @DisplayName("🔴 removing the sole ACTIVE TENANT_ADMIN@alpha → 409; a SUSPENDED one does not count")
    void soleActiveAdmin_refused() {
        InMemoryRoleRowsPort port = port().row(1L, "TENANT_ADMIN", "alpha", null, null)
                .row(3L, "TENANT_ADMIN", "alpha", null, null);
        TenantLastAdminGuard guard = new TenantLastAdminGuard(port);

        assertThatThrownBy(() -> guard.requireAdminRemains("alpha", r -> r.operatorInternalId() == 1L, null))
                .isInstanceOf(TenantLastAdminRequiredException.class);
    }

    @Test
    @DisplayName("control: another ACTIVE admin from ANY origin (group row) remains → passes")
    void anotherAdminAnyOrigin_passes() {
        InMemoryRoleRowsPort port = port().row(1L, "TENANT_ADMIN", "alpha", null, null)
                .row(2L, "TENANT_ADMIN", "alpha", 9L, null);
        TenantLastAdminGuard guard = new TenantLastAdminGuard(port);

        assertThatCode(() -> guard.requireAdminRemains("alpha", r -> r.operatorInternalId() == 1L, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("no check when the write removes no administrator row, when T is '*', or when the write re-grants the role")
    void noCheckCases() {
        InMemoryRoleRowsPort port = port().row(1L, "TENANT_ADMIN", "alpha", null, null)
                .row(2L, "SUPPORT_READONLY", "alpha", null, null);
        TenantLastAdminGuard guard = new TenantLastAdminGuard(port);

        // target 2 is not an administrator of alpha
        assertThatCode(() -> guard.requireAdminRemains("alpha", r -> r.operatorInternalId() == 2L, null))
                .doesNotThrowAnyException();
        // the platform has no «last admin of a company»
        assertThatCode(() -> guard.requireAdminRemains("*", r -> true, null)).doesNotThrowAnyException();
        // the edit removes the direct row and writes it again
        assertThatCode(() -> guard.requireAdminRemains("alpha", r -> r.operatorInternalId() == 1L, 1L))
                .doesNotThrowAnyException();
        // another company's administrators do not count for alpha
        InMemoryRoleRowsPort other = port().row(1L, "TENANT_ADMIN", "alpha", null, null)
                .row(2L, "TENANT_ADMIN", "beta", null, null);
        assertThatThrownBy(() -> new TenantLastAdminGuard(other)
                .requireAdminRemains("alpha", r -> r.operatorInternalId() == 1L, null))
                .isInstanceOf(TenantLastAdminRequiredException.class);
    }
}
