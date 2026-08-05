package com.example.admin.integration;

import com.example.admin.support.OperatorJwtTestFixture;
import com.example.testsupport.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-576 (and retroactively TASK-BE-571) — the portfolio demo operator's seed
 * rows, asserted against the DB the real migration produced.
 *
 * <p><b>Why this test exists.</b> {@code R__seed_demo_operator.sql} carries the whole
 * console side of the demo identity, and until now <b>nothing asserted any of it</b>.
 * Every row in it is silently deletable: drop the {@code oidc_subject} literal and the
 * console fail-closes to a 401 that the UI renders with the same text a 5s timeout
 * produces; drop a tenant assignment and the console renders <b>200 with empty
 * lists</b>. Neither shows up as a failure anywhere. A seed nobody asserts is a seed
 * that drifts.
 *
 * <p><b>What the assignment set means.</b> The two tenants are not redundant — they
 * carry different things, and that is exactly what TASK-BE-576 had to discover:
 * <ul>
 *   <li>{@code demo-corp} — <b>authorization</b>. Its five ACTIVE domain subscriptions
 *       derive {@code ECOMMERCE/WMS/SCM/ERP/FINANCE_OPERATOR} at assume time.</li>
 *   <li>{@code ecommerce} — <b>visibility</b>. Every storefront row is written under
 *       {@code tenant_id='ecommerce'} (the gateway pins the consumer token's tenant,
 *       and the catalog itself takes that value from the column default), and each
 *       service filters reads by the request's tenant. Without this row the operator
 *       sees an empty console <em>and cannot write</em> — advancing a shipment the
 *       buyer's own token had just returned failed with 404 SHIPPING_NOT_FOUND.</li>
 * </ul>
 *
 * <p>The set is asserted <b>exactly</b>, in both directions. A missing entry is the
 * regression this test was written for; an unexpected extra one means someone widened
 * a demo operator's reach into a tenant nobody reviewed, which on this seed is the
 * more dangerous direction.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
class DemoOperatorSeedIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofMinutes(3));

    /**
     * MUST equal the OIDC {@code sub} of the console login — the account UUID on the
     * matching {@code iam}-tenant credential row (auth-service migration-dev V9001).
     * Operator resolution has been account_id-ONLY since TASK-MONO-299, so a drifted
     * value here does not degrade: it fail-closes to 401.
     */
    private static final String DEMO_OIDC_SUBJECT = "0199de70-0000-7000-8000-00000000ad03";

    static String signingKeyPem;

    @BeforeAll
    static void setupShared() throws IOException {
        OperatorJwtTestFixture jwt = new OperatorJwtTestFixture();
        java.security.PrivateKey pk = extractPrivateKey(jwt);
        signingKeyPem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pk.getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
    }

    private static java.security.PrivateKey extractPrivateKey(OperatorJwtTestFixture fixture) {
        try {
            var field = OperatorJwtTestFixture.class.getDeclaredField("keyPair");
            field.setAccessible(true);
            java.security.KeyPair kp = (java.security.KeyPair) field.get(fixture);
            return kp.getPrivate();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("admin.jwt.active-signing-kid", () -> "test-key-001");
        registry.add("admin.jwt.signing-keys.test-key-001", () -> signingKeyPem);
        registry.add("admin.jwt.issuer", () -> "admin-service");
        registry.add("admin.jwt.expected-token-type", () -> "admin");
        registry.add("admin.auth-service.base-url", () -> "http://localhost:18085");
        registry.add("admin.account-service.base-url", () -> "http://localhost:18085");
        registry.add("admin.security-service.base-url", () -> "http://localhost:18085");
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("seed: demo-operator exists, ACTIVE, home tenant demo-corp, and carries the console login's oidc_subject verbatim")
    void demoOperatorSeeded() {
        List<String> rows = jdbcTemplate.queryForList("""
                SELECT CONCAT_WS('|', tenant_id, email, status, oidc_subject)
                  FROM admin_operators WHERE operator_id = 'demo-operator'
                """, String.class);

        assertThat(rows)
                .as("demo-operator row (R__seed_demo_operator.sql). Absent = the whole "
                        + "portfolio demo console is unreachable.")
                .containsExactly("demo-corp|demo@demo.com|ACTIVE|" + DEMO_OIDC_SUBJECT);
    }

    @Test
    @DisplayName("seed: demo-operator may assume exactly {demo-corp, ecommerce} — demo-corp carries the roles, ecommerce carries the data")
    void demoOperatorTenantAssignmentsAreExactlyTheTwo() {
        List<String> tenants = jdbcTemplate.queryForList("""
                SELECT a.tenant_id
                  FROM operator_tenant_assignment a
                  JOIN admin_operators o ON o.id = a.operator_id
                 WHERE o.operator_id = 'demo-operator'
                 ORDER BY a.tenant_id
                """, String.class);

        assertThat(tenants)
                .as("Dropping `ecommerce` returns the console to TASK-BE-576's symptom: the "
                        + "gateway still ACCEPTS the demo-corp token (entitlement-trust), so every "
                        + "E-Commerce list renders 200 with zero rows and the operator cannot write. "
                        + "Nothing else in this repo fails when that row goes missing.")
                .containsExactly("demo-corp", "ecommerce");
    }

    @Test
    @DisplayName("seed: the demo operator's SUPER_ADMIN grant is bound to its home tenant, not to the data tenant")
    void superAdminGrantStaysOnHomeTenant() {
        List<String> grants = jdbcTemplate.queryForList("""
                SELECT CONCAT_WS('|', r.name, g.tenant_id)
                  FROM admin_operator_roles g
                  JOIN admin_operators o ON o.id = g.operator_id
                  JOIN admin_roles r ON r.id = g.role_id
                 WHERE o.operator_id = 'demo-operator'
                 ORDER BY r.name, g.tenant_id
                """, String.class);

        // The second assignment (TASK-BE-576) deliberately grants NO extra role: assuming
        // `ecommerce` derives ECOMMERCE_OPERATOR from that tenant's own subscriptions
        // (ADR-MONO-035 — domain-ops pages are gated by entitlement, not by this RBAC row).
        // If a future change starts minting per-tenant role grants here, this assertion is
        // where that shows up rather than in a widened production surface.
        assertThat(grants).containsExactly("SUPER_ADMIN|demo-corp");
    }
}
