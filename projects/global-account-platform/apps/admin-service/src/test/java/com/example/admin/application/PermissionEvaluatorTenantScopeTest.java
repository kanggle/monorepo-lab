package com.example.admin.application;

import com.example.admin.domain.rbac.AdminOperator;
import com.example.admin.domain.rbac.PermissionEvaluator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Set;

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

    // ── TASK-BE-326: 3-arg dual-read overload ──────────────────────────────────

    @Test
    @DisplayName("BE-326: net-zero — effective={home} → home tenant allowed, other denied (legacy parity)")
    void dualRead_netZero_homeAllowed_otherDenied() {
        AdminOperator op = operator("fan-platform");
        Set<String> effective = Set.of("fan-platform"); // no assignments
        assertThat(EVALUATOR.isTenantAllowed(op, "fan-platform", effective)).isTrue();
        assertThat(EVALUATOR.isTenantAllowed(op, "other-platform", effective)).isFalse();
    }

    @Test
    @DisplayName("BE-326: assigned non-home tenant in effective set → allowed (union membership)")
    void dualRead_assignedNonHomeTenant_allowed() {
        AdminOperator op = operator("wms");
        Set<String> effective = Set.of("wms", "scm"); // assignment → scm
        assertThat(EVALUATOR.isTenantAllowed(op, "scm", effective))
                .as("assigned tenant scm is a member of the effective scope → allowed")
                .isTrue();
        assertThat(EVALUATOR.isTenantAllowed(op, "wms", effective)).isTrue();
        assertThat(EVALUATOR.isTenantAllowed(op, "erp", effective))
                .as("erp neither home nor assigned → denied")
                .isFalse();
    }

    @Test
    @DisplayName("BE-326: platform-scope short-circuits regardless of effective set")
    void dualRead_platformScope_alwaysAllowed() {
        AdminOperator superAdmin = operator("*");
        assertThat(EVALUATOR.isTenantAllowed(superAdmin, "anything", Set.of("*"))).isTrue();
        assertThat(EVALUATOR.isTenantAllowed(superAdmin, null, Set.of("*"))).isTrue();
    }

    @Test
    @DisplayName("BE-326: null/empty effective set falls back to {operator.tenantId()} (legacy compat)")
    void dualRead_nullEffective_fallsBackToOwnTenant() {
        AdminOperator op = operator("fan-platform");
        assertThat(EVALUATOR.isTenantAllowed(op, "fan-platform", null)).isTrue();
        assertThat(EVALUATOR.isTenantAllowed(op, "other", null)).isFalse();
        assertThat(EVALUATOR.isTenantAllowed(op, null, Set.of())).isTrue(); // null target → own tenant
    }

    @Test
    @DisplayName("BE-326: operator=null → false (null-safe)")
    void dualRead_nullOperator_false() {
        assertThat(EVALUATOR.isTenantAllowed(null, "fan-platform", Set.of("fan-platform"))).isFalse();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static AdminOperator operator(String tenantId) {
        return new AdminOperator("op-id", "op@ex.com", "Op",
                AdminOperator.Status.ACTIVE, 0L, tenantId, null);
    }
}
