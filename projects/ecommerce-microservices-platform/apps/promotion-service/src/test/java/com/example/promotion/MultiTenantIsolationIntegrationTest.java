package com.example.promotion;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M6 — promotion-service cross-tenant leak regression (multi-tenant.md M6,
 * ADR-MONO-030 §2.3 AC-4; TASK-BE-368). Drives the full request path through
 * {@code TenantContextFilter} (gateway {@code X-Tenant-Id} header → request tenant
 * context) so it proves M2 layer 2 (context propagation) + layer 3 (persistence
 * {@code WHERE tenant_id}) together: tenant A's promotion is invisible to tenant B
 * (404 single-read — existence hidden, M3; list exclusion), tenant A's coupon is not
 * visible under tenant B for the consumer "my coupons" read, and a no-header request
 * resolves to the default tenant (net-zero, D8) which a real tenant still cannot see.
 *
 * <p>Promotions/coupons are seeded deterministically via {@link JdbcTemplate} with an
 * explicit {@code tenant_id} (no HTTP create timing / no per-tenant create seam), then
 * every assertion runs through the HTTP surface so the tenant filter + persistence
 * scoping are exercised end-to-end. The Docker-free {@code :check} slice never loads
 * the real wiring; this Testcontainers {@code @SpringBootTest} is the authoritative
 * isolation proof. Pinned to {@link PromotionServiceApplication} so a
 * bare-{@code @SpringBootTest} configuration ambiguity can never bite.
 */
@SpringBootTest(
        classes = PromotionServiceApplication.class,
        properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@AutoConfigureMockMvc
@Tag("integration")
@Testcontainers
@EmbeddedKafka(partitions = 1)
@DisplayName("멀티테넌트 격리(M6) 통합 테스트 — promotion cross-tenant leak 회귀")
class MultiTenantIsolationIntegrationTest {

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String ROLE_HEADER = "X-User-Role";
    private static final String USER_HEADER = "X-User-Id";
    private static final String ROLE_ADMIN = "ECOMMERCE_OPERATOR";
    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";
    private static final String DEFAULT_TENANT = "ecommerce";

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("promotion_db")
            .withUsername("promotion_user")
            .withPassword("promotion_pass");

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

    /** Seeds a promotions row with an explicit tenant_id; returns the promotion id. */
    private String seedPromotion(String tenantId, String name) {
        String promotionId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO promotions (promotion_id, tenant_id, name, description, discount_type, "
                        + "discount_value, max_discount_amount, max_issuance_count, issued_count, "
                        + "start_date, end_date, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'FIXED', 3000, 0, 100, 0, ?, ?, ?, ?)",
                promotionId, tenantId, name, "격리테스트",
                Timestamp.from(now.minusSeconds(3600)), Timestamp.from(now.plusSeconds(31_536_000L)),
                Timestamp.from(now), Timestamp.from(now));
        return promotionId;
    }

    /** Seeds an ISSUED coupon row with an explicit tenant_id; returns the coupon id. */
    private String seedCoupon(String tenantId, String promotionId, String userId) {
        String couponId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO coupons (coupon_id, tenant_id, promotion_id, user_id, status, "
                        + "issued_at, expires_at) VALUES (?, ?, ?, ?, 'ISSUED', ?, ?)",
                couponId, tenantId, promotionId, userId,
                Timestamp.from(now), Timestamp.from(now.plusSeconds(31_536_000L)));
        return couponId;
    }

    @Test
    @DisplayName("관리자 목록 조회는 자기 테넌트 프로모션만 포함한다")
    void crossTenantAdminListIsScopedToTenant() throws Exception {
        String promotionIdA = seedPromotion(TENANT_A, "list-" + System.nanoTime());

        // tenant B's admin list does NOT contain tenant A's promotion.
        mockMvc.perform(get("/api/promotions")
                        .header(ROLE_HEADER, ROLE_ADMIN)
                        .header(TENANT_HEADER, TENANT_B)
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(promotionIdA))));

        // tenant A's admin list DOES contain it.
        mockMvc.perform(get("/api/promotions")
                        .header(ROLE_HEADER, ROLE_ADMIN)
                        .header(TENANT_HEADER, TENANT_A)
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString(promotionIdA)));
    }

    @Test
    @DisplayName("테넌트 A 프로모션을 테넌트 B 컨텍스트로 단건 조회하면 404 (A 컨텍스트로는 200)")
    void crossTenantAdminDetailRead_returns404() throws Exception {
        String promotionIdA = seedPromotion(TENANT_A, "detail-" + System.nanoTime());

        // tenant B cannot see tenant A's promotion — 404, not 403 (existence hidden, M3).
        mockMvc.perform(get("/api/promotions/{id}", promotionIdA)
                        .header(ROLE_HEADER, ROLE_ADMIN)
                        .header(TENANT_HEADER, TENANT_B))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROMOTION_NOT_FOUND"));

        // tenant A sees its own promotion.
        mockMvc.perform(get("/api/promotions/{id}", promotionIdA)
                        .header(ROLE_HEADER, ROLE_ADMIN)
                        .header(TENANT_HEADER, TENANT_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promotionId").value(promotionIdA));
    }

    @Test
    @DisplayName("쿠폰 'my coupons' 조회는 자기 테넌트 쿠폰만 본다")
    void crossTenantCouponListScoped() throws Exception {
        String userId = "user-" + System.nanoTime();
        String promotionIdA = seedPromotion(TENANT_A, "coupon-" + System.nanoTime());
        String couponIdA = seedCoupon(TENANT_A, promotionIdA, userId);

        // Same user id under tenant B sees no tenant-a coupon.
        mockMvc.perform(get("/api/coupons/me")
                        .header(USER_HEADER, userId)
                        .header(TENANT_HEADER, TENANT_B)
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(couponIdA))));

        // Under tenant A the coupon is visible.
        mockMvc.perform(get("/api/coupons/me")
                        .header(USER_HEADER, userId)
                        .header(TENANT_HEADER, TENANT_A)
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString(couponIdA)));
    }

    @Test
    @DisplayName("net-zero(D8): X-Tenant-Id 부재 = default 테넌트로 resolve, 실 테넌트는 못 본다")
    void noTenantHeader_resolvesToDefaultTenant() throws Exception {
        String promotionIdDefault = seedPromotion(DEFAULT_TENANT, "default-" + System.nanoTime());

        // visible without a tenant header (default tenant)...
        mockMvc.perform(get("/api/promotions/{id}", promotionIdDefault)
                        .header(ROLE_HEADER, ROLE_ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.promotionId").value(promotionIdDefault));

        // ...but a real tenant (tenant-a) cannot see the default-tenant promotion.
        mockMvc.perform(get("/api/promotions/{id}", promotionIdDefault)
                        .header(ROLE_HEADER, ROLE_ADMIN)
                        .header(TENANT_HEADER, TENANT_A))
                .andExpect(status().isNotFound());
    }
}
