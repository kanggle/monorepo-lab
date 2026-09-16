package com.example.promotion;

import com.example.promotion.application.command.ApplyCouponCommand;
import com.example.promotion.application.service.CouponCommandService;
import com.example.promotion.domain.coupon.Coupon;
import com.example.promotion.domain.coupon.CouponPlacementReleasedException;
import com.example.promotion.domain.coupon.CouponRepository;
import com.example.promotion.domain.coupon.CouponStatus;
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

import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-INT-027 AC-5 — the fence against a late {@code apply}, proved on a real Postgres.
 *
 * <p>This is the <b>authoritative</b> lane for the mechanism: the Flyway V9 table, its
 * {@code UNIQUE (coupon_id, order_id)} index and the JPA mapping only exist in a real database.
 * The unit tests pin the branch logic with mocked repositories; they cannot tell whether the
 * migration applied or the constraint is real.
 *
 * <p>Assertions are on persisted state — the coupon's own status re-read from the database, and
 * the row count in {@code coupon_release} — never on a mock interaction.
 */
@SpringBootTest(classes = PromotionServiceApplication.class,
        properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@Tag("integration")
@Testcontainers
@EmbeddedKafka(partitions = 1)
@DisplayName("쿠폰 해제 펜스 통합 테스트 (TASK-INT-027)")
class CouponReleaseFenceIntegrationTest {

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
    private CouponCommandService couponCommandService;
    @Autowired
    private PromotionRepository promotionRepository;
    @Autowired
    private CouponRepository couponRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Clock clock;

    private String activePromotionId() {
        Instant now = Instant.now(clock);
        Promotion promotion = Promotion.create(
                "펜스 테스트", "설명", DiscountType.FIXED, 5000, 0, 100,
                now.minusSeconds(86_400), now.plusSeconds(30L * 86_400), clock);
        promotionRepository.save(promotion);
        return promotion.getPromotionId();
    }

    /** An ISSUED coupon belonging to {@code userId}, persisted. */
    private String issuedCouponId(String promotionId, String userId) {
        Coupon coupon = Coupon.issue(promotionId, userId,
                Instant.now(clock).plusSeconds(30L * 86_400), clock);
        couponRepository.save(coupon);
        return coupon.getCouponId();
    }

    private long fenceRows(String couponId, String orderId) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM coupon_release WHERE coupon_id = ? AND order_id = ?",
                Long.class, couponId, orderId);
        return n == null ? 0L : n;
    }

    private CouponStatus persistedStatus(String couponId) {
        return couponRepository.findById(couponId).orElseThrow().getStatus();
    }

    @Test
    @DisplayName("release 가 먼저 도착하면 펜스가 남고, 늦게 온 apply 는 거절되며 쿠폰은 ISSUED 로 남는다")
    void releaseFirst_thenLateApply_isRefusedAndTheCouponStaysIssued() {
        String promotionId = activePromotionId();
        String couponId = issuedCouponId(promotionId, "user-" + System.nanoTime());
        String orderId = "order-" + System.nanoTime();

        // The placement did not commit; its release arrives while the apply is still in flight.
        couponCommandService.releaseCoupon(couponId, orderId);
        assertThat(fenceRows(couponId, orderId)).isEqualTo(1);

        // The late apply now tries to commit against an order that was never saved.
        assertThatThrownBy(() -> couponCommandService.applyCoupon(
                new ApplyCouponCommand(couponId, "ignored-user", orderId, 30000)))
                .isInstanceOf(CouponPlacementReleasedException.class);

        assertThat(persistedStatus(couponId)).isEqualTo(CouponStatus.ISSUED);
        assertThat(couponRepository.findById(couponId).orElseThrow().getOrderId()).isNull();
    }

    @Test
    @DisplayName("되돌릴 것이 없는 release 가 재시도로 두 번 와도 행은 하나다 — UNIQUE (coupon_id, order_id)")
    void aRetriedReleaseLeavesExactlyOneRow() {
        String promotionId = activePromotionId();
        String couponId = issuedCouponId(promotionId, "user-" + System.nanoTime());
        String orderId = "order-" + System.nanoTime();

        couponCommandService.releaseCoupon(couponId, orderId);
        assertThatCode(() -> couponCommandService.releaseCoupon(couponId, orderId))
                .doesNotThrowAnyException();

        assertThat(fenceRows(couponId, orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("펜스는 쌍 단위다 — 같은 쿠폰을 다른 주문으로 쓰는 것은 막지 않는다")
    void theFenceIsScopedToThePair() {
        String promotionId = activePromotionId();
        String userId = "user-" + System.nanoTime();
        String couponId = issuedCouponId(promotionId, userId);
        String deadOrder = "order-dead-" + System.nanoTime();
        String liveOrder = "order-live-" + System.nanoTime();

        couponCommandService.releaseCoupon(couponId, deadOrder);

        couponCommandService.applyCoupon(new ApplyCouponCommand(couponId, userId, liveOrder, 30000));

        assertThat(persistedStatus(couponId)).isEqualTo(CouponStatus.USED);
        assertThat(couponRepository.findById(couponId).orElseThrow().getOrderId()).isEqualTo(liveOrder);
        assertThat(fenceRows(couponId, liveOrder)).isZero();
    }

    @Test
    @DisplayName("apply 가 먼저 커밋됐으면 release 는 되돌리기만 하고 펜스를 남기지 않는다")
    void releaseAfterApply_revertsTheCouponAndWritesNoFence() {
        String promotionId = activePromotionId();
        String userId = "user-" + System.nanoTime();
        String couponId = issuedCouponId(promotionId, userId);
        String orderId = "order-" + System.nanoTime();

        couponCommandService.applyCoupon(new ApplyCouponCommand(couponId, userId, orderId, 30000));
        assertThat(persistedStatus(couponId)).isEqualTo(CouponStatus.USED);

        couponCommandService.releaseCoupon(couponId, orderId);

        assertThat(persistedStatus(couponId)).isEqualTo(CouponStatus.ISSUED);
        assertThat(fenceRows(couponId, orderId)).isZero();
    }
}
