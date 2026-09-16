package com.example.promotion;

import com.example.promotion.application.service.StaleUsedCouponQueryService;
import com.example.promotion.domain.coupon.StaleUsedCoupon;
import com.example.promotion.domain.promotion.DiscountType;
import com.example.promotion.domain.promotion.Promotion;
import com.example.promotion.domain.promotion.PromotionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-INT-028 AC-8 — the stale-used sweep on a real Postgres.
 *
 * <p>What a mocked repository cannot show: that the JPQL really is tenant-agnostic, that its
 * {@code status}/{@code order_id}/{@code used_at} predicate excludes exactly what the contract says,
 * and that Flyway V10 created the index the sweep's ordering relies on.
 */
@SpringBootTest(classes = PromotionServiceApplication.class,
        properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@Tag("integration")
@Testcontainers
@EmbeddedKafka(partitions = 1)
@DisplayName("오래된 USED 쿠폰 목록 통합 테스트 (TASK-INT-028)")
class StaleUsedCouponsIntegrationTest {

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
    private StaleUsedCouponQueryService staleUsedCouponQueryService;
    @Autowired
    private PromotionRepository promotionRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Clock clock;

    private String activePromotionId() {
        Instant now = Instant.now(clock);
        Promotion promotion = Promotion.create("고아 쿠폰 테스트", "설명", DiscountType.FIXED, 5000, 0, 100,
                now.minusSeconds(30L * 86_400), now.plusSeconds(30L * 86_400), clock);
        promotionRepository.save(promotion);
        return promotion.getPromotionId();
    }

    /** Inserts a coupon row directly so tenant, status, order and used_at are exactly what the case needs. */
    private String seedCoupon(String promotionId, String tenantId, String status, String orderId,
                              Instant usedAt) {
        String couponId = UUID.randomUUID().toString();
        Instant now = Instant.now(clock);
        jdbc.update("INSERT INTO coupons (coupon_id, promotion_id, user_id, status, issued_at, used_at, "
                        + "expired_at, expires_at, order_id, tenant_id) VALUES (?, ?, ?, ?, ?, ?, NULL, ?, ?, ?)",
                couponId, promotionId, "user-" + UUID.randomUUID(), status,
                Timestamp.from(now.minusSeconds(86_400)),
                usedAt == null ? null : Timestamp.from(usedAt),
                Timestamp.from(now.plusSeconds(30L * 86_400)),
                orderId, tenantId);
        return couponId;
    }

    @Test
    @DisplayName("테넌트를 가로질러 오래된 USED 만 used_at 순으로 — 최근 사용·ISSUED·주문 없는 USED 는 빠진다")
    void returnsOnlyOldUsedCouponsWithAnOrder_acrossTenants_oldestFirst() {
        String promotionId = activePromotionId();
        Instant now = Instant.now(clock);

        String oldInOtherTenant = seedCoupon(promotionId, "tenant-b", "USED", "order-b", now.minusSeconds(3 * 3600));
        String oldInDefaultTenant = seedCoupon(promotionId, "ecommerce", "USED", "order-a", now.minusSeconds(2 * 3600));
        String usedRecently = seedCoupon(promotionId, "ecommerce", "USED", "order-c", now.minusSeconds(10 * 60));
        String issued = seedCoupon(promotionId, "ecommerce", "ISSUED", null, null);
        String usedWithoutOrder = seedCoupon(promotionId, "ecommerce", "USED", null, now.minusSeconds(4 * 3600));

        List<StaleUsedCoupon> result = staleUsedCouponQueryService.find(60, 200);
        Set<String> seeded = Set.of(oldInOtherTenant, oldInDefaultTenant, usedRecently, issued, usedWithoutOrder);
        List<StaleUsedCoupon> ours = result.stream().filter(c -> seeded.contains(c.couponId())).toList();

        assertThat(ours).extracting(StaleUsedCoupon::couponId)
                .containsExactly(oldInOtherTenant, oldInDefaultTenant);
        assertThat(ours.get(0).tenantId()).isEqualTo("tenant-b");
        assertThat(ours.get(0).orderId()).isEqualTo("order-b");
        assertThat(ours.get(1).tenantId()).isEqualTo("ecommerce");
    }

    @Test
    @DisplayName("V10 인덱스가 실제로 생겼다")
    void migrationV10CreatedTheIndex() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'coupons' AND indexname = 'idx_coupons_status_used_at'",
                Integer.class);

        assertThat(count).isEqualTo(1);
    }
}
