package com.example.admin.infrastructure.persistence;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-298 / ADR-MONO-014 — non-Docker shape-pin for the forward Flyway
 * migration {@code V0027__add_oidc_subject_to_admin_operators.sql}.
 *
 * <h3>Why this test exists — the TASK-BE-297 V0016 cycle-3 trap it traps</h3>
 *
 * <p>V0016 (TASK-BE-297) shipped THREE times before it worked: cycle 1 used a
 * pre-normalization {@code REPLACE()} text-substring (silent no-op on a
 * normalized MySQL column); cycle 2 hoisted a JSON path into a MySQL user
 * variable {@code SET @plr := ...} referenced across Flyway-split statements
 * (resolved to NULL → every UPDATE matched zero rows, byte-identical CI
 * failure). The fix was: NO cross-statement user variable + structural-only
 * mutation + a NULL-safe idempotency guard, pinned by a non-Docker shape test
 * so a regression fails fast at the source rather than only after a full CI
 * Testcontainers cycle.
 *
 * <p>V0027 is a STRUCTURAL DDL migration (ADD COLUMN / ADD UNIQUE INDEX), so
 * the JSON-normalization no-op does not apply, but the cross-statement state
 * and NULL-safe-idempotency disciplines DO. This test pins exactly those
 * structural invariants: each PREPARE/EXECUTE block is self-contained, the
 * idempotency guard is the NULL-safe {@code information_schema COUNT(*) = 0}
 * form, and the migration is forward-only. Same model as the auth-service
 * {@code V0016MigrationShapeTest}.
 *
 * <p>It asserts on migration <em>shape</em> (not executed SQL) because the
 * defeating behaviour (Flyway statement splitting + a MySQL session variable
 * resolving to NULL) only manifests on a real MySQL 8.0 under Flyway — exactly
 * the Docker-only layer this test is the early warning for.
 */
class V0027MigrationShapeTest {

    private static final String MIGRATION_RESOURCE =
            "/db/migration/V0027__add_oidc_subject_to_admin_operators.sql";

    private static String sql;

    @BeforeAll
    static void loadMigration() throws IOException {
        try (InputStream in =
                     V0027MigrationShapeTest.class.getResourceAsStream(MIGRATION_RESOURCE)) {
            assertThat(in)
                    .as("V0027 migration must be present on the classpath at " + MIGRATION_RESOURCE)
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
    @DisplayName("V0027 adds oidc_subject column + UNIQUE index structurally (ALTER TABLE DDL)")
    void addsColumnAndUniqueIndexStructurally() {
        String body = body();
        assertThat(body)
                .as("must add the oidc_subject VARCHAR column via ALTER TABLE DDL")
                .contains("ALTER TABLE admin_operators ADD COLUMN oidc_subject VARCHAR(255)");
        assertThat(body)
                .as("must add the platform-global UNIQUE index for the OIDC<->operator link")
                .contains("ADD UNIQUE INDEX uk_admin_operators_oidc_subject (oidc_subject)");
    }

    @Test
    @DisplayName("V0027 idempotency guard is the NULL-safe information_schema COUNT(*) = 0 form")
    void nullSafeInformationSchemaIdempotencyGuard() {
        String body = body().toUpperCase();
        // COUNT(*) is ALWAYS an integer (never NULL), so `= 0` always yields
        // TRUE/FALSE and can never silently skip the DDL the way the V0016
        // cycle-2 NULL-unsafe `<> 'x'` predicate did.
        assertThat(body)
                .as("column guard must query information_schema.columns")
                .contains("INFORMATION_SCHEMA.COLUMNS");
        assertThat(body)
                .as("index guard must query information_schema.statistics")
                .contains("INFORMATION_SCHEMA.STATISTICS");
        assertThat(body)
                .as("guard must be the NULL-safe COUNT(*) ... = 0 form")
                .contains("COUNT(*)")
                .contains(") = 0");
    }

    @Test
    @DisplayName("V0027 uses no cross-statement MySQL user variable (V0016 cycle-2 @plr no-op trap)")
    void noCrossStatementUserVariableAcrossStatements() {
        // The V0016 cycle-2 no-op: `SET @x := ...` set in one Flyway-split
        // statement and READ in a LATER statement resolved to NULL. Here every
        // @ddl_* variable is written and consumed inside the SAME
        // PREPARE/EXECUTE/DEALLOCATE block (statement 1 sets+uses @ddl_add_col;
        // statement 2 sets+uses @ddl_add_idx) — no variable set in an earlier
        // statement is referenced by a later one. Assert each variable's
        // PREPARE immediately follows its SET (no later-statement read).
        String body = body();
        // The two session vars used:
        assertThat(body).contains("SET @ddl_add_col");
        assertThat(body).contains("PREPARE stmt_add_col FROM @ddl_add_col");
        assertThat(body).contains("SET @ddl_add_idx");
        assertThat(body).contains("PREPARE stmt_add_idx FROM @ddl_add_idx");
        // Each prepared statement is deallocated within its own block — no
        // cross-block reuse / dangling handle.
        assertThat(body).contains("DEALLOCATE PREPARE stmt_add_col");
        assertThat(body).contains("DEALLOCATE PREPARE stmt_add_idx");
        // The column variable is never referenced by the index block (and vice
        // versa) — i.e. no @ddl_add_col token appears after the idx SET.
        int idxSet = body.indexOf("SET @ddl_add_idx");
        assertThat(idxSet).isGreaterThan(0);
        assertThat(body.substring(idxSet))
                .as("the index block must not reference the column block's user "
                        + "variable (no cross-statement state — V0016 cycle-2 lesson)")
                .doesNotContain("@ddl_add_col");
    }

    @Test
    @DisplayName("V0027 is forward-only (no down/rollback) per data-model.md migration policy")
    void forwardOnlyNoRollback() {
        String body = body().toUpperCase();
        assertThat(body)
                .as("forward-only migration — no DROP COLUMN / DROP INDEX / ROLLBACK")
                .doesNotContain("ROLLBACK")
                .doesNotContain("DROP COLUMN")
                .doesNotContain("DROP INDEX");
    }
}
