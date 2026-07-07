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
                        // single-role domains
                        "SCM_OPERATOR", "ERP_OPERATOR", "FINANCE_OPERATOR",
                        "MES_OPERATOR", "FAN_OPERATOR");
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
                List.of("WMS_OPERATOR", "SCM_OPERATOR"))).isNull();
        assertThat(DelegatableRoleCatalog.firstNonDelegatable(
                List.of("WMS_OPERATOR", "WMS_ADMIN"))).isEqualTo("WMS_ADMIN");
        assertThat(DelegatableRoleCatalog.firstNonDelegatable(List.of())).isNull();
        assertThat(DelegatableRoleCatalog.firstNonDelegatable(null)).isNull();
    }
}
