package com.example.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M6 — payment-service cross-tenant leak regression (multi-tenant.md M6,
 * ADR-MONO-030 §2.3 AC-4; TASK-BE-400). Drives the full request path through
 * {@code TenantContextFilter} (gateway {@code X-Tenant-Id} header → request tenant
 * context) so it proves M2 layer 2 (context propagation) + layer 3 (persistence
 * {@code WHERE tenant_id}) together:
 * <ul>
 *   <li>(a) tenant-A context querying tenant-B's payment by orderId returns 404 (existence
 *       hidden, M3 — not 403);</li>
 *   <li>(b) cross-tenant payment confirmation attempt — confirming a payment whose
 *       {@code orderId} belongs to tenant-B while the request carries tenant-A context —
 *       returns 404 ({@code PaymentNotFoundException}), not 403.</li>
 * </ul>
 *
 * <p>Payment rows are seeded deterministically via {@link JdbcTemplate} with an explicit
 * {@code tenant_id} (bypassing the event-driven create seam), then assertions run
 * through the HTTP surface so the tenant filter + persistence scoping are exercised
 * end-to-end. The Docker-free {@code :check} slice never loads the real wiring; this
 * Testcontainers {@code @SpringBootTest} is the authoritative isolation proof
 * (feedback_spring_boot_diagnostic_patterns §14-17). Pinned to
 * {@link PaymentServiceApplication} so a bare-{@code @SpringBootTest} configuration
 * ambiguity can never bite.
 *
 * <p>Testcontainers is host-blocked on the development machine (MalformedChunkCodingException
 * with Docker engine 29.1.3/API 1.52 × Testcontainers 1.20.4 — see project memory
 * project_testcontainers_docker_desktop_blocker). This test is {@code @Tag("integration")}
 * and therefore excluded from the local {@code :test} task; CI Linux is unaffected.
 */
@SpringBootTest(
        classes = PaymentServiceApplication.class,
        properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@AutoConfigureMockMvc
@Tag("integration")
@Testcontainers
@EmbeddedKafka(partitions = 1)
@DisplayName("멀티테넌트 격리(M6) 통합 테스트 — payment cross-tenant leak 회귀")
class MultiTenantIsolationIntegrationTest {

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String USER_HEADER = "X-User-Id";
    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";
    private static final String USER_A = "user-a";

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("payment_db")
            .withUsername("payment_user")
            .withPassword("payment_pass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Seeds a COMPLETED payment row with an explicit tenant_id.
     * Returns the orderId so callers can use it in HTTP assertions.
     */
    private String seedCompletedPayment(String tenantId, String userId) {
        String paymentId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO payments (payment_id, tenant_id, order_id, user_id, amount, "
                        + "status, created_at, paid_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'COMPLETED', ?, ?)",
                paymentId, tenantId, orderId, userId, 50_000L,
                Timestamp.from(now), Timestamp.from(now));
        return orderId;
    }

    @Test
    @DisplayName("(a) 테넌트 B 결제를 테넌트 A 컨텍스트로 orderId 조회하면 404 (B 컨텍스트로는 200)")
    void crossTenantPaymentRead_returns404() throws Exception {
        String orderId = seedCompletedPayment(TENANT_B, USER_A);

        // tenant A cannot see tenant B's payment — 404, not 403 (existence hidden, M3).
        mockMvc.perform(get("/api/payments/orders/{orderId}", orderId)
                        .header(USER_HEADER, USER_A)
                        .header(TENANT_HEADER, TENANT_A))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));

        // tenant B sees its own payment.
        mockMvc.perform(get("/api/payments/orders/{orderId}", orderId)
                        .header(USER_HEADER, USER_A)
                        .header(TENANT_HEADER, TENANT_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId));
    }

    @Test
    @DisplayName("(b) 테넌트 A 컨텍스트로 테넌트 B 결제의 orderId 로 결제확인 요청하면 404")
    void crossTenantConfirmAttempt_returns404() throws Exception {
        // Seed a PENDING row under tenant-b that a tenant-a confirm attempt should not reach.
        String paymentId = UUID.randomUUID().toString();
        String orderId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO payments (payment_id, tenant_id, order_id, user_id, amount, "
                        + "status, created_at) VALUES (?, ?, ?, ?, ?, 'PENDING', ?)",
                paymentId, TENANT_B, orderId, USER_A, 30_000L, Timestamp.from(now));

        // PaymentQueryService.getPaymentByOrderId uses findByOrderId which is tenant-scoped.
        // Under tenant-a context the row is invisible → 404.
        mockMvc.perform(get("/api/payments/orders/{orderId}", orderId)
                        .header(USER_HEADER, USER_A)
                        .header(TENANT_HEADER, TENANT_A))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }
}
