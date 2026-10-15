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
 * TASK-MONO-792 (ADR-MONO-083 P5 · owner decision Q5, 2026-10-15 UTC) — V0056 on real MySQL.
 *
 * <p>Flyway already ran V0056 when this context started (before any fixture existed), so a green boot proves the SQL
 * parses and the PRESETS are right. The data half re-executes the migration's OWN statements (read from the
 * classpath, not re-typed) against fixtures it has to act on, inside one rolled-back transaction:
 * <ul>
 *   <li>a tenant set holding only {@code SCM_OPERATOR} («SCM 전체» when it was made) → the eight SCM roles, no
 *       {@code SCM_OPERATOR} (the existing operator keeps the same scope — AC-2);</li>
 *   <li>a mixed set keeps its non-SCM role; a set without SCM is untouched (control);</li>
 *   <li>a partnership's {@code delegated_scope} and a participant's {@code participant_scope} lose
 *       {@code SCM_OPERATOR} and gain the eight roles; a partnership without it is untouched (control);</li>
 *   <li>a second run changes 0 rows.</li>
 * </ul>
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
class V0056ScmRoleMigrationIntegrationTest extends AbstractIntegrationTest {

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

    private static final String MIGRATION = "/db/migration/V0056__scm_fine_grained_domain_roles.sql";

    static final List<String> SCM_ROLES = List.of(
            "SCM_PROCUREMENT_READ", "SCM_PROCUREMENT_WRITE",
            "SCM_DEMAND_PLANNING_READ", "SCM_DEMAND_PLANNING_WRITE",
            "SCM_LOGISTICS_READ", "SCM_LOGISTICS_WRITE",
            "SCM_INVENTORY_VISIBILITY_READ", "SCM_INVENTORY_VISIBILITY_WRITE");

    static final String HOST = "00000000-0000-7000-8000-000000792a01";
    static final String PARTNER_OP = "00000000-0000-7000-8000-000000792a02";

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PlatformTransactionManager transactionManager;

    private static List<String> migrationStatements() throws IOException {
        String sql;
        try (InputStream in = V0056ScmRoleMigrationIntegrationTest.class.getResourceAsStream(MIGRATION)) {
            assertThat(in).as("V0056 on the classpath").isNotNull();
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
    @DisplayName("🔴 after boot: legacy-compat = all-domains-full = the catalog; scm-full = the eight roles; no set holds SCM_OPERATOR; 20 presets")
    void presetsAfterBoot() throws IOException {
        assertThat(migrationStatements()).as("the file is present").isNotEmpty();
        for (String key : List.of("legacy-compat", "all-domains-full")) {
            assertThat(rolesOfPreset(key)).as(key).containsExactlyInAnyOrderElementsOf(DomainRoleCatalog.roleNames());
        }
        assertThat(rolesOfPreset("scm-full")).containsExactlyInAnyOrderElementsOf(SCM_ROLES);
        assertThat(rolesOfPreset("scm-read")).containsExactlyInAnyOrder(
                "SCM_PROCUREMENT_READ", "SCM_DEMAND_PLANNING_READ", "SCM_LOGISTICS_READ",
                "SCM_INVENTORY_VISIBILITY_READ");
        assertThat(rolesOfPreset("scm-procurement-operate"))
                .containsExactlyInAnyOrder("SCM_PROCUREMENT_READ", "SCM_PROCUREMENT_WRITE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM domain_permission_set_role WHERE role_name = 'SCM_OPERATOR'", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM domain_permission_set WHERE tenant_id IS NULL", Integer.class)).isEqualTo(20);
    }

    @Test
    @DisplayName("🔴 Q5: every set / partnership holding SCM_OPERATOR → the eight SCM roles; controls untouched; re-run changes 0 rows")
    void rewritesEverySetAndPartnership() throws IOException {
        List<String> statements = migrationStatements();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            status.setRollbackOnly();
            long full = tenantSet("t792-a", "s792-full", List.of("scm|SCM_OPERATOR"));
            long mixed = tenantSet("t792-a", "s792-mixed", List.of("scm|SCM_OPERATOR", "wms|INVENTORY_READ"));
            long wmsOnly = tenantSet("t792-a", "s792-wms", List.of("wms|OUTBOUND_READ"));
            operator(HOST, "t792-host");
            operator(PARTNER_OP, "t792-partner");
            long withScm = partnership("t792-host", "t792-partner",
                    "{\"domains\": [\"scm\", \"wms\"], \"roles\": [\"OUTBOUND_READ\", \"SCM_OPERATOR\"]}");
            long withoutScm = partnership("t792-host", "t792-other",
                    "{\"domains\": [\"wms\"], \"roles\": [\"OUTBOUND_READ\"]}");
            jdbcTemplate.update("""
                    INSERT INTO tenant_partnership_participant (partnership_id, operator_id, participant_scope, assigned_at)
                    SELECT ?, o.id, '{"domains": ["scm"], "roles": ["SCM_OPERATOR"]}', NOW(6)
                      FROM admin_operators o WHERE o.operator_id = ?
                    """, withScm, PARTNER_OP);

            for (String s : statements) {
                jdbcTemplate.update(s);
            }

            assertThat(roles(full)).containsExactlyInAnyOrderElementsOf(SCM_ROLES);
            List<String> mixedExpected = new ArrayList<>(SCM_ROLES);
            mixedExpected.add("INVENTORY_READ");
            assertThat(roles(mixed)).containsExactlyInAnyOrderElementsOf(mixedExpected);
            assertThat(roles(wmsOnly)).as("control: a set without SCM is untouched").containsExactly("OUTBOUND_READ");

            List<String> delegatedExpected = new ArrayList<>(List.of("OUTBOUND_READ"));
            delegatedExpected.addAll(SCM_ROLES);
            assertThat(jsonRoles("SELECT JSON_EXTRACT(delegated_scope, '$.roles') FROM tenant_partnership WHERE id = ?",
                    withScm)).containsExactlyElementsOf(delegatedExpected);
            assertThat(jsonRoles("SELECT JSON_EXTRACT(delegated_scope, '$.roles') FROM tenant_partnership WHERE id = ?",
                    withoutScm)).as("control: untouched").containsExactly("OUTBOUND_READ");
            assertThat(jsonRoles("""
                    SELECT JSON_EXTRACT(participant_scope, '$.roles') FROM tenant_partnership_participant
                     WHERE partnership_id = ?
                    """, withScm)).containsExactlyElementsOf(SCM_ROLES);

            int second = 0;
            for (String s : statements) {
                second += jdbcTemplate.update(s);
            }
            assertThat(second).as("second run changes 0 rows").isZero();
        });
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM domain_permission_set WHERE tenant_id = 't792-a'", Integer.class))
                .as("rolled back").isZero();
    }

    private List<String> rolesOfPreset(String systemKey) {
        return jdbcTemplate.queryForList("""
                SELECT r.role_name FROM domain_permission_set_role r
                  JOIN domain_permission_set s ON s.id = r.set_id WHERE s.system_key = ?
                """, String.class, systemKey);
    }

    private long tenantSet(String tenantId, String name, List<String> domainRoles) {
        String setId = java.util.UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO domain_permission_set
                  (set_id, tenant_id, system_key, name, description, created_by, created_at, updated_at, version)
                VALUES (?, ?, NULL, ?, NULL, NULL, NOW(6), NOW(6), 0)
                """, setId, tenantId, name);
        Long id = jdbcTemplate.queryForObject("SELECT id FROM domain_permission_set WHERE set_id = ?", Long.class, setId);
        for (String dr : domainRoles) {
            String[] p = dr.split("\\|");
            jdbcTemplate.update("INSERT INTO domain_permission_set_role (set_id, domain_key, role_name) VALUES (?, ?, ?)",
                    id, p[0], p[1]);
        }
        return id;
    }

    private List<String> roles(long setId) {
        return jdbcTemplate.queryForList(
                "SELECT role_name FROM domain_permission_set_role WHERE set_id = ?", String.class, setId);
    }

    private void operator(String uuid, String tenantId) {
        jdbcTemplate.update("""
                INSERT INTO admin_operators
                  (operator_id, tenant_id, email, password_hash, display_name, status, created_at, updated_at, version)
                VALUES (?, ?, ?, 'x', 'V0056 Op', 'ACTIVE', NOW(6), NOW(6), 0)
                """, uuid, tenantId, uuid + "@example.com");
    }

    private long partnership(String host, String partner, String delegatedScope) {
        String pid = java.util.UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO tenant_partnership
                  (partnership_id, host_tenant_id, partner_tenant_id, status, delegated_scope,
                   invited_at, created_at, updated_at, version)
                VALUES (?, ?, ?, 'ACTIVE', ?, NOW(6), NOW(6), NOW(6), 0)
                """, pid, host, partner, delegatedScope);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM tenant_partnership WHERE partnership_id = ?", Long.class, pid);
        return id;
    }

    /** A JSON array of strings read back as a Java list, in array order. */
    private List<String> jsonRoles(String sql, long id) {
        String json = jdbcTemplate.queryForObject(sql, String.class, id);
        List<String> out = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([A-Z_]+)\"").matcher(json == null ? "" : json);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }
}
