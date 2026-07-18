package com.example.admin.infrastructure.persistence;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-520 / ADR-MONO-046 D5 — non-Docker shape-pin for
 * {@code V0044__add_group_origin_marker.sql}, mirroring {@link V0042MigrationShapeTest}.
 *
 * <p>Pins the backward-compatibility shape (nullable + DEFAULT NULL marker on BOTH substrate
 * tables — so every existing direct grant stays byte-identical), the real FK to
 * {@code operator_group.id} ON DELETE CASCADE, and the V0016 cycle-2 anti-trap (self-contained
 * PREPARE/EXECUTE/DEALLOCATE, NULL-safe {@code COUNT(*) ... = 0} guards). None of these is
 * caught by a green Testcontainers run on a fresh DB.
 */
class V0044MigrationShapeTest {

    private static final String MIGRATION_RESOURCE = "/db/migration/V0044__add_group_origin_marker.sql";

    private static String sql;

    @BeforeAll
    static void loadMigration() throws IOException {
        try (InputStream in = V0044MigrationShapeTest.class.getResourceAsStream(MIGRATION_RESOURCE)) {
            assertThat(in).as("V0044 migration must be on the classpath at " + MIGRATION_RESOURCE).isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

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
    @DisplayName("V0044 adds a NULLABLE BIGINT DEFAULT NULL group_origin to BOTH substrate tables (backward-compatible)")
    void addsNullableDefaultNullMarkerOnBothTables() {
        String body = body();
        assertThat(body)
                .as("admin_operator_roles marker — nullable+defaulted so every existing direct grant stays NULL")
                .contains("ALTER TABLE admin_operator_roles ADD COLUMN group_origin BIGINT NULL DEFAULT NULL");
        assertThat(body)
                .as("operator_tenant_assignment marker — same shape")
                .contains("ALTER TABLE operator_tenant_assignment ADD COLUMN group_origin BIGINT NULL DEFAULT NULL");
    }

    @Test
    @DisplayName("V0044 adds a REAL FK to operator_group.id ON DELETE CASCADE on BOTH tables")
    void addsCascadingForeignKeyOnBothTables() {
        String body = body().toUpperCase();
        assertThat(body).as("role substrate FK")
                .contains("FK_ADMIN_OPERATOR_ROLES_GROUP_ORIGIN FOREIGN KEY (GROUP_ORIGIN) REFERENCES OPERATOR_GROUP(ID) ON DELETE CASCADE");
        assertThat(body).as("assignment substrate FK")
                .contains("FK_OPERATOR_TENANT_ASSIGNMENT_GROUP_ORIGIN FOREIGN KEY (GROUP_ORIGIN) REFERENCES OPERATOR_GROUP(ID) ON DELETE CASCADE");
    }

    @Test
    @DisplayName("V0044 indexes group_origin on both tables (cascade-revoke filter)")
    void indexesGroupOriginOnBothTables() {
        String body = body();
        assertThat(body).contains("ADD INDEX idx_admin_operator_roles_group_origin (group_origin)");
        assertThat(body).contains("ADD INDEX idx_operator_tenant_assignment_group_origin (group_origin)");
    }

    @Test
    @DisplayName("V0044 idempotency guards are the NULL-safe information_schema COUNT(*) = 0 form")
    void nullSafeInformationSchemaGuards() {
        String body = body().toUpperCase();
        assertThat(body).as("column guard").contains("INFORMATION_SCHEMA.COLUMNS");
        assertThat(body).as("index guard").contains("INFORMATION_SCHEMA.STATISTICS");
        assertThat(body).as("fk guard").contains("INFORMATION_SCHEMA.TABLE_CONSTRAINTS");
        assertThat(body).as("NULL-safe COUNT(*) ... = 0").contains("COUNT(*)").contains(") = 0");
    }

    @Test
    @DisplayName("V0044 uses no cross-statement MySQL user variable (V0016 cycle-2 @plr no-op trap)")
    void noCrossStatementUserVariable() {
        String body = body();
        for (String stem : new String[]{
                "aor_col", "aor_idx", "aor_fk", "ota_col", "ota_idx", "ota_fk"}) {
            assertThat(body).contains("SET @ddl_" + stem);
            assertThat(body).contains("PREPARE stmt_" + stem + " FROM @ddl_" + stem);
            assertThat(body).contains("DEALLOCATE PREPARE stmt_" + stem);
        }
    }

    @Test
    @DisplayName("V0044 is forward-only (no down/rollback) per data-model.md migration policy")
    void forwardOnlyNoRollback() {
        String body = body().toUpperCase();
        assertThat(body)
                .doesNotContain("ROLLBACK")
                .doesNotContain("DROP COLUMN")
                .doesNotContain("DROP INDEX")
                .doesNotContain("DROP CONSTRAINT");
    }
}
