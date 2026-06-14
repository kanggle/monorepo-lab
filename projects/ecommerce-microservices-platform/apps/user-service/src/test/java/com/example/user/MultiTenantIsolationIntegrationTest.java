package com.example.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M6 — user-service cross-tenant leak regression (multi-tenant.md M6,
 * ADR-MONO-030 §2.3 AC-4; TASK-BE-367). Drives the full request path through
 * {@code TenantContextFilter} (gateway {@code X-Tenant-Id} header → request tenant
 * context) so it proves M2 layer 2 (context propagation) + layer 3 (persistence
 * {@code WHERE tenant_id}) together: tenant A's user is invisible to tenant B
 * (404 single-read — existence hidden, M3; list exclusion), and a no-header request
 * resolves to the default tenant (net-zero, D8) which a real tenant still cannot
 * see.
 *
 * <p>Profiles are created by the {@code UserSignedUp} consumer (no HTTP POST seam),
 * so each tenant's row is seeded deterministically via {@link JdbcTemplate} with an
 * explicit {@code tenant_id} (no Kafka timing), then every assertion runs through
 * the admin HTTP surface so the tenant filter + persistence scoping are exercised
 * end-to-end. The Docker-free {@code :check} slice never loads the real wiring; this
 * Testcontainers {@code @SpringBootTest} is the authoritative isolation proof
 * (feedback_spring_boot_diagnostic_patterns §14-17). Pinned to
 * {@link UserServiceApplication} so a bare-{@code @SpringBootTest} configuration
 * ambiguity can never bite.
 */
@SpringBootTest(classes = UserServiceApplication.class)
@AutoConfigureMockMvc
@Tag("integration")
@Testcontainers
@DisplayName("멀티테넌트 격리(M6) 통합 테스트 — user cross-tenant leak 회귀")
class MultiTenantIsolationIntegrationTest {

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String ROLE_HEADER = "X-User-Role";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";
    private static final String DEFAULT_TENANT = "ecommerce";

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("user_db")
            .withUsername("user_user")
            .withPassword("user_pass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // No Kafka broker is started: the publisher/consumer are @Profile("!standalone")
        // beans that only connect lazily on a real event; these read-path assertions
        // never publish, so a dummy bootstrap address keeps wiring happy.
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Seeds a user_profiles row with an explicit tenant_id (the consumer's create path). */
    private UUID seedUser(String tenantId, String email) {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO user_profiles (id, tenant_id, user_id, email, name, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?)",
                id, tenantId, userId, email, "격리테스트",
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        return userId;
    }

    @Test
    @DisplayName("관리자 목록 조회는 자기 테넌트 사용자만 포함한다")
    void crossTenantAdminListIsScopedToTenant() throws Exception {
        String email = "list-" + System.nanoTime() + "@example.com";
        UUID userIdA = seedUser(TENANT_A, email);

        // tenant B's admin list does NOT contain tenant A's user.
        mockMvc.perform(get("/api/admin/users")
                        .header(ROLE_HEADER, ROLE_ADMIN)
                        .header(TENANT_HEADER, TENANT_B)
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(userIdA.toString()))));

        // tenant A's admin list DOES contain it.
        mockMvc.perform(get("/api/admin/users")
                        .header(ROLE_HEADER, ROLE_ADMIN)
                        .header(TENANT_HEADER, TENANT_A)
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString(userIdA.toString())));
    }

    @Test
    @DisplayName("테넌트 A 사용자를 테넌트 B 컨텍스트로 단건 조회하면 404 (A 컨텍스트로는 200)")
    void crossTenantAdminDetailRead_returns404() throws Exception {
        String email = "detail-" + System.nanoTime() + "@example.com";
        UUID userIdA = seedUser(TENANT_A, email);

        // tenant B cannot see tenant A's user — 404, not 403 (existence hidden, M3).
        mockMvc.perform(get("/api/admin/users/{userId}", userIdA)
                        .header(ROLE_HEADER, ROLE_ADMIN)
                        .header(TENANT_HEADER, TENANT_B))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_PROFILE_NOT_FOUND"));

        // tenant A sees its own user.
        mockMvc.perform(get("/api/admin/users/{userId}", userIdA)
                        .header(ROLE_HEADER, ROLE_ADMIN)
                        .header(TENANT_HEADER, TENANT_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userIdA.toString()))
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    @DisplayName("net-zero(D8): X-Tenant-Id 부재 = default 테넌트로 resolve, 실 테넌트는 못 본다")
    void noTenantHeader_resolvesToDefaultTenant() throws Exception {
        String email = "default-" + System.nanoTime() + "@example.com";
        UUID userIdDefault = seedUser(DEFAULT_TENANT, email);

        // visible without a tenant header (default tenant)...
        mockMvc.perform(get("/api/admin/users/{userId}", userIdDefault)
                        .header(ROLE_HEADER, ROLE_ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userIdDefault.toString()));

        // ...but a real tenant (tenant-a) cannot see the default-tenant user.
        mockMvc.perform(get("/api/admin/users/{userId}", userIdDefault)
                        .header(ROLE_HEADER, ROLE_ADMIN)
                        .header(TENANT_HEADER, TENANT_A))
                .andExpect(status().isNotFound());
    }
}
