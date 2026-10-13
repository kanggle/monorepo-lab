package com.example.admin.application;

import com.example.admin.application.exception.OperatorOwnedTenantLimitExceededException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-MONO-784 S5 (owner decision OD-1 · ADR-MONO-082 D4) — the counting unit and the verdict of the
 * per-identity onboarding cap.
 */
@DisplayName("OperatorOwnedTenantLimit — 신원당 상한 (TASK-MONO-784 S5 · OD-1)")
class OperatorOwnedTenantLimitTest {

    private static final long OP = 41L;

    private static InMemoryRoleRowsPort port() {
        return new InMemoryRoleRowsPort()
                .role(1, "TENANT_ADMIN").role(2, "TENANT_BILLING_ADMIN").role(3, "SUPPORT_LOCK").role(9, "SUPER_ADMIN");
    }

    @Test
    @DisplayName("🔴 세는 단위 = TENANT_ADMIN 역할 행의 서로 다른 테넌트(출처 무관 · '*' 제외 · 다른 역할 제외)")
    void countingUnit() {
        InMemoryRoleRowsPort p = port()
                .row(OP, "TENANT_ADMIN", "alpha", null, null)          // direct
                .row(OP, "TENANT_ADMIN", "beta", 7L, null)             // group fan-out — counted
                .row(OP, "TENANT_ADMIN", "zeta", null, "node-1")       // org-node row — counted
                .row(OP, "TENANT_BILLING_ADMIN", "alpha", null, null)  // a second role in alpha — still one tenant
                .row(OP, "TENANT_BILLING_ADMIN", "gamma", null, null)  // not TENANT_ADMIN — not counted
                .row(OP, "SUPPORT_LOCK", "delta", null, null)
                .row(OP, "TENANT_ADMIN", "*", null, null)              // '*' — not a company
                .row(99L, "TENANT_ADMIN", "epsilon", null, null);      // someone else

        assertThat(new OperatorOwnedTenantLimit(p, 2).ownedTenants(OP))
                .containsExactlyInAnyOrder("alpha", "beta", "zeta");
    }

    @Test
    @DisplayName("🔴 기본 2: 하나 소유 → 통과 · 둘 소유 → 422 OPERATOR_OWNED_TENANT_LIMIT_EXCEEDED")
    void defaultTwo_refusesAtTwo() {
        InMemoryRoleRowsPort p = port().row(OP, "TENANT_ADMIN", "alpha", null, null);
        OperatorOwnedTenantLimit limit = new OperatorOwnedTenantLimit(p, 2);

        limit.requireBelowLimit(OP); // one owned — the second company is allowed

        p.row(OP, "TENANT_ADMIN", "beta", null, null);
        assertThatThrownBy(() -> limit.requireBelowLimit(OP))
                .isInstanceOf(OperatorOwnedTenantLimitExceededException.class);
    }

    @Test
    @DisplayName("설정값을 따른다(773 AC-0 이 값을 정한다): 3 이면 둘 소유도 통과 · 0 이하는 기동 거절")
    void configurable() {
        InMemoryRoleRowsPort p = port()
                .row(OP, "TENANT_ADMIN", "alpha", null, null)
                .row(OP, "TENANT_ADMIN", "beta", null, null);
        new OperatorOwnedTenantLimit(p, 3).requireBelowLimit(OP);

        assertThatThrownBy(() -> new OperatorOwnedTenantLimit(p, 0)).isInstanceOf(IllegalStateException.class);
    }
}
