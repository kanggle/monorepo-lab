package com.example.admin.integration;

import com.example.admin.domain.rbac.DomainPermissionSetPresets;
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
 * TASK-MONO-794 (ADR-MONO-083 P7 · owner decisions, 2026-10-16 UTC) — V0058 on real MySQL. Same method as
 * {@code V0057ErpRoleMigrationIntegrationTest}: the boot ran V0058 before any fixture existed (proves the SQL parses
 * and the presets); the data half re-executes the file's OWN statements against fixtures inside one rolled-back
 * transaction:
 * <ul>
 *   <li>a tenant set holding only {@code FINANCE_OPERATOR} («Finance 전체» when it was made) → the five Finance roles
 *       INCLUDING {@code FINANCE_LEDGER_CONTROL}, no {@code FINANCE_OPERATOR} (the existing operator keeps the same
 *       API set — AC-2, owner decision Q1);</li>
 *   <li>a mixed set keeps its non-Finance role; a set without Finance is untouched (control);</li>
 *   <li>a partnership's {@code delegated_scope} and a participant's {@code participant_scope} lose
 *       {@code FINANCE_OPERATOR} and gain the five roles; one without it is untouched (control);</li>
 *   <li>a second run changes 0 rows.</li>
 * </ul>
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
class V0058FinanceRoleMigrationIntegrationTest extends AbstractIntegrationTest {

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

    private static final String MIGRATION = "/db/migration/V0058__finance_fine_grained_domain_roles.sql";

    static final List<String> FINANCE_ROLES = List.of(
            "FINANCE_ACCOUNT_READ", "FINANCE_ACCOUNT_WRITE", "FINANCE_LEDGER_READ", "FINANCE_LEDGER_WRITE",
            "FINANCE_LEDGER_CONTROL");

    static final String HOST = "00000000-0000-7000-8000-000000794a01";
    static final String PARTNER_OP = "00000000-0000-7000-8000-000000794a02";

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PlatformTransactionManager transactionManager;

    private static List<String> migrationStatements() throws IOException {
        String sql;
        try (InputStream in = V0058FinanceRoleMigrationIntegrationTest.class.getResourceAsStream(MIGRATION)) {
            assertThat(in).as("V0058 on the classpath").isNotNull();
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
    @DisplayName("🔴 after boot: legacy-compat = all-domains-full = the catalog; finance-full = the five roles; finance-ledger-operate has NO CONTROL; no set holds FINANCE_OPERATOR; 26 presets")
    void presetsAfterBoot() throws IOException {
        assertThat(migrationStatements()).as("the file is present").isNotEmpty();
        for (String key : List.of("legacy-compat", "all-domains-full")) {
            assertThat(rolesOfPreset(key)).as(key).containsExactlyInAnyOrderElementsOf(DomainRoleCatalog.roleNames());
        }
        assertThat(rolesOfPreset("finance-full")).containsExactlyInAnyOrderElementsOf(FINANCE_ROLES);
        assertThat(rolesOfPreset("finance-read")).containsExactlyInAnyOrder("FINANCE_ACCOUNT_READ", "FINANCE_LEDGER_READ");
        assertThat(rolesOfPreset("finance-account-operate"))
                .containsExactlyInAnyOrder("FINANCE_ACCOUNT_READ", "FINANCE_ACCOUNT_WRITE");
        assertThat(rolesOfPreset("finance-ledger-operate"))
                .as("owner decision Q1 — control is not part of the ledger-operate preset")
                .containsExactlyInAnyOrder("FINANCE_LEDGER_READ", "FINANCE_LEDGER_WRITE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM domain_permission_set_role WHERE role_name = 'FINANCE_OPERATOR'", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM domain_permission_set WHERE tenant_id IS NULL", Integer.class))
                .isEqualTo(DomainPermissionSetPresets.SYSTEM_KEYS.size())
                .isEqualTo(33); // + TASK-MONO-795 V0059 (7 E-commerce presets)
    }

    @Test
    @DisplayName("🔴 every set / partnership holding FINANCE_OPERATOR → the five Finance roles (CONTROL included); controls untouched; re-run changes 0 rows")
    void rewritesEverySetAndPartnership() throws IOException {
        List<String> statements = migrationStatements();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            status.setRollbackOnly();
            long full = tenantSet("t794-a", "s794-full", List.of("finance|FINANCE_OPERATOR"));
            long mixed = tenantSet("t794-a", "s794-mixed", List.of("finance|FINANCE_OPERATOR", "wms|INVENTORY_READ"));
            long wmsOnly = tenantSet("t794-a", "s794-wms", List.of("wms|OUTBOUND_READ"));
            operator(HOST, "t794-host");
            operator(PARTNER_OP, "t794-partner");
            long withFinance = partnership("t794-host", "t794-partner",
                    "{\"domains\": [\"finance\", \"wms\"], \"roles\": [\"OUTBOUND_READ\", \"FINANCE_OPERATOR\"]}");
            long withoutFinance = partnership("t794-host", "t794-other",
                    "{\"domains\": [\"wms\"], \"roles\": [\"OUTBOUND_READ\"]}");
            jdbcTemplate.update("""
                    INSERT INTO tenant_partnership_participant (partnership_id, operator_id, participant_scope, assigned_at)
                    SELECT ?, o.id, '{"domains": ["finance"], "roles": ["FINANCE_OPERATOR"]}', NOW(6)
                      FROM admin_operators o WHERE o.operator_id = ?
                    """, withFinance, PARTNER_OP);

            for (String s : statements) {
                jdbcTemplate.update(s);
            }

            assertThat(roles(full)).containsExactlyInAnyOrderElementsOf(FINANCE_ROLES);
            List<String> mixedExpected = new ArrayList<>(FINANCE_ROLES);
            mixedExpected.add("INVENTORY_READ");
            assertThat(roles(mixed)).containsExactlyInAnyOrderElementsOf(mixedExpected);
            assertThat(roles(wmsOnly)).as("control: a set without Finance is untouched").containsExactly("OUTBOUND_READ");

            List<String> delegatedExpected = new ArrayList<>(List.of("OUTBOUND_READ"));
            delegatedExpected.addAll(FINANCE_ROLES);
            assertThat(jsonRoles("SELECT JSON_EXTRACT(delegated_scope, '$.roles') FROM tenant_partnership WHERE id = ?",
                    withFinance)).containsExactlyElementsOf(delegatedExpected);
            assertThat(jsonRoles("SELECT JSON_EXTRACT(delegated_scope, '$.roles') FROM tenant_partnership WHERE id = ?",
                    withoutFinance)).as("control: untouched").containsExactly("OUTBOUND_READ");
            assertThat(jsonRoles("""
                    SELECT JSON_EXTRACT(participant_scope, '$.roles') FROM tenant_partnership_participant
                     WHERE partnership_id = ?
                    """, withFinance)).containsExactlyElementsOf(FINANCE_ROLES);

            int second = 0;
            for (String s : statements) {
                second += jdbcTemplate.update(s);
            }
            assertThat(second).as("second run changes 0 rows").isZero();
        });
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM domain_permission_set WHERE tenant_id = 't794-a'", Integer.class))
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
                VALUES (?, ?, ?, 'x', 'V0058 Op', 'ACTIVE', NOW(6), NOW(6), 0)
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
        return jdbcTemplate.queryForObject(
                "SELECT id FROM tenant_partnership WHERE partnership_id = ?", Long.class, pid);
    }

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
