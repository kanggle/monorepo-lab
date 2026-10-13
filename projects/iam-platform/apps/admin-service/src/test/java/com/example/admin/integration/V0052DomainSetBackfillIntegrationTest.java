package com.example.admin.integration;

import com.example.admin.domain.rbac.DomainRoleCatalog;
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
 * TASK-BE-631 AC-5 (ADR-MONO-083 D8 · OD-1 · OD-3) — V0052's backfill on real MySQL.
 *
 * <p>Flyway already ran V0052 when this context started (before any hand-made operator existed), so a green boot
 * proves only that the SQL parses. This test re-executes the migration's OWN statements — read from the classpath
 * file, not re-typed — against fixtures the backfill has to act on, inside one rolled-back transaction (so it can
 * never hand legacy-compat rows to other classes' fixture operators, several of which assert «no set → []»):
 * <ul>
 *   <li>every membership of a non-{@code '*'} operator gets (operator, tenant, legacy-compat, MIGRATION); a
 *       {@code '*'} operator gets exactly one ({@code '*'}, legacy-compat, MIGRATION) row and no membership;</li>
 *   <li>the data-model completion predicate («pre-cutover memberships with no set row») is &gt; 0 before and
 *       {@code 0} after — the P3 deployment gate;</li>
 *   <li>a SECOND run of every V0052 data statement inserts 0 rows (idempotent);</li>
 *   <li>control: a membership created AFTER the cutover starts with zero set rows (no code branch — no row).</li>
 * </ul>
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
class V0052DomainSetBackfillIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofMinutes(3));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("admin.auth-service.base-url", () -> "http://localhost:18087");
        registry.add("admin.account-service.base-url", () -> "http://localhost:18087");
        registry.add("admin.security-service.base-url", () -> "http://localhost:18087");
    }

    private static final String MIGRATION = "/db/migration/V0052__create_domain_permission_sets_and_backfill.sql";

    static final String MEMBER = "00000000-0000-7000-8000-000000631b01";
    static final String PLATFORM = "00000000-0000-7000-8000-000000631b02";
    static final String OLD = "2026-01-01 00:00:00";
    static final String CUTOVER = "2026-06-01 00:00:00";

    /** data-model.md § Migration Strategy V0052 — the completion predicate, verbatim but for the bind marker. */
    static final String COMPLETION_PREDICATE = """
            SELECT
              (SELECT COUNT(*)
                 FROM operator_tenant_assignment a
                 JOIN admin_operators o ON o.id = a.operator_id
                WHERE o.tenant_id <> '*'
                  AND a.granted_at <= ?
                  AND NOT EXISTS (SELECT 1 FROM operator_domain_set_grant g
                                   WHERE g.operator_id = a.operator_id AND g.tenant_id = a.tenant_id))
            + (SELECT COUNT(*)
                 FROM admin_operators o
                WHERE o.tenant_id = '*'
                  AND o.created_at <= ?
                  AND NOT EXISTS (SELECT 1 FROM operator_domain_set_grant g
                                   WHERE g.operator_id = o.id AND g.tenant_id = '*'))
              AS missing_backfill
            """;

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PlatformTransactionManager transactionManager;

    /** The migration's statements, comment lines stripped, split on ';'. */
    private static List<String> migrationStatements() throws IOException {
        String sql;
        try (InputStream in = V0052DomainSetBackfillIntegrationTest.class.getResourceAsStream(MIGRATION)) {
            assertThat(in).as("V0052 on the classpath").isNotNull();
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

    private static List<String> statementsStartingWith(String prefix) throws IOException {
        return migrationStatements().stream().filter(s -> s.startsWith(prefix)).toList();
    }

    @Test
    @DisplayName("presets: 15 rows; legacy-compat = all-domains-full = the whole catalog; wms-full = the 8 wms roles")
    void presetsMatchTheCatalog() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM domain_permission_set WHERE tenant_id IS NULL", Integer.class)).isEqualTo(15);
        for (String key : List.of("legacy-compat", "all-domains-full")) {
            List<String> roles = jdbcTemplate.queryForList("""
                    SELECT r.role_name FROM domain_permission_set_role r
                      JOIN domain_permission_set s ON s.id = r.set_id WHERE s.system_key = ?
                    """, String.class, key);
            assertThat(roles).as(key).containsExactlyInAnyOrderElementsOf(DomainRoleCatalog.roleNames());
        }
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM domain_permission_set_role r
                  JOIN domain_permission_set s ON s.id = r.set_id WHERE s.system_key = 'wms-full'
                """, Integer.class)).isEqualTo(8);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT set_id FROM domain_permission_set WHERE system_key = 'legacy-compat'", String.class))
                .as("a fixed set id, the same in every environment")
                .isEqualTo("00000083-0000-7000-8000-000000000015");
        assertThat(jdbcTemplate.queryForList("""
                SELECT r.name FROM admin_role_permissions p JOIN admin_roles r ON r.id = p.role_id
                 WHERE p.permission_key = 'domain_access.manage' ORDER BY r.name
                """, String.class)).containsExactly("SUPER_ADMIN", "TENANT_ADMIN");
    }

    @Test
    @DisplayName("AC-5: memberships → MIGRATION rows · '*' → ('*','*') · predicate >0 → 0 · re-run 0 rows · post-cutover membership stays at 0")
    void backfill_isCompleteScopedAndIdempotent() throws IOException {
        List<String> backfill = statementsStartingWith("INSERT INTO operator_domain_set_grant");
        assertThat(backfill).as("(a) memberships + (b) '*' operators").hasSize(2);
        List<String> seeds = migrationStatements().stream().filter(s -> s.startsWith("INSERT IGNORE")).toList();
        assertThat(seeds).as("preset sets · preset roles ×3 · permission ×2").hasSize(6);

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            status.setRollbackOnly(); // never leak legacy-compat rows to other classes' fixtures
            operator(MEMBER, "s52-a");
            operator(PLATFORM, "*");
            membership(MEMBER, "s52-a", OLD);
            membership(MEMBER, "s52-b", OLD);

            assertThat(predicate()).as("before: the fixtures' pre-cutover memberships have no set row").isPositive();

            int first = 0;
            for (String s : backfill) {
                first += jdbcTemplate.update(s);
            }
            assertThat(first).as("at least the two MEMBER rows and the PLATFORM row").isGreaterThanOrEqualTo(3);

            assertThat(grants(MEMBER)).containsExactly(
                    "s52-a|legacy-compat|MIGRATION|1|1", "s52-b|legacy-compat|MIGRATION|1|1");
            assertThat(grants(PLATFORM)).containsExactly("*|legacy-compat|MIGRATION|1|1");
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM operator_tenant_assignment a JOIN admin_operators o ON o.id = a.operator_id
                     WHERE o.operator_id = ?
                    """, Integer.class, PLATFORM)).as("'*' takes no membership (ADR-082 D2)").isZero();
            assertThat(predicate()).as("after: every pre-cutover membership / '*' operator holds a set row").isZero();

            // idempotent: every data statement of the file, run again, inserts nothing
            int second = 0;
            for (String s : backfill) {
                second += jdbcTemplate.update(s);
            }
            for (String s : seeds) {
                second += jdbcTemplate.update(s);
            }
            assertThat(second).as("second run inserts 0 rows").isZero();
            assertThat(grants(MEMBER)).hasSize(2);

            // control: a membership created after the cutover starts at zero sets — and is not in the predicate
            membership(MEMBER, "s52-late", null);
            assertThat(grants(MEMBER)).noneMatch(g -> g.startsWith("s52-late|"));
            assertThat(predicate()).isZero();
        });
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_operators WHERE operator_id IN (?, ?)", Integer.class, MEMBER, PLATFORM))
                .as("rolled back").isZero();
    }

    private int predicate() {
        Integer n = jdbcTemplate.queryForObject(COMPLETION_PREDICATE, Integer.class, CUTOVER, CUTOVER);
        return n == null ? -1 : n;
    }

    private void operator(String uuid, String tenantId) {
        jdbcTemplate.update("""
                INSERT INTO admin_operators
                  (operator_id, tenant_id, email, password_hash, display_name, status,
                   created_at, updated_at, version)
                VALUES (?, ?, ?, 'x', 'V0052 Op', 'ACTIVE', ?, ?, 0)
                """, uuid, tenantId, uuid + "@example.com", OLD, OLD);
    }

    /** {@code grantedAt == null} → NOW(6) (after the cutover). */
    private void membership(String uuid, String tenantId, String grantedAt) {
        jdbcTemplate.update("""
                INSERT INTO operator_tenant_assignment (operator_id, tenant_id, granted_at, granted_by, permission_set_id)
                SELECT o.id, ?, COALESCE(?, NOW(6)), NULL, NULL FROM admin_operators o WHERE o.operator_id = ?
                """, tenantId, grantedAt, uuid);
    }

    /** {@code tenant|system_key|source_key|group_origin IS NULL|granted_by IS NULL}, sorted. */
    private List<String> grants(String uuid) {
        return jdbcTemplate.queryForList("""
                SELECT CONCAT_WS('|', g.tenant_id, s.system_key, g.source_key, g.group_origin IS NULL,
                                 g.granted_by IS NULL)
                  FROM operator_domain_set_grant g
                  JOIN admin_operators o ON o.id = g.operator_id
                  JOIN domain_permission_set s ON s.id = g.set_id
                 WHERE o.operator_id = ?
                 ORDER BY g.tenant_id
                """, String.class, uuid);
    }
}
