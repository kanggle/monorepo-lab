package com.example.promotion;

import com.example.promotion.application.command.IssueCouponsCommand;
import com.example.promotion.application.exception.IdempotencyKeyConflictException;
import com.example.promotion.application.exception.IdempotencyKeyRequiredException;
import com.example.promotion.application.result.IssueCouponsResult;
import com.example.promotion.application.service.CouponCommandService;
import com.example.promotion.domain.coupon.CouponRepository;
import com.example.promotion.domain.promotion.DiscountType;
import com.example.promotion.domain.promotion.Promotion;
import com.example.promotion.domain.promotion.PromotionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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
 * TASK-BE-536 ④ — a duplicated {@code POST /api/promotions/{promotionId}/coupons/issue}
 * must not mint a second batch of coupons.
 *
 * <p>This is the <b>authoritative</b> lane: the mechanism is the Flyway V8 table
 * plus its {@code UNIQUE (promotion_id, idempotency_key)} index, which only exists
 * in a real Postgres, and the property under test (issued coupon COUNT) is a
 * persisted-state property. Unit tests (mocked repositories) pin the branch logic;
 * this proves the migration applied and the constraint is real.
 *
 * <p>Every assertion is on the persisted issued-coupon <b>count</b>, never on the
 * existence of a dedupe row alone.
 */
@SpringBootTest(classes = PromotionServiceApplication.class,
        properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@Tag("integration")
@Testcontainers
@EmbeddedKafka(partitions = 1)
@DisplayName("쿠폰 발급 멱등성 통합 테스트 (TASK-BE-536)")
class CouponIssueIdempotencyIntegrationTest {

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

    private String activePromotionId(int maxIssuanceCount) {
        Instant now = Instant.now(clock);
        Promotion promotion = Promotion.create(
                "발급 멱등성 테스트", "설명", DiscountType.FIXED, 1000, 0, maxIssuanceCount,
                now.minusSeconds(86_400), now.plusSeconds(30L * 86_400), clock);
        promotionRepository.save(promotion);
        return promotion.getPromotionId();
    }

    private long issuedCouponCountFor(String promotionId) {
        return promotionRepository.findById(promotionId).orElseThrow().getIssuedCount();
    }

    /** AC-1 — the same key replayed does NOT mint a second batch. */
    @Test
    @DisplayName("AC-1 같은 키로 쿠폰 발급 2회 → 발급 수가 두 번 증가하지 않는다")
    void sameKeyReplay_doesNotIssueTwice() {
        String promotionId = activePromotionId(100);
        String userId = "user-" + System.nanoTime();

        couponCommandService.issueCoupons(
                new IssueCouponsCommand(promotionId, java.util.List.of(userId), "ECOMMERCE_OPERATOR", "key-A"));
        IssueCouponsResult replay = couponCommandService.issueCoupons(
                new IssueCouponsCommand(promotionId, java.util.List.of(userId), "ECOMMERCE_OPERATOR", "key-A"));

        assertThat(replay.issuedCount()).isEqualTo(1);
        assertThat(issuedCouponCountFor(promotionId)).isEqualTo(1);
    }

    /** AC-2 — the regression guard (F1). A genuine second batch uses a different key. */
    @Test
    @DisplayName("AC-2 다른 키로 두 번째 발급 배치 → 성공하고 누적된다")
    void differentKey_isGenuineSecondBatch_andAccumulates() {
        String promotionId = activePromotionId(100);

        couponCommandService.issueCoupons(new IssueCouponsCommand(
                promotionId, java.util.List.of("user-a"), "ECOMMERCE_OPERATOR", "key-A"));
        couponCommandService.issueCoupons(new IssueCouponsCommand(
                promotionId, java.util.List.of("user-b"), "ECOMMERCE_OPERATOR", "key-B"));

        assertThat(issuedCouponCountFor(promotionId)).isEqualTo(2);
    }

    /** Same key, different user batch → 409-mapped exception; the first issuance stands unchanged. */
    @Test
    @DisplayName("같은 키 + 다른 사용자 배치 → IdempotencyKeyConflictException, 발급수 불변")
    void sameKeyDifferentUserBatch_isRejected() {
        String promotionId = activePromotionId(100);
        couponCommandService.issueCoupons(new IssueCouponsCommand(
                promotionId, java.util.List.of("user-a"), "ECOMMERCE_OPERATOR", "key-A"));

        assertThatThrownBy(() -> couponCommandService.issueCoupons(new IssueCouponsCommand(
                promotionId, java.util.List.of("user-c", "user-d"), "ECOMMERCE_OPERATOR", "key-A")))
                .isInstanceOf(IdempotencyKeyConflictException.class);

        assertThat(issuedCouponCountFor(promotionId)).isEqualTo(1);
    }

    /** The key is scoped to the promotion — the same key value against another promotion works. */
    @Test
    @DisplayName("키는 promotion 단위 스코프 — 다른 promotion 에 같은 키를 써도 정상 처리된다")
    void keyIsScopedToPromotion() {
        String first = activePromotionId(100);
        String second = activePromotionId(100);

        couponCommandService.issueCoupons(new IssueCouponsCommand(
                first, java.util.List.of("user-a"), "ECOMMERCE_OPERATOR", "shared-key"));
        assertThatCode(() -> couponCommandService.issueCoupons(new IssueCouponsCommand(
                second, java.util.List.of("user-a"), "ECOMMERCE_OPERATOR", "shared-key")))
                .doesNotThrowAnyException();

        assertThat(issuedCouponCountFor(first)).isEqualTo(1);
        assertThat(issuedCouponCountFor(second)).isEqualTo(1);
    }

    /** Refuses a keyless request outright — no coupon is minted. */
    @Test
    @DisplayName("키 없는 쿠폰 발급 요청은 거부되고 발급이 일어나지 않는다")
    void missingKey_isRefused_noCouponIssued() {
        String promotionId = activePromotionId(100);

        assertThatThrownBy(() -> couponCommandService.issueCoupons(new IssueCouponsCommand(
                promotionId, java.util.List.of("user-a"), "ECOMMERCE_OPERATOR", null)))
                .isInstanceOf(IdempotencyKeyRequiredException.class);

        assertThat(issuedCouponCountFor(promotionId)).isZero();
    }

    /**
     * Edge Case — a dedup guard must compose with the issuance cap: a replay must
     * issue NOTHING, not "issue up to the cap".
     */
    @Test
    @DisplayName("Edge Case 한도 소진 후 같은 키 재생 → 캡까지 발급이 아니라 아무것도 발급하지 않는다")
    void replayAfterCapExhausted_composesWithCap_issuesNothing() {
        String promotionId = activePromotionId(1);
        IssueCouponsResult first = couponCommandService.issueCoupons(new IssueCouponsCommand(
                promotionId, java.util.List.of("user-a"), "ECOMMERCE_OPERATOR", "key-cap"));
        assertThat(first.issuedCount()).isEqualTo(1);

        IssueCouponsResult replay = couponCommandService.issueCoupons(new IssueCouponsCommand(
                promotionId, java.util.List.of("user-a"), "ECOMMERCE_OPERATOR", "key-cap"));

        assertThat(replay.issuedCount()).isEqualTo(1);
        assertThat(issuedCouponCountFor(promotionId)).isEqualTo(1);
    }

    /**
     * AC-4 backstop is a real DB constraint, not a read-then-write check. Asserted
     * directly against the schema.
     */
    @Test
    @DisplayName("AC-4 (promotion_id, idempotency_key) 유니크 제약이 실제 스키마에 존재한다")
    void uniqueConstraintExistsInSchema() {
        String promotionId = activePromotionId(100);
        couponCommandService.issueCoupons(new IssueCouponsCommand(
                promotionId, java.util.List.of("user-a"), "ECOMMERCE_OPERATOR", "key-A"));

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO coupon_issue_request (promotion_id, idempotency_key, user_ids_digest, issued_count) "
                        + "VALUES (?, ?, ?, ?)", promotionId, "key-A", "deadbeef", 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
