package com.example.admin.integration;

import com.example.admin.support.OperatorJwtTestFixture;
import com.example.testsupport.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-777 — {@code GET /api/admin/operators/lookup} against real MySQL (Flyway schema) and
 * the REAL {@code QueryTenantScopeGate} / {@code PermissionEvaluator}. What the slice test cannot
 * measure: the JPQL membership predicate (HOME ∪ ASSIGNED), the ACTIVE + {@code oidc_subject}
 * filters, and that an operator with NO role at all is served.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class OperatorLookupIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofMinutes(3));

    static OperatorJwtTestFixture jwt;
    static String signingKeyPem;

    @BeforeAll
    static void setupShared() throws IOException {
        jwt = new OperatorJwtTestFixture();
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
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private static final String T = "m777-corp";
    private static final String OTHER = "m777-other";
    private static final String HR = "00000000-0000-7000-8000-000000077701";        // no role at all
    private static final String HOME_OP = "00000000-0000-7000-8000-000000077702";
    private static final String ASSIGNED_OP = "00000000-0000-7000-8000-000000077703";
    private static final String SUSPENDED_OP = "00000000-0000-7000-8000-000000077704";
    private static final String UNLINKED_OP = "00000000-0000-7000-8000-000000077705";
    private static final String OUTSIDER = "00000000-0000-7000-8000-000000077706";

    @BeforeEach
    void seed() {
        operator(HR, T, "hr@m777.example", "ACTIVE", null);
        operator(HOME_OP, T, "kim@m777.example", "ACTIVE", "acc-777-home");
        operator(ASSIGNED_OP, OTHER, "kim@m777.example", "ACTIVE", "acc-777-assigned");
        assign(ASSIGNED_OP, T);
        operator(SUSPENDED_OP, T, "gone@m777.example", "SUSPENDED", "acc-777-suspended");
        operator(UNLINKED_OP, T, "nolink@m777.example", "ACTIVE", null);
        operator(OUTSIDER, OTHER, "out@m777.example", "ACTIVE", "acc-777-out");
    }

    private String hrToken() {
        return "Bearer " + jwt.operatorToken(HR);
    }

    @Test
    @DisplayName("HOME ∪ ASSIGNED operators with the e-mail → their oidc_subject, sorted by home tenant (operator holding no role)")
    void finds_home_and_assigned_operators() throws Exception {
        mockMvc.perform(get("/api/admin/operators/lookup")
                        .param("email", "KIM@m777.example").param("tenantId", T)
                        .header("Authorization", hrToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].accountId").value("acc-777-home"))
                .andExpect(jsonPath("$.content[0].tenantId").value(T))
                .andExpect(jsonPath("$.content[1].accountId").value("acc-777-assigned"))
                .andExpect(jsonPath("$.content[1].tenantId").value(OTHER));
    }

    @Test
    @DisplayName("SUSPENDED · no oidc_subject · other tenant's operator → empty, like an unknown e-mail")
    void filters_people_who_cannot_enter_the_tenant() throws Exception {
        for (String email : new String[] {"gone@m777.example", "nolink@m777.example",
                "out@m777.example", "nobody@m777.example"}) {
            String body = mockMvc.perform(get("/api/admin/operators/lookup")
                            .param("email", email).param("tenantId", T)
                            .header("Authorization", hrToken()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).as(email).isEqualTo("{\"content\":[]}");
        }
    }

    @Test
    @DisplayName("out-of-scope tenant → 200 byte-identical to not-found (real gate)")
    void out_of_scope_is_not_found() throws Exception {
        String body = mockMvc.perform(get("/api/admin/operators/lookup")
                        .param("email", "out@m777.example").param("tenantId", OTHER)
                        .header("Authorization", hrToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).isEqualTo("{\"content\":[]}");
    }

    private void operator(String uuid, String tenant, String email, String status, String oidcSubject) {
        jdbcTemplate.update("""
                INSERT IGNORE INTO admin_operators
                  (operator_id, tenant_id, email, password_hash, display_name, status,
                   oidc_subject, created_at, updated_at, version)
                VALUES (?, ?, ?, NULL, ?, ?, ?, NOW(6), NOW(6), 0)
                """, uuid, tenant, email, "M777 " + uuid.substring(uuid.length() - 2), status, oidcSubject);
    }

    private void assign(String uuid, String tenant) {
        jdbcTemplate.update("""
                INSERT IGNORE INTO operator_tenant_assignment (operator_id, tenant_id, granted_at, granted_by, permission_set_id)
                SELECT o.id, ?, NOW(6), NULL, NULL FROM admin_operators o WHERE o.operator_id = ?
                """, tenant, uuid);
    }
}
