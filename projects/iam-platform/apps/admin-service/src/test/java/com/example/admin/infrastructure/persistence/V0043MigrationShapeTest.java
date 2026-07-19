package com.example.admin.infrastructure.persistence;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-520 / ADR-MONO-046 D1/D3/D5 — non-Docker shape-pin for
 * {@code V0043__create_operator_group_tables.sql}: the tenant-scope CHECK ({@code tenant_id
 * <> '*'}), the grant_type XOR CHECK, the composite/natural unique keys, and the FK cascade
 * behaviours — the security-load-bearing constraints a fresh green Testcontainers run would
 * not distinguish from a laxer schema.
 */
class V0043MigrationShapeTest {

    private static final String MIGRATION_RESOURCE = "/db/migration/V0043__create_operator_group_tables.sql";

    private static String sql;

    @BeforeAll
    static void loadMigration() throws IOException {
        try (InputStream in = V0043MigrationShapeTest.class.getResourceAsStream(MIGRATION_RESOURCE)) {
            assertThat(in).as("V0043 migration must be on the classpath at " + MIGRATION_RESOURCE).isNotNull();
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
    @DisplayName("V0043 creates all three tables idempotently (CREATE TABLE IF NOT EXISTS)")
    void createsThreeTablesIdempotently() {
        String body = body().toUpperCase();
        assertThat(body).contains("CREATE TABLE IF NOT EXISTS OPERATOR_GROUP (");
        assertThat(body).contains("CREATE TABLE IF NOT EXISTS OPERATOR_GROUP_MEMBER (");
        assertThat(body).contains("CREATE TABLE IF NOT EXISTS OPERATOR_GROUP_GRANT (");
    }

    @Test
    @DisplayName("V0043 forbids a platform-global group at the DB (tenant_id <> '*')")
    void tenantScopeCheckForbidsPlatformGroup() {
        assertThat(body())
                .contains("CONSTRAINT ck_operator_group_tenant_not_platform CHECK (tenant_id <> '*')");
    }

    @Test
    @DisplayName("V0043 pins exactly one grant reference per grant_type (ROLE XOR TENANT_ASSIGNMENT)")
    void grantTypeXorCheck() {
        String body = body().toUpperCase();
        assertThat(body).contains("CK_OPERATOR_GROUP_GRANT_TYPE");
        assertThat(body).contains("GRANT_TYPE = 'ROLE'").contains("ROLE_ID IS NOT NULL").contains("TENANT_ID IS NULL");
        assertThat(body).contains("GRANT_TYPE = 'TENANT_ASSIGNMENT'").contains("TENANT_ID IS NOT NULL");
    }

    @Test
    @DisplayName("V0043 declares the tenant-name uniqueness + external-id + grant natural keys")
    void uniqueKeys() {
        String body = body();
        assertThat(body).contains("uk_operator_group_group_id (group_id)");
        assertThat(body).contains("uk_operator_group_tenant_name (tenant_id, name)");
        assertThat(body).contains("uk_operator_group_grant_grant_id (grant_id)");
        assertThat(body).contains("uk_operator_group_grant_natural (group_id, grant_type, role_id, tenant_id)");
    }

    @Test
    @DisplayName("V0043 composite membership PK (group_id, operator_id) prevents duplicate membership")
    void membershipCompositePk() {
        assertThat(body()).contains("PRIMARY KEY (group_id, operator_id)");
    }

    @Test
    @DisplayName("V0043 FK cascade behaviours: member/grant cascade with group; role RESTRICT; audit-actor SET NULL")
    void foreignKeyBehaviours() {
        // Collapse the column-alignment whitespace so the assertions read as single-spaced SQL.
        String body = body().toUpperCase().replaceAll("[ \\t]+", " ");
        // group deletion cascades membership + grant templates
        assertThat(body).contains("FK_OPERATOR_GROUP_MEMBER_GROUP FOREIGN KEY (GROUP_ID) REFERENCES OPERATOR_GROUP(ID) ON DELETE CASCADE");
        assertThat(body).contains("FK_OPERATOR_GROUP_GRANT_GROUP FOREIGN KEY (GROUP_ID) REFERENCES OPERATOR_GROUP(ID) ON DELETE CASCADE");
        // a role bound to a group grant may not be hard-deleted
        assertThat(body).contains("FK_OPERATOR_GROUP_GRANT_ROLE FOREIGN KEY (ROLE_ID) REFERENCES ADMIN_ROLES(ID) ON DELETE RESTRICT");
        // created_by / added_by / granted_by null out on operator deletion (audit trail is soft)
        assertThat(body).contains("FK_OPERATOR_GROUP_CREATED_BY FOREIGN KEY (CREATED_BY) REFERENCES ADMIN_OPERATORS(ID) ON DELETE SET NULL");
    }

    @Test
    @DisplayName("V0043 is forward-only (no down/rollback)")
    void forwardOnly() {
        String body = body().toUpperCase();
        assertThat(body).doesNotContain("DROP TABLE").doesNotContain("ROLLBACK");
    }
}
