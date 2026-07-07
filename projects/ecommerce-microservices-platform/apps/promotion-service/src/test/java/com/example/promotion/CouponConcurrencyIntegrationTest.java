package com.example.promotion;

import com.example.promotion.application.command.ApplyCouponCommand;
import com.example.promotion.application.command.IssueCouponsCommand;
import com.example.promotion.application.service.CouponCommandService;
import com.example.promotion.domain.coupon.Coupon;
import com.example.promotion.domain.coupon.CouponRepository;
import com.example.promotion.domain.coupon.CouponStatus;
import com.example.promotion.domain.promotion.DiscountType;
import com.example.promotion.domain.promotion.Promotion;
import com.example.promotion.domain.promotion.PromotionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PromotionServiceApplication.class, properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@Tag("integration")
@Testcontainers
@EmbeddedKafka(partitions = 1)
@DisplayName("쿠폰 동시성 통합 테스트")
class CouponConcurrencyIntegrationTest {

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

    @Test
    @DisplayName("동시에 쿠폰 발급 요청 시 수량 제한이 정확하게 동작한다")
    void concurrentIssuance_limitsCorrectly() throws Exception {
        // Window must contain the runtime clock (CouponCommandService validates
        // isActive against its injected system Clock), so it is relative to now —
        // a fixed calendar window would make every issuance throw
        // PromotionNotActiveException once that window is in the past (TASK-MONO-319:
        // the bare-@SpringBootTest config ambiguity previously masked this by aborting
        // the test before its body ran).
        Clock clock = Clock.systemUTC();
        Instant now = Instant.now(clock);

        Promotion promotion = Promotion.create(
                "동시성 테스트", "동시 발급 테스트",
                DiscountType.FIXED, 1000, 0, 5,
                now.minusSeconds(86_400),
                now.plusSeconds(30L * 86_400), clock
        );
        promotionRepository.save(promotion);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            String userId = "concurrent-user-" + i;
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    couponCommandService.issueCoupons(
                            new IssueCouponsCommand(promotion.getPromotionId(), List.of(userId), "ECOMMERCE_OPERATOR")
                    );
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                }
            }));
        }

        latch.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();

        // Max issuance count is 5, so only 5 should succeed
        assertThat(successCount.get()).isLessThanOrEqualTo(5);
        assertThat(successCount.get() + failCount.get()).isEqualTo(threadCount);

        Promotion updated = promotionRepository.findById(promotion.getPromotionId()).orElseThrow();
        assertThat(updated.getIssuedCount()).isLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("동시에 같은 쿠폰을 적용해도 한 번만 성공한다")
    void concurrentApply_onlyOneSucceeds() throws Exception {
        // Now-relative window — see concurrentIssuance_limitsCorrectly for why.
        Clock clock = Clock.systemUTC();
        Instant now = Instant.now(clock);

        Promotion promotion = Promotion.create(
                "적용 동시성 테스트", "동시 적용 테스트",
                DiscountType.FIXED, 1000, 0, 100,
                now.minusSeconds(86_400),
                now.plusSeconds(30L * 86_400), clock
        );
        promotionRepository.save(promotion);

        String userId = "apply-user-" + System.nanoTime();

        // Issue one coupon
        couponCommandService.issueCoupons(
                new IssueCouponsCommand(promotion.getPromotionId(), List.of(userId), "ECOMMERCE_OPERATOR")
        );

        // Find the coupon
        var coupons = couponRepository.findByUserId(userId, 0, 10);
        String couponId = coupons.content().get(0).getCouponId();

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            int idx = i;
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    couponCommandService.applyCoupon(
                            new ApplyCouponCommand(couponId, userId, "order-" + idx, 10000)
                    );
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                }
            }));
        }

        latch.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();

        // Only one should succeed
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(threadCount - 1);

        Coupon used = couponRepository.findById(couponId).orElseThrow();
        assertThat(used.getStatus()).isEqualTo(CouponStatus.USED);
    }
}
