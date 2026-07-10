package com.example.admin.infrastructure.persistence;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-492 / ADR-MONO-047 D5 — non-Docker shape-pin for
 * {@code V0042__admin_operator_roles_org_node_id.sql}, mirroring
 * {@link V0027MigrationShapeTest}.
 *
 * <p>Two classes of regression are pinned here, and neither is caught by a green
 * Testcontainers run on a fresh database:
 *
 * <ol>
 *   <li><b>The V0016 cycle-2 trap.</b> A MySQL user variable set in one Flyway-split
 *       statement and read in a later one resolves to NULL, silently turning the DDL into a
 *       no-op — on a fresh DB the column would simply be absent and every later assertion
 *       would fail confusingly; on an existing DB the migration would appear to succeed and
 *       do nothing. Each {@code @ddl_*} here must be set and consumed inside the same
 *       PREPARE/EXECUTE/DEALLOCATE block, and the guard must be the NULL-safe
 *       {@code COUNT(*) ... = 0} form.</li>
 *   <li><b>The security shape.</b> {@code tenant_id} must NOT be repurposed as the scope
 *       column (BE-289 WI-2 operator-mirror invariant), and the
 *       {@code org_node_id IS NOT NULL ∧ tenant_id = '*'} combination must be forbidden at
 *       the DB — not only in the application — so a hand-written seed / ops row cannot
 *       create an ambiguous grant that the {@code '*'} pre-scan would silently resolve to
 *       platform scope.</li>
 * </ol>
 */
class V0042MigrationShapeTest {

    private static final String MIGRATION_RESOURCE =
            "/db/migration/V0042__admin_operator_roles_org_node_id.sql";

    private static String sql;

    @BeforeAll
    static void loadMigration() throws IOException {
        try (InputStream in = V0042MigrationShapeTest.class.getResourceAsStream(MIGRATION_RESOURCE)) {
            assertThat(in)
                    .as("V0042 migration must be present on the classpath at " + MIGRATION_RESOURCE)
                    .isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Statements only — comment lines stripped so assertions ignore the rationale prose. */
    private static String body() {
        StringBuilder sb = new StringBuilder();
        for (String line : sql.split("\n")) {
            if (line.stripLeading().startsWith("--")) {
                continue;
            }
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    @Test
    @DisplayName("V0042 adds a NULLABLE org_node_id column + its index (additive, net-zero)")
    void addsNullableScopeDriverColumnAndIndex() {
        String body = body();
        assertThat(body)
                .as("the scope driver is an additive NULLABLE column — every existing row stays NULL")
                .contains("ALTER TABLE admin_operator_roles ADD COLUMN org_node_id VARCHAR(36) NULL");
        assertThat(body)
                .as("grants are looked up by node on the /admins surface")
                .contains("ADD INDEX idx_admin_operator_roles_org_node (org_node_id)");
    }

    @Test
    @DisplayName("V0042 forbids `org_node_id IS NOT NULL AND tenant_id = '*'` at the DB, not just in the app")
    void checkConstraintForbidsPlatformGrantWithNode() {
        String body = body();
        assertThat(body)
                .as("a platform-scoped grant may not also carry a node — the '*' pre-scan "
                        + "would make the node silently inert (ADR-047 D5)")
                .contains("ADD CONSTRAINT ck_admin_operator_roles_node_not_platform "
                        + "CHECK (org_node_id IS NULL OR tenant_id <> ''*'')");
    }

    @Test
    @DisplayName("V0042 does NOT repurpose tenant_id — no ALTER on it (BE-289 WI-2 operator-mirror invariant)")
    void tenantIdIsNotRepurposed() {
        String body = body().toUpperCase();
        assertThat(body)
                .as("tenant_id must keep mirroring the bound operator's own tenant — it is the "
                        + "audit-routing column, never the org-node scope column")
                .doesNotContain("MODIFY COLUMN TENANT_ID")
                .doesNotContain("CHANGE COLUMN TENANT_ID")
                .doesNotContain("DROP COLUMN TENANT_ID")
                .doesNotContain("UPDATE ADMIN_OPERATOR_ROLES");
    }

    @Test
    @DisplayName("V0042 idempotency guards are the NULL-safe information_schema COUNT(*) = 0 form")
    void nullSafeInformationSchemaIdempotencyGuards() {
        String body = body().toUpperCase();
        assertThat(body).as("column guard").contains("INFORMATION_SCHEMA.COLUMNS");
        assertThat(body).as("index guard").contains("INFORMATION_SCHEMA.STATISTICS");
        assertThat(body).as("check-constraint guard").contains("INFORMATION_SCHEMA.TABLE_CONSTRAINTS");
        assertThat(body).as("NULL-safe COUNT(*) ... = 0").contains("COUNT(*)").contains(") = 0");
    }

    @Test
    @DisplayName("V0042 uses no cross-statement MySQL user variable (V0016 cycle-2 @plr no-op trap)")
    void noCrossStatementUserVariable() {
        String body = body();
        assertThat(body).contains("SET @ddl_add_col");
        assertThat(body).contains("PREPARE stmt_add_col FROM @ddl_add_col");
        assertThat(body).contains("DEALLOCATE PREPARE stmt_add_col");
        assertThat(body).contains("SET @ddl_add_idx");
        assertThat(body).contains("PREPARE stmt_add_idx FROM @ddl_add_idx");
        assertThat(body).contains("DEALLOCATE PREPARE stmt_add_idx");
        assertThat(body).contains("SET @ddl_add_chk");
        assertThat(body).contains("PREPARE stmt_add_chk FROM @ddl_add_chk");
        assertThat(body).contains("DEALLOCATE PREPARE stmt_add_chk");

        // No block references an earlier block's variable.
        int idxSet = body.indexOf("SET @ddl_add_idx");
        int chkSet = body.indexOf("SET @ddl_add_chk");
        assertThat(idxSet).isGreaterThan(0);
        assertThat(chkSet).isGreaterThan(idxSet);
        assertThat(body.substring(idxSet)).doesNotContain("@ddl_add_col");
        assertThat(body.substring(chkSet)).doesNotContain("@ddl_add_col").doesNotContain("@ddl_add_idx");
    }

    @Test
    @DisplayName("V0042 is forward-only (no down/rollback) per data-model.md migration policy")
    void forwardOnlyNoRollback() {
        String body = body().toUpperCase();
        assertThat(body)
                .doesNotContain("ROLLBACK")
                .doesNotContain("DROP COLUMN")
                .doesNotContain("DROP INDEX")
                .doesNotContain("DROP CONSTRAINT");
    }
}
