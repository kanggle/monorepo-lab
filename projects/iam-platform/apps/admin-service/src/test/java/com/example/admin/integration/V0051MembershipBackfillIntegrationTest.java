package com.example.admin.integration;

import com.example.testsupport.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ConnectionCallback;
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
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-MONO-784 S2 (ADR-MONO-082 b · decisions D-2 · D-3) — V0051 on real MySQL.
 *
 * <p>Flyway already ran V0051 when this context started (on a DB that held no hand-made operator
 * rows yet), so a green boot alone proves only that the SQL parses. This test re-executes the
 * migration's OWN statements — read from the classpath file, not re-typed — against rows the
 * backfill has to act on:
 * <ul>
 *   <li>the PRIMARY KEY is {@code (operator_id, role_id, tenant_id)} and the same role can now sit in
 *       two companies on one operator row;</li>
 *   <li>re-running the guarded PK swap is a no-op;</li>
 *   <li>the backfill gives a home-only operator its home membership AND a membership for a role-row
 *       tenant, gives a {@code '*'} operator nothing, leaves an existing membership (with its
 *       {@code org_scope}) untouched — and a SECOND run inserts zero rows.</li>
 * </ul>
 *
 * <p>The data half runs inside one transaction that is rolled back, so the backfill cannot hand
 * memberships to other test classes' fixture operators in the shared database (several of them
 * deliberately assert that a home WITHOUT a membership is out of scope).
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
class V0051MembershipBackfillIntegrationTest extends AbstractIntegrationTest {

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

    private static final String MIGRATION =
            "/db/migration/V0051__admin_operator_roles_tenant_pk_and_membership_backfill.sql";

    static final String HOME_ONLY = "00000000-0000-7000-8000-000000784b01";
    static final String PLATFORM = "00000000-0000-7000-8000-000000784b02";
    static final String ALREADY_MEMBER = "00000000-0000-7000-8000-000000784b03";

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PlatformTransactionManager transactionManager;

    /** The migration's statements, comment lines stripped, split on ';'. */
    private static List<String> migrationStatements() throws IOException {
        String sql;
        try (InputStream in = V0051MembershipBackfillIntegrationTest.class.getResourceAsStream(MIGRATION)) {
            assertThat(in).as("V0051 on the classpath").isNotNull();
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

    private static String backfillStatement() throws IOException {
        List<String> inserts = migrationStatements().stream()
                .filter(s -> s.startsWith("INSERT INTO operator_tenant_assignment"))
                .toList();
        assertThat(inserts).as("exactly one backfill statement in V0051").hasSize(1);
        return inserts.get(0);
    }

    @Test
    @DisplayName("V0051's guarded PK swap is a no-op on the live schema — it never shrinks the V0053 PK (+source_key) back to three columns")
    void guardedSwapIsNoOpOnRerun_andNeverUndoesV0053() throws IOException {
        // TASK-BE-633: V0053 widened the PK to (operator_id, role_id, tenant_id, source_key). V0051 asserted
        // its own three columns against the LIVE schema, which is no longer the live shape. What must
        // still hold is the guard: re-running V0051's swap block must change nothing — in particular it
        // must not drop source_key (that would collapse the per-source rows back into one).
        List<String> pkColumns = jdbcTemplate.queryForList("""
                SELECT column_name FROM information_schema.key_column_usage
                 WHERE table_schema = DATABASE() AND table_name = 'admin_operator_roles'
                   AND constraint_name = 'PRIMARY'
                 ORDER BY ordinal_position
                """, String.class);
        assertThat(pkColumns).containsExactly("operator_id", "role_id", "tenant_id", "source_key");

        // The PK block (SET / PREPARE / EXECUTE / DEALLOCATE) must run on ONE connection — the
        // prepared statement and the user variable are session state.
        List<String> pkBlock = migrationStatements().stream()
                .filter(s -> !s.startsWith("INSERT INTO operator_tenant_assignment"))
                .toList();
        assertThat(pkBlock).hasSize(4);
        jdbcTemplate.execute((ConnectionCallback<Void>) con -> {
            try (Statement st = con.createStatement()) {
                for (String s : pkBlock) {
                    st.execute(s);
                }
            }
            return null;
        });
        assertThat(jdbcTemplate.queryForList("""
                SELECT column_name FROM information_schema.key_column_usage
                 WHERE table_schema = DATABASE() AND table_name = 'admin_operator_roles'
                   AND constraint_name = 'PRIMARY'
                 ORDER BY ordinal_position
                """, String.class)).containsExactly("operator_id", "role_id", "tenant_id", "source_key");
    }

    @Test
    @DisplayName("V0051 backfill: home-only row gains home + role-row-tenant memberships · '*' none · existing row untouched · second run inserts 0")
    void backfill_isScopedAndIdempotent() throws IOException {
        String backfill = backfillStatement();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            status.setRollbackOnly(); // never leak memberships to other classes' fixtures
            operator(HOME_ONLY, "s2-acme");
            operator(PLATFORM, "*");
            operator(ALREADY_MEMBER, "s2-globex");
            // HOME_ONLY holds a role in its home AND (since 784 S2's PK) the same role in a second company.
            role(HOME_ONLY, "SUPPORT_READONLY", "s2-acme");
            role(HOME_ONLY, "SUPPORT_READONLY", "s2-initech");
            role(PLATFORM, "SUPER_ADMIN", "*");
            // ALREADY_MEMBER: a membership an admin narrowed (org_scope) — must survive byte-for-byte.
            jdbcTemplate.update("""
                    INSERT INTO operator_tenant_assignment
                      (operator_id, tenant_id, granted_at, granted_by, permission_set_id, org_scope)
                    SELECT o.id, 's2-globex', NOW(6), NULL, NULL, CAST('["dept-1"]' AS JSON)
                      FROM admin_operators o WHERE o.operator_id = ?
                    """, ALREADY_MEMBER);
            assertThat(memberships(HOME_ONLY)).as("before: the home alone, no membership").isEmpty();

            int first = jdbcTemplate.update(backfill);

            assertThat(memberships(HOME_ONLY))
                    .as("home + the role-row tenant, whole tenant / direct / no permission set")
                    .containsExactly("s2-acme|1|1|1", "s2-initech|1|1|1");
            assertThat(memberships(PLATFORM)).as("'*' is not a membership (ADR-082 D2)").isEmpty();
            assertThat(memberships(ALREADY_MEMBER)).containsExactly("s2-globex|0|1|1");
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT JSON_EXTRACT(a.org_scope, '$[0]') FROM operator_tenant_assignment a
                      JOIN admin_operators o ON o.id = a.operator_id WHERE o.operator_id = ?
                    """, String.class, ALREADY_MEMBER)).isEqualTo("\"dept-1\"");
            assertThat(first).as("at least the two HOME_ONLY rows (other pre-existing fixtures may add more)")
                    .isGreaterThanOrEqualTo(2);

            int second = jdbcTemplate.update(backfill);
            assertThat(second).as("idempotent — the second run inserts nothing").isZero();
            assertThat(memberships(HOME_ONLY)).hasSize(2);
        });
        // Rolled back: nothing of this test survives.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_operators WHERE operator_id IN (?, ?, ?)",
                Integer.class, HOME_ONLY, PLATFORM, ALREADY_MEMBER)).isZero();
    }

    private void operator(String uuid, String tenantId) {
        jdbcTemplate.update("""
                INSERT INTO admin_operators
                  (operator_id, tenant_id, email, password_hash, display_name, status,
                   created_at, updated_at, version)
                VALUES (?, ?, ?, 'x', 'V0051 Op', 'ACTIVE', NOW(6), NOW(6), 0)
                """, uuid, tenantId, uuid + "@example.com");
    }

    private void role(String uuid, String roleName, String tenantId) {
        int n = jdbcTemplate.update("""
                INSERT INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
                SELECT o.id, r.id, ?, NOW(6), NULL
                  FROM admin_operators o JOIN admin_roles r ON r.name = ?
                 WHERE o.operator_id = ?
                """, tenantId, roleName, uuid);
        assertThat(n).as("role row %s@%s inserted (3-column PK allows the same role in two companies)",
                roleName, tenantId).isEqualTo(1);
    }

    /** {@code tenant|org_scope IS NULL|permission_set_id IS NULL|group_origin IS NULL}, sorted. */
    private List<String> memberships(String uuid) {
        return jdbcTemplate.queryForList("""
                SELECT CONCAT_WS('|', a.tenant_id, a.org_scope IS NULL, a.permission_set_id IS NULL,
                                 a.group_origin IS NULL)
                  FROM operator_tenant_assignment a
                  JOIN admin_operators o ON o.id = a.operator_id
                 WHERE o.operator_id = ?
                 ORDER BY a.tenant_id
                """, String.class, uuid);
    }
}
