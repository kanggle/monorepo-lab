package com.example.admin.application;

import com.example.admin.domain.rbac.AdminOperator;
import com.example.admin.domain.rbac.PermissionEvaluator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link PermissionEvaluator#isTenantAllowed} default method.
 *
 * <p>TASK-BE-249: five canonical scenarios per task spec §Implementation Notes.
 *
 * <ol>
 *   <li>Platform-scope operator (tenantId='*') → always true regardless of targetTenantId</li>
 *   <li>Normal operator querying own tenant → true</li>
 *   <li>Normal operator querying different tenant → false</li>
 *   <li>targetTenantId=null (null-safe legacy compat) → defaults to operator's own tenant → true</li>
 *   <li>operator=null → always false (null-safe)</li>
 * </ol>
 */
@DisplayName("PermissionEvaluator.isTenantAllowed 단위 테스트 (TASK-BE-249)")
class PermissionEvaluatorTenantScopeTest {

    /** Minimal no-op implementation — only the default method under test. */
    private static final PermissionEvaluator EVALUATOR = new PermissionEvaluator() {
        @Override
        public boolean hasPermission(String operatorId, String permission) {
            return false;
        }

        @Override
        public boolean hasAllPermissions(String operatorId, Collection<String> permissions) {
            return false;
        }
    };

    // ── Scenario 1: platform-scope operator ────────────────────────────────────

    @Test
    @DisplayName("1. platform-scope(tenantId='*') + specific targetTenantId → true")
    void platformScope_specificTarget_returnsTrue() {
        AdminOperator superAdmin = operator("*");
        assertThat(EVALUATOR.isTenantAllowed(superAdmin, "fan-platform")).isTrue();
    }

    @Test
    @DisplayName("1b. platform-scope(tenantId='*') + targetTenantId='*' → true")
    void platformScope_starTarget_returnsTrue() {
        AdminOperator superAdmin = operator("*");
        assertThat(EVALUATOR.isTenantAllowed(superAdmin, "*")).isTrue();
    }

    @Test
    @DisplayName("1c. platform-scope(tenantId='*') + targetTenantId=null → true")
    void platformScope_nullTarget_returnsTrue() {
        AdminOperator superAdmin = operator("*");
        assertThat(EVALUATOR.isTenantAllowed(superAdmin, null)).isTrue();
    }

    // ── Scenario 2: normal operator, own tenant ────────────────────────────────

    @Test
    @DisplayName("2. normal operator querying own tenant → true")
    void normalOperator_ownTenant_returnsTrue() {
        AdminOperator op = operator("fan-platform");
        assertThat(EVALUATOR.isTenantAllowed(op, "fan-platform")).isTrue();
    }

    // ── Scenario 3: normal operator, different tenant ─────────────────────────

    @Test
    @DisplayName("3. normal operator querying different tenant → false")
    void normalOperator_differentTenant_returnsFalse() {
        AdminOperator op = operator("fan-platform");
        assertThat(EVALUATOR.isTenantAllowed(op, "other-platform")).isFalse();
    }

    @Test
    @DisplayName("3b. normal operator querying platform-scope sentinel '*' → false")
    void normalOperator_platformSentinelTarget_returnsFalse() {
        AdminOperator op = operator("fan-platform");
        assertThat(EVALUATOR.isTenantAllowed(op, "*")).isFalse();
    }

    // ── Scenario 4: targetTenantId=null legacy compat ─────────────────────────

    @Test
    @DisplayName("4. targetTenantId=null → defaults to operator's own tenant → true")
    void normalOperator_nullTarget_defaultsToOwnTenant_returnsTrue() {
        AdminOperator op = operator("fan-platform");
        assertThat(EVALUATOR.isTenantAllowed(op, null)).isTrue();
    }

    // ── Scenario 5: operator=null ─────────────────────────────────────────────

    @Test
    @DisplayName("5. operator=null → always false (null-safe)")
    void nullOperator_returnsFalse() {
        assertThat(EVALUATOR.isTenantAllowed(null, "fan-platform")).isFalse();
        assertThat(EVALUATOR.isTenantAllowed(null, null)).isFalse();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static AdminOperator operator(String tenantId) {
        return new AdminOperator("op-id", "op@ex.com", "Op",
                AdminOperator.Status.ACTIVE, 0L, tenantId);
    }
}
