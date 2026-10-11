package com.example.admin.integration;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * TASK-MONO-784 S2 (ADR-MONO-082 b · rider R1 · decision D-2) — the home tenant is no longer part of an
 * operator's tenant scope; the scope is its {@code operator_tenant_assignment} rows («memberships») only.
 *
 * <p>In production every pre-784 operator received a home membership from the V0051 backfill, and every
 * operator-creating path writes one. An integration fixture that INSERTs an {@code admin_operators} row by
 * hand runs AFTER the migrations, so the backfill never sees it: such a fixture must add the membership itself,
 * or the operator silently loses its own company (assume · console registry · console eligibility · token
 * exchange · the read gate).
 *
 * <p>{@link #addHomeMembership} reproduces exactly the backfill's row for one operator — whole tenant
 * ({@code org_scope} NULL), no permission set (rider R4), direct ({@code group_origin} NULL) — and is a
 * no-op for a {@code '*'} operator (it has no memberships, ADR-082 D2) and when the row already exists.
 *
 * <p>Call it only where the test means «an ordinary operator of its own company». A test that is ABOUT the
 * home-vs-membership difference must not call it for the operator under test.
 */
final class MembershipFixture {

    private MembershipFixture() {
    }

    /** The V0051 backfill row for this one operator (by external {@code operator_id}). Idempotent. */
    static void addHomeMembership(JdbcTemplate jdbc, String operatorUuid) {
        jdbc.update("""
                INSERT IGNORE INTO operator_tenant_assignment
                  (operator_id, tenant_id, granted_at, granted_by, permission_set_id)
                SELECT o.id, o.tenant_id, NOW(6), NULL, NULL
                  FROM admin_operators o
                 WHERE o.operator_id = ? AND o.tenant_id <> '*'
                """, operatorUuid);
    }
}
