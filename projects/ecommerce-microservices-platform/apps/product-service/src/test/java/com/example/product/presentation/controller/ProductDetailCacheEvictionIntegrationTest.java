package com.example.product.presentation.controller;

import com.example.product.ProductServiceApplication;
import com.example.product.application.command.AdjustStockCommand;
import com.example.product.application.command.RegisterProductCommand;
import com.example.product.application.command.UpdateProductCommand;
import com.example.product.application.command.VariantCommand;
import com.example.product.application.dto.ProductDetail;
import com.example.product.application.service.AdjustStockService;
import com.example.product.application.service.DeleteProductService;
import com.example.product.application.service.QueryProductService;
import com.example.product.application.service.RegisterProductService;
import com.example.product.application.service.UpdateProductService;
import com.example.product.application.service.VariantManagementService;
import com.example.product.domain.exception.ProductNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression for TASK-BE-436 — the {@code @Cacheable("product-detail")} read key
 * carries a seller-scope segment (tenant:scope:productId), but the targeted
 * {@code @CacheEvict} keys on the write paths omitted it (tenant:productId), so a
 * stock adjust / update / variant change / delete never evicted the cached
 * detail. The detail then served stale data for the full TTL (60s) — the user
 * symptom "재고를 늘려도 상세에 반영되지 않음".
 *
 * Unlike the other product-service ITs, this test FORCES the cache on
 * ({@code spring.cache.type=simple} → ConcurrentMapCacheManager); the default
 * test run leaves the cache a no-op, which would silently hide the bug (a write
 * path that fails to evict still "passes" because nothing was cached). With the
 * cache active, each test populates the detail cache, performs a write, and
 * asserts the very next read reflects it — i.e. that the eviction actually fired.
 * Against the pre-fix (key-mismatch) code these assertions fail with stale reads.
 */
@SpringBootTest(classes = ProductServiceApplication.class)
@Tag("integration")
@Testcontainers
@DisplayName("상품 상세 캐시 무효화 통합 테스트 (TASK-BE-436)")
class ProductDetailCacheEvictionIntegrationTest {

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
        // Force an in-memory cache so eviction is actually exercised (the default
        // leaves the cache effectively no-op and would mask the key mismatch).
        registry.add("spring.cache.type", () -> "simple");
    }

    @MockitoBean
    @SuppressWarnings("unused")
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private RegisterProductService registerProductService;

    @Autowired
    private AdjustStockService adjustStockService;

    @Autowired
    private UpdateProductService updateProductService;

    @Autowired
    private VariantManagementService variantManagementService;

    @Autowired
    private DeleteProductService deleteProductService;

    @Autowired
    private QueryProductService queryProductService;

    private UUID registerProduct(String name, int stock) {
        // A fresh random Idempotency-Key per call — each call in this file registers
        // a genuinely distinct product, so no replay semantics are exercised here
        // (TASK-BE-536; the idempotency guard itself is covered elsewhere).
        return registerProductService.register(new RegisterProductCommand(
                name, "설명", 10000L, null, null, null,
                List.of(new VariantCommand("기본", stock, 0)), UUID.randomUUID().toString()));
    }

    @Test
    @DisplayName("재고 증가 후 캐시가 무효화돼 상세 조회가 새 재고를 즉시 반영한다")
    void stockAdjust_evictsDetailCache() {
        UUID productId = registerProduct("재고 캐시 상품", 10);
        UUID variantId = queryProductService.findById(productId).variants().get(0).id(); // populate cache (stock 10)

        adjustStockService.adjust(
                new AdjustStockCommand(productId, variantId, 5, "RESTOCK", UUID.randomUUID().toString()));

        // Pre-fix: stale cache returns 10; post-fix: 15.
        int stockAfter = queryProductService.findById(productId).variants().get(0).stock();
        assertThat(stockAfter).isEqualTo(15);
    }

    @Test
    @DisplayName("상품 수정 후 캐시가 무효화돼 상세 조회가 새 이름/가격을 즉시 반영한다")
    void update_evictsDetailCache() {
        UUID productId = registerProduct("수정 전 이름", 10);
        ProductDetail before = queryProductService.findById(productId); // populate cache
        assertThat(before.name()).isEqualTo("수정 전 이름");

        updateProductService.update(new UpdateProductCommand(
                productId, "수정 후 이름", null, 20000L, null));

        ProductDetail after = queryProductService.findById(productId);
        assertThat(after.name()).isEqualTo("수정 후 이름");
        assertThat(after.price()).isEqualTo(20000L);
    }

    @Test
    @DisplayName("변형 추가 후 캐시가 무효화돼 상세 조회가 새 변형을 즉시 반영한다")
    void addVariant_evictsDetailCache() {
        UUID productId = registerProduct("변형 캐시 상품", 10);
        assertThat(queryProductService.findById(productId).variants()).hasSize(1); // populate cache

        variantManagementService.addVariant(productId, "라지", 7, 500);

        assertThat(queryProductService.findById(productId).variants()).hasSize(2);
    }

    @Test
    @DisplayName("삭제 후 캐시가 무효화돼 삭제된 상품 상세가 잔존 서빙되지 않는다")
    void delete_evictsDetailCache() {
        UUID productId = registerProduct("삭제 캐시 상품", 10);
        queryProductService.findById(productId); // populate cache

        deleteProductService.delete(productId);

        // Pre-fix: stale cache still serves the deleted product's detail.
        assertThatThrownBy(() -> queryProductService.findById(productId))
                .isInstanceOf(ProductNotFoundException.class);
    }
}
