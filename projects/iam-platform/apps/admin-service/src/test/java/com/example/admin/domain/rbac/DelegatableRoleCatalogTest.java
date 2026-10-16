package com.example.admin.domain.rbac;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-479 / ADR-MONO-045 D3 — unit tests for {@link DelegatableRoleCatalog}.
 *
 * <p>Pins the allowlist to the exact flattened value set of auth-service
 * {@code OperatorRoleDerivation} (AC-5) and proves admin-tier / tenant-admin roles are
 * NOT delegatable.
 */
class DelegatableRoleCatalogTest {

    @Test
    @DisplayName("the allowlist is exactly the flattened OperatorRoleDerivation operator-role set")
    void allowlist_isExactOperatorRoleSet() {
        assertThat(DelegatableRoleCatalog.DELEGATABLE_OPERATOR_ROLES)
                .containsExactlyInAnyOrder(
                        // wms (granular operator-tier, TASK-BE-433)
                        "WMS_OPERATOR",
                        "OUTBOUND_READ", "OUTBOUND_WRITE",
                        "INBOUND_READ", "INBOUND_WRITE",
                        "INVENTORY_READ", "INVENTORY_WRITE",
                        "MASTER_READ",
                        // ecommerce operator role
                        "ECOMMERCE_OPERATOR",
                        // scm — fine-grained since TASK-MONO-792 (SCM_OPERATOR is derived, not delegatable)
                        "SCM_PROCUREMENT_READ", "SCM_PROCUREMENT_WRITE",
                        "SCM_DEMAND_PLANNING_READ", "SCM_DEMAND_PLANNING_WRITE",
                        "SCM_LOGISTICS_READ", "SCM_LOGISTICS_WRITE",
                        "SCM_INVENTORY_VISIBILITY_READ", "SCM_INVENTORY_VISIBILITY_WRITE",
                        // erp — fine-grained since TASK-MONO-793 (ERP_OPERATOR is derived, not delegatable)
                        "ERP_MASTERDATA_READ", "ERP_MASTERDATA_WRITE", "ERP_APPROVAL_READ", "ERP_APPROVAL_WRITE",
                        // single-role domains
                        "FINANCE_OPERATOR",
                        "MES_OPERATOR", "FAN_OPERATOR");
    }

    /**
     * TASK-BE-631 (ADR-MONO-083 D2) — the allow-list is now DERIVED from {@link DomainRoleCatalog}. Its content must
     * be byte-identical to the hand-kept literal it replaced (copied verbatim below from the pre-BE-631 source), so
     * the merge to one source changed no partnership's delegatable set: a catalog row added or removed later turns
     * this red and has to be decided as a partnership change too.
     */
    @Test
    @DisplayName("BE-631 + 792: the allow-list is derived from DomainRoleCatalog = the pre-BE-631 literal with SCM_OPERATOR swapped for the eight SCM roles")
    void allowlist_derivedFromDomainRoleCatalog_byteIdenticalToTheReplacedLiteral() {
        java.util.Set<String> preBe631Literal = java.util.Set.of(
                "WMS_OPERATOR",
                "OUTBOUND_READ", "OUTBOUND_WRITE",
                "INBOUND_READ", "INBOUND_WRITE",
                "INVENTORY_READ", "INVENTORY_WRITE",
                "MASTER_READ",
                "ECOMMERCE_OPERATOR",
                "SCM_OPERATOR",
                "ERP_OPERATOR",
                "FINANCE_OPERATOR",
                "MES_OPERATOR",
                "FAN_OPERATOR");
        // TASK-MONO-792 — the ONE deliberate partnership change: a host may delegate SCM business roles
        // (the delegate's SCM_OPERATOR is derived by auth); existing delegations were rewritten by V0056.
        java.util.Set<String> expected = new java.util.HashSet<>(preBe631Literal);
        expected.remove("SCM_OPERATOR");
        expected.addAll(List.of("SCM_PROCUREMENT_READ", "SCM_PROCUREMENT_WRITE", "SCM_DEMAND_PLANNING_READ",
                "SCM_DEMAND_PLANNING_WRITE", "SCM_LOGISTICS_READ", "SCM_LOGISTICS_WRITE",
                "SCM_INVENTORY_VISIBILITY_READ", "SCM_INVENTORY_VISIBILITY_WRITE"));
        // TASK-MONO-793 — the same change for ERP: ERP_OPERATOR → the four ERP business roles (V0057).
        expected.remove("ERP_OPERATOR");
        expected.addAll(List.of("ERP_MASTERDATA_READ", "ERP_MASTERDATA_WRITE",
                "ERP_APPROVAL_READ", "ERP_APPROVAL_WRITE"));
        assertThat(DelegatableRoleCatalog.DELEGATABLE_OPERATOR_ROLES).isEqualTo(expected);
        assertThat(DelegatableRoleCatalog.DELEGATABLE_OPERATOR_ROLES).isEqualTo(DomainRoleCatalog.roleNames());
        assertThat(DelegatableRoleCatalog.DELEGATABLE_OPERATOR_ROLES).hasSize(24);
    }

    @Test
    @DisplayName("operator-tier roles are delegatable")
    void operatorRoles_delegatable() {
        assertThat(DelegatableRoleCatalog.isDelegatable("WMS_OPERATOR")).isTrue();
        assertThat(DelegatableRoleCatalog.isDelegatable("OUTBOUND_WRITE")).isTrue();
        assertThat(DelegatableRoleCatalog.isDelegatable("FINANCE_OPERATOR")).isTrue();
    }

    @Test
    @DisplayName("admin-tier domain roles and tenant-admin roles are NOT delegatable")
    void adminRoles_notDelegatable() {
        // admin-tier domain roles (deliberately withheld by OperatorRoleDerivation)
        assertThat(DelegatableRoleCatalog.isDelegatable("WMS_ADMIN")).isFalse();
        assertThat(DelegatableRoleCatalog.isDelegatable("SCM_ADMIN")).isFalse();
        // tenant-admin roles (also caught by ScopeSet.containsAdminRole)
        for (String adminRole : ScopeSet.ADMIN_ROLE_NAMES) {
            assertThat(DelegatableRoleCatalog.isDelegatable(adminRole))
                    .as("tenant-admin role %s must not be delegatable", adminRole)
                    .isFalse();
        }
        // unknown / null
        assertThat(DelegatableRoleCatalog.isDelegatable("MADE_UP_ROLE")).isFalse();
        assertThat(DelegatableRoleCatalog.isDelegatable(null)).isFalse();
    }

    @Test
    @DisplayName("firstNonDelegatable returns the offending role, or null when all delegatable")
    void firstNonDelegatable_reportsOffender() {
        assertThat(DelegatableRoleCatalog.firstNonDelegatable(
                List.of("WMS_OPERATOR", "SCM_PROCUREMENT_READ"))).isNull();
        // TASK-MONO-792: the SCM entry role is derived, never delegated
        assertThat(DelegatableRoleCatalog.firstNonDelegatable(
                List.of("WMS_OPERATOR", "SCM_OPERATOR"))).isEqualTo("SCM_OPERATOR");
        // TASK-MONO-793: the ERP entry role likewise
        assertThat(DelegatableRoleCatalog.firstNonDelegatable(
                List.of("ERP_APPROVAL_READ", "ERP_OPERATOR"))).isEqualTo("ERP_OPERATOR");
        assertThat(DelegatableRoleCatalog.firstNonDelegatable(
                List.of("WMS_OPERATOR", "WMS_ADMIN"))).isEqualTo("WMS_ADMIN");
        assertThat(DelegatableRoleCatalog.firstNonDelegatable(List.of())).isNull();
        assertThat(DelegatableRoleCatalog.firstNonDelegatable(null)).isNull();
    }
}
