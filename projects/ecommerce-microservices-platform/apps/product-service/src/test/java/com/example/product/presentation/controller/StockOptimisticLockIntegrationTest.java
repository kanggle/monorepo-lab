package com.example.product.presentation.controller;

import com.example.product.ProductServiceApplication;
import com.example.product.application.command.AdjustStockCommand;
import com.example.product.application.command.RegisterProductCommand;
import com.example.product.application.command.VariantCommand;
import com.example.product.application.service.AdjustStockService;
import com.example.product.application.service.QueryProductService;
import com.example.product.application.service.RegisterProductService;
import com.example.product.domain.event.ProductEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ProductServiceApplication.class)
@Tag("integration")
@Testcontainers
@DisplayName("재고 Optimistic Locking 통합 테스트")
class StockOptimisticLockIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("product_db")
            .withUsername("product_user")
            .withPassword("product_pass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:0");
        // No Redis container in this IT harness — disable the cache abstraction so the
        // @Cacheable/@CacheEvict stock path does not attempt a Redis connection
        // (TASK-MONO-319; mirrors the sibling ITs that set spring.cache.type).
        registry.add("spring.cache.type", () -> "none");
    }

    @MockitoBean
    @SuppressWarnings("unused")
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private RegisterProductService registerProductService;

    @Autowired
    private AdjustStockService adjustStockService;

    @Autowired
    private QueryProductService queryProductService;

    @Test
    @DisplayName("동시 재고 차감 요청 시 일부만 성공하고 나머지는 OptimisticLockingFailureException 발생")
    void concurrentStockDecrease_partialSuccess() throws InterruptedException {
        RegisterProductCommand command = new RegisterProductCommand(
                "동시성 테스트 상품", "설명", 10000L, null, null, null,
                List.of(new VariantCommand("기본", 100, 0)), "reg-lock-1");
        UUID productId = registerProductService.register(command);
        UUID variantId = queryProductService.findById(productId).variants().get(0).id();

        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            // Each thread represents a DISTINCT genuine concurrent adjustment (e.g. a
            // different order's reservation), so each carries its OWN Idempotency-Key
            // (TASK-BE-536 AC-2) — a shared key would make the dedup guard itself the
            // race arbiter instead of Inventory's @Version, defeating this test's
            // purpose (optimistic-lock behaviour under real concurrent writes).
            String idempotencyKey = "lock-key-" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    adjustStockService.adjust(
                            new AdjustStockCommand(productId, variantId, -1, "ORDER_RESERVED", idempotencyKey));
                    successCount.incrementAndGet();
                } catch (OptimisticLockingFailureException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    conflictCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        int finalStock = queryProductService.findById(productId).variants().get(0).stock();

        assertThat(successCount.get() + conflictCount.get()).isEqualTo(threadCount);
        assertThat(successCount.get()).isGreaterThanOrEqualTo(1);
        assertThat(finalStock).isEqualTo(100 - successCount.get());
    }

    @Test
    @DisplayName("동시 재고 증가와 감소 요청 시 데이터 정합성이 유지된다")
    void concurrentIncreaseAndDecrease_dataConsistency() throws InterruptedException {
        RegisterProductCommand command = new RegisterProductCommand(
                "동시성 증감 상품", "설명", 10000L, null, null, null,
                List.of(new VariantCommand("기본", 50, 0)), "reg-lock-2");
        UUID productId = registerProductService.register(command);
        UUID variantId = queryProductService.findById(productId).variants().get(0).id();

        int threadCount = 6;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger increaseSuccess = new AtomicInteger(0);
        AtomicInteger decreaseSuccess = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            int delta = (i % 2 == 0) ? 5 : -3;
            String reason = (delta > 0) ? "RESTOCK" : "ORDER_RESERVED";
            // Distinct key per thread — see the sibling test above (TASK-BE-536 AC-2).
            String idempotencyKey = "lock-key2-" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    adjustStockService.adjust(
                            new AdjustStockCommand(productId, variantId, delta, reason, idempotencyKey));
                    if (delta > 0) {
                        increaseSuccess.incrementAndGet();
                    } else {
                        decreaseSuccess.incrementAndGet();
                    }
                } catch (Exception ignored) {
                    // OptimisticLockingFailureException 또는 비즈니스 예외
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        int finalStock = queryProductService.findById(productId).variants().get(0).stock();
        int expectedStock = 50 + (increaseSuccess.get() * 5) - (decreaseSuccess.get() * 3);

        assertThat(finalStock).isEqualTo(expectedStock);
    }
}
