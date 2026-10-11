package com.example.admin.infrastructure.persistence;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-MONO-784 S2 (ADR-MONO-082 b · decisions D-2 · D-3) — non-Docker shape-pin for
 * {@code V0051__admin_operator_roles_tenant_pk_and_membership_backfill.sql}, same form as
 * {@link V0042MigrationShapeTest} / {@link V0044MigrationShapeTest}.
 *
 * <p>What a green Testcontainers run on a FRESH database cannot catch, and this pins:
 * <ol>
 *   <li><b>The PK swap is one statement.</b> {@code DROP PRIMARY KEY} on its own fails on MySQL
 *       (ER 1553 — the PK's leading {@code operator_id} serves {@code fk_admin_operator_roles_operator});
 *       a fresh DB still passes only if the two halves stay in one {@code ALTER}. And the new key must
 *       carry {@code tenant_id} in third position (the FK needs {@code operator_id} leading).</li>
 *   <li><b>The PK swap is guarded</b> on {@code tenant_id} NOT yet being in PRIMARY (re-run safe).</li>
 *   <li><b>The backfill is idempotent and scoped</b>: a NOT EXISTS guard on {@code (operator_id, tenant_id)}
 *       (a second run inserts nothing), {@code '*'} operators and {@code '*'} role rows are excluded
 *       (ADR-082 D2 — {@code '*'} is not a membership), it reads BOTH the home and the role-row tenants,
 *       and it never UPDATEs / DELETEs an existing membership (an {@code org_scope} set by an admin
 *       survives).</li>
 *   <li>The V0016 cycle-2 trap: no cross-statement user variable.</li>
 * </ol>
 * The authoritative behavioural check (two runs on real MySQL, a home-only row gains its membership,
 * the second run adds nothing) is {@code V0051MembershipBackfillIntegrationTest}.
 */
class V0051MigrationShapeTest {

    private static final String MIGRATION_RESOURCE =
            "/db/migration/V0051__admin_operator_roles_tenant_pk_and_membership_backfill.sql";

    private static String sql;

    @BeforeAll
    static void loadMigration() throws IOException {
        try (InputStream in = V0051MigrationShapeTest.class.getResourceAsStream(MIGRATION_RESOURCE)) {
            assertThat(in).as("V0051 migration must be on the classpath at " + MIGRATION_RESOURCE).isNotNull();
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

    /** Whitespace-collapsed, upper-cased body — so formatting changes cannot break the pins. */
    private static String flat() {
        return body().replaceAll("\\s+", " ").toUpperCase();
    }

    @Test
    @DisplayName("PK: one ALTER drops and re-adds PRIMARY KEY as (operator_id, role_id, tenant_id) — FK-safe, operator_id leading")
    void pkSwapIsOneStatementWithTenantThird() {
        assertThat(body())
                .contains("'ALTER TABLE admin_operator_roles DROP PRIMARY KEY, "
                        + "ADD PRIMARY KEY (operator_id, role_id, tenant_id)'");
        // Control: there is no standalone DROP PRIMARY KEY statement (ER 1553 on MySQL).
        assertThat(flat().split("DROP PRIMARY KEY", -1)).hasSize(2);
    }

    @Test
    @DisplayName("PK: guarded on tenant_id not yet in PRIMARY (information_schema.key_column_usage, NULL-safe COUNT(*) = 0)")
    void pkSwapIsGuarded() {
        String flat = flat();
        assertThat(flat).contains("INFORMATION_SCHEMA.KEY_COLUMN_USAGE");
        assertThat(flat).contains("CONSTRAINT_NAME = 'PRIMARY'");
        assertThat(flat).contains("COLUMN_NAME = 'TENANT_ID') = 0");
        assertThat(flat).contains("'SELECT 1'");
    }

    @Test
    @DisplayName("PK guard uses no cross-statement user variable (V0016 cycle-2 trap)")
    void noCrossStatementUserVariable() {
        String body = body();
        assertThat(body).contains("SET @ddl_aor_pk");
        assertThat(body).contains("PREPARE stmt_aor_pk FROM @ddl_aor_pk");
        assertThat(body).contains("EXECUTE stmt_aor_pk");
        assertThat(body).contains("DEALLOCATE PREPARE stmt_aor_pk");
        // Exactly one user variable in the whole file — the self-contained triple above.
        Matcher m = Pattern.compile("@[a-z_]+").matcher(body);
        java.util.Set<String> vars = new java.util.TreeSet<>();
        while (m.find()) {
            vars.add(m.group());
        }
        assertThat(vars).containsExactly("@ddl_aor_pk");
    }

    @Test
    @DisplayName("backfill: INSERT INTO operator_tenant_assignment from home ∪ role-row tenants, '*' excluded on both sides")
    void backfillReadsHomeAndRoleTenantsExcludingPlatform() {
        String flat = flat();
        assertThat(flat).contains("INSERT INTO OPERATOR_TENANT_ASSIGNMENT "
                + "(OPERATOR_ID, TENANT_ID, GRANTED_AT, GRANTED_BY, PERMISSION_SET_ID, ORG_SCOPE, GROUP_ORIGIN)");
        // A whole-tenant, direct, permission-set-free membership (rider R4).
        assertThat(flat).contains("SELECT SRC.OPERATOR_ID, SRC.TENANT_ID, NOW(6), NULL, NULL, NULL, NULL");
        // (a) the home tenant of every non-'*' operator
        assertThat(flat).contains("SELECT O.ID AS OPERATOR_ID, O.TENANT_ID AS TENANT_ID "
                + "FROM ADMIN_OPERATORS O WHERE O.TENANT_ID <> '*'");
        // (b) every non-'*' role-row tenant of a non-'*' operator
        assertThat(flat).contains("FROM ADMIN_OPERATOR_ROLES AOR JOIN ADMIN_OPERATORS O ON O.ID = AOR.OPERATOR_ID "
                + "WHERE O.TENANT_ID <> '*' AND AOR.TENANT_ID <> '*'");
        assertThat(flat).contains(" UNION ");
    }

    @Test
    @DisplayName("backfill: NOT EXISTS on (operator_id, tenant_id) — a second run inserts nothing")
    void backfillIsIdempotent() {
        String flat = flat();
        assertThat(flat).contains("WHERE NOT EXISTS ( SELECT 1 FROM "
                + "(SELECT OPERATOR_ID, TENANT_ID FROM OPERATOR_TENANT_ASSIGNMENT) A "
                + "WHERE A.OPERATOR_ID = SRC.OPERATOR_ID AND A.TENANT_ID = SRC.TENANT_ID )");
        // Plain INSERT (not IGNORE / REPLACE / ON DUPLICATE): the guard, not a swallowed error, is what makes
        // the second run a no-op — and nothing can overwrite an existing row's org_scope.
        assertThat(flat).doesNotContain("INSERT IGNORE").doesNotContain("REPLACE INTO")
                .doesNotContain("ON DUPLICATE KEY");
    }

    @Test
    @DisplayName("forward-only and additive: no UPDATE/DELETE of data, no DROP of a column/index/constraint, no rollback")
    void forwardOnlyAdditive() {
        String flat = flat();
        assertThat(flat)
                .doesNotContain("ROLLBACK")
                .doesNotContain("DROP COLUMN")
                .doesNotContain("DROP INDEX")
                .doesNotContain("DROP FOREIGN KEY")
                .doesNotContain("DROP CONSTRAINT")
                .doesNotContain("DELETE ")
                .doesNotContain("UPDATE ");
    }
}
