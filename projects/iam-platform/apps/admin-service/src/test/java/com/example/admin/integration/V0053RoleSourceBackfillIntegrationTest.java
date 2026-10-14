package com.example.admin.integration;

import com.example.testsupport.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-633 (Failure Scenario 1) — V0053's DATA statements on real MySQL, against rows in the shape the v1
 * single-owner rule left behind.
 *
 * <p>Flyway already ran V0053 on an empty schema when this context started, so a green boot proves only that the
 * SQL parses and the DDL applies. This test re-executes the migration's OWN data statements — read from the
 * classpath file, not re-typed — inside one rolled-back transaction, on fixtures that read exactly like pre-V0053
 * rows right after step 1 added the column (every row {@code source_key = 'DIRECT'}, the origin only in
 * {@code group_origin}):
 * <ul>
 *   <li>a row G1 materialised first, while G2 grants the same role to the same member (v1 skipped G2) → the row
 *       becomes {@code GROUP:G1} and a {@code GROUP:G2} row is ADDED (re-fan-out);</li>
 *   <li>a member who held the role DIRECTLY when G1's fan-out skipped it → the direct row stays {@code DIRECT} and
 *       a {@code GROUP:G1} row is added;</li>
 *   <li>a group member who no longer belongs to the group's tenant (784 D-1) → NOTHING is added;</li>
 *   <li>a second run of both statements changes 0 rows (idempotent).</li>
 * </ul>
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
class V0053RoleSourceBackfillIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofMinutes(3));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("admin.auth-service.base-url", () -> "http://localhost:18090");
        registry.add("admin.account-service.base-url", () -> "http://localhost:18090");
        registry.add("admin.security-service.base-url", () -> "http://localhost:18090");
    }

    private static final String MIGRATION = "/db/migration/V0053__admin_operator_roles_source_key.sql";

    static final String TENANT = "be633-mig";
    static final String TWO_GROUPS = "00000000-0000-7000-8000-000000633c01";
    static final String DIRECT_TOO = "00000000-0000-7000-8000-000000633c02";
    static final String LEFT_TENANT = "00000000-0000-7000-8000-000000633c03";
    static final String ROLE = "SUPPORT_LOCK";

    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    /** The migration's statements, comment lines stripped, split on ';'. */
    private static List<String> migrationStatements() throws IOException {
        String sql;
        try (InputStream in = V0053RoleSourceBackfillIntegrationTest.class.getResourceAsStream(MIGRATION)) {
            assertThat(in).as("V0053 on the classpath").isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        StringBuilder body = new StringBuilder();
        for (String line : sql.split("\n")) {
            if (!line.stripLeading().startsWith("--")) {
                body.append(line).append('\n');
            }
        }
        List<String> statements = new ArrayList<>();
        for (String s : body.toString().split(";")) {
            if (!s.isBlank()) {
                statements.add(s.strip());
            }
        }
        return statements;
    }

    @Test
    @DisplayName("FS-1: G1-owned row → GROUP:G1 + G2's row re-fanned · direct kept + G1 row added · non-member of the tenant gets nothing · re-run 0")
    void sourceBackfillAndReFanOut() throws IOException {
        List<String> statements = migrationStatements();
        List<String> update = statements.stream().filter(s -> s.startsWith("UPDATE admin_operator_roles")).toList();
        List<String> insert = statements.stream().filter(s -> s.startsWith("INSERT INTO admin_operator_roles")).toList();
        assertThat(update).as("step 2").hasSize(1);
        assertThat(insert).as("step 5").hasSize(1);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            status.setRollbackOnly(); // never leak re-fanned rows to other classes' fixtures
            operator(TWO_GROUPS);
            operator(DIRECT_TOO);
            operator(LEFT_TENANT);
            membership(TWO_GROUPS);
            membership(DIRECT_TOO);
            // LEFT_TENANT: no membership of TENANT any more (exported — its group membership survived in the fixture)
            long g1 = group("be633 mig G1");
            long g2 = group("be633 mig G2");
            roleTemplate(g1);
            roleTemplate(g2);
            member(g1, TWO_GROUPS);
            member(g2, TWO_GROUPS);
            member(g1, DIRECT_TOO);
            member(g1, LEFT_TENANT);
            // v1 leftovers, as they read right after step 1 added the column (default 'DIRECT')
            legacyRow(TWO_GROUPS, g1);     // G1 wrote first; G2's fan-out was skipped
            legacyRow(DIRECT_TOO, null);   // direct; G1's fan-out was skipped

            int updated = 0;
            for (String s : update) {
                updated += jdbc.update(s);
            }
            int inserted = 0;
            for (String s : insert) {
                inserted += jdbc.update(s);
            }

            assertThat(rows(TWO_GROUPS)).as("G1's row keeps its origin as its source; G2's source is re-fanned")
                    .containsExactlyInAnyOrder("GROUP:" + g1 + "|" + g1, "GROUP:" + g2 + "|" + g2);
            assertThat(rows(DIRECT_TOO)).as("the direct row stays direct; G1's source is added")
                    .containsExactlyInAnyOrder("DIRECT|-", "GROUP:" + g1 + "|" + g1);
            assertThat(rows(LEFT_TENANT)).as("784 D-1: no role row without a membership").isEmpty();
            // ≥, not =: the statements are global and the IT suite shares one MySQL — another class's leftovers may
            // be in scope. The rows above are the exact check; the second run below is the global one.
            assertThat(updated).as("at least the legacy group row").isGreaterThanOrEqualTo(1);
            assertThat(inserted).as("at least G2 → TWO_GROUPS, G1 → DIRECT_TOO").isGreaterThanOrEqualTo(2);

            int second = 0;
            for (String s : update) {
                second += jdbc.update(s);
            }
            for (String s : insert) {
                second += jdbc.update(s);
            }
            assertThat(second).as("a second run changes nothing").isZero();
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_operators WHERE operator_id IN (?, ?, ?)",
                Integer.class, TWO_GROUPS, DIRECT_TOO, LEFT_TENANT)).as("rolled back").isZero();
    }

    @Test
    @DisplayName("V0053 shape on the live schema: PK = (operator_id, role_id, tenant_id, source_key) · source_key NOT NULL DEFAULT 'DIRECT' · both CHECKs refuse")
    void liveShape() {
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.key_column_usage
                 WHERE table_schema = DATABASE() AND table_name = 'admin_operator_roles' AND constraint_name = 'PRIMARY'
                 ORDER BY ordinal_position
                """, String.class)).containsExactly("operator_id", "role_id", "tenant_id", "source_key");
        assertThat(jdbc.queryForObject("""
                SELECT CONCAT_WS('|', is_nullable, column_default, character_maximum_length)
                  FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'admin_operator_roles' AND column_name = 'source_key'
                """, String.class)).isEqualTo("NO|DIRECT|48");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            status.setRollbackOnly();
            operator(TWO_GROUPS);
            // a node row that names no source (would read as DIRECT and be deleted by a role PATCH)
            assertRefused("""
                    INSERT INTO admin_operator_roles (operator_id, role_id, tenant_id, org_node_id, granted_at)
                    SELECT o.id, r.id, ?, 'n1', NOW(6) FROM admin_operators o CROSS JOIN admin_roles r
                     WHERE o.operator_id = ? AND r.name = 'ORG_ADMIN'
                    """, "ck_admin_operator_roles_node_source");
            // an unknown source / a group source in '*'
            assertRefused("""
                    INSERT INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, source_key)
                    SELECT o.id, r.id, ?, NOW(6), 'MIGRATION' FROM admin_operators o CROSS JOIN admin_roles r
                     WHERE o.operator_id = ? AND r.name = 'SUPPORT_LOCK'
                    """, "ck_admin_operator_roles_source");
        });
    }

    private void assertRefused(String sql, String constraint) {
        try {
            jdbc.update(sql, TENANT, TWO_GROUPS);
        } catch (org.springframework.dao.DataAccessException e) {
            assertThat(e.getMessage()).contains(constraint);
            return;
        }
        throw new AssertionError("expected " + constraint + " to refuse the row");
    }

    private void operator(String uuid) {
        jdbc.update("""
                INSERT INTO admin_operators
                  (operator_id, tenant_id, email, password_hash, display_name, status, created_at, updated_at, version)
                VALUES (?, ?, ?, 'x', 'V0053 Op', 'ACTIVE', NOW(6), NOW(6), 0)
                """, uuid, TENANT, uuid + "@be633.example.com");
    }

    private void membership(String uuid) {
        jdbc.update("""
                INSERT INTO operator_tenant_assignment (operator_id, tenant_id, granted_at, granted_by, permission_set_id)
                SELECT o.id, ?, NOW(6), NULL, NULL FROM admin_operators o WHERE o.operator_id = ?
                """, TENANT, uuid);
    }

    private long group(String name) {
        String groupId = java.util.UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO operator_group (group_id, tenant_id, name, created_at, updated_at, version)
                VALUES (?, ?, ?, NOW(6), NOW(6), 0)
                """, groupId, TENANT, name);
        Long id = jdbc.queryForObject("SELECT id FROM operator_group WHERE group_id = ?", Long.class, groupId);
        return id == null ? -1 : id;
    }

    private void roleTemplate(long groupInternalId) {
        jdbc.update("""
                INSERT INTO operator_group_grant (grant_id, group_id, grant_type, role_id, tenant_id, granted_by, granted_at)
                SELECT ?, ?, 'ROLE', r.id, NULL, NULL, NOW(6) FROM admin_roles r WHERE r.name = ?
                """, java.util.UUID.randomUUID().toString(), groupInternalId, ROLE);
    }

    private void member(long groupInternalId, String uuid) {
        jdbc.update("""
                INSERT INTO operator_group_member (group_id, operator_id, added_at, added_by)
                SELECT ?, o.id, NOW(6), NULL FROM admin_operators o WHERE o.operator_id = ?
                """, groupInternalId, uuid);
    }

    /** A row in the pre-V0053 shape: the origin in group_origin only, source_key at its column default. */
    private void legacyRow(String uuid, Long groupOrigin) {
        jdbc.update("""
                INSERT INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by, group_origin)
                SELECT o.id, r.id, ?, NOW(6), NULL, ? FROM admin_operators o CROSS JOIN admin_roles r
                 WHERE o.operator_id = ? AND r.name = ?
                """, TENANT, groupOrigin, uuid, ROLE);
    }

    /** {@code source_key|group_origin} of the operator's ROLE rows, sorted. */
    private List<String> rows(String uuid) {
        return jdbc.queryForList("""
                SELECT CONCAT_WS('|', b.source_key, COALESCE(CAST(b.group_origin AS CHAR), '-'))
                  FROM admin_operator_roles b
                  JOIN admin_operators o ON o.id = b.operator_id
                  JOIN admin_roles r ON r.id = b.role_id
                 WHERE o.operator_id = ? AND r.name = ? AND b.tenant_id = ?
                 ORDER BY b.source_key
                """, String.class, uuid, ROLE, TENANT);
    }
}
