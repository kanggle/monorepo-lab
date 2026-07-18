package com.example.admin.integration;

import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-520 — shared operator seeding for the operator-group integration tests. Mirrors the
 * {@code seedOperator} helper in {@code OrgNodeAdminIntegrationTest} (idempotent insert +
 * re-activate + a case-insensitive collision guard, since the whole IT suite shares one MySQL
 * container and {@code admin_operators} is {@code utf8mb4_unicode_ci}).
 */
final class GroupItSeeds {

    private GroupItSeeds() {}

    static void seedOperator(JdbcTemplate jdbc, String uuid, String tenantId, String email, String roleName) {
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM admin_operators WHERE operator_id = ?", Integer.class, uuid);
        if (existing == null || existing == 0) {
            jdbc.update("""
                    INSERT INTO admin_operators
                      (operator_id, tenant_id, email, password_hash, display_name, status,
                       created_at, updated_at, version)
                    VALUES (?, ?, ?, 'x', ?, 'ACTIVE', NOW(6), NOW(6), 0)
                    """, uuid, tenantId, email, "Test Op");
        } else {
            jdbc.update("UPDATE admin_operators SET status = 'ACTIVE' WHERE operator_id = ?", uuid);
        }
        // Fail loudly if another IT class owns this operator id (case-insensitive collation).
        String actualTenant = jdbc.queryForObject(
                "SELECT tenant_id FROM admin_operators WHERE operator_id = ?", String.class, uuid);
        assertThat(actualTenant)
                .as("operator %s must be owned by this test class (tenant_id skew ⇒ uuid collision)", uuid)
                .isEqualTo(tenantId);
        if (roleName != null) {
            jdbc.update("""
                    INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
                    SELECT o.id, r.id, o.tenant_id, NOW(6), NULL
                      FROM admin_operators o CROSS JOIN admin_roles r
                     WHERE o.operator_id = ? AND r.name = ?
                    """, uuid, roleName);
        }
    }
}
