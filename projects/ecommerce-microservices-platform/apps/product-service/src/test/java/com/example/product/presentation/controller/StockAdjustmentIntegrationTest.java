package com.example.product.presentation.controller;

import com.example.product.ProductServiceApplication;
import com.example.product.application.command.AdjustStockCommand;
import com.example.product.application.command.RegisterProductCommand;
import com.example.product.application.command.VariantCommand;
import com.example.product.application.dto.AdjustStockResult;
import com.example.product.application.service.AdjustStockService;
import com.example.product.application.service.QueryProductService;
import com.example.product.application.service.RegisterProductService;
import com.example.product.domain.event.ProductEvent;
import com.example.product.domain.event.StockChangedPayload;
import com.example.product.domain.exception.InsufficientStockException;
import com.example.product.domain.exception.ProductNotFoundException;
import com.example.product.domain.exception.VariantNotFoundException;
import com.example.product.domain.model.ProductStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@SpringBootTest(classes = ProductServiceApplication.class)
@Tag("integration")
@Testcontainers
@Transactional
@DisplayName("재고 조정 통합 테스트")
class StockAdjustmentIntegrationTest {

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
    @DisplayName("재고 증가 후 DB에 반영된다")
    void adjustStock_increase_reflectedInDb() {
        RegisterProductCommand command = new RegisterProductCommand(
                "재고 증가 상품", "설명", 10000L, null, null, null,
                List.of(new VariantCommand("기본", 10, 0)), "reg-key-1");
        UUID productId = registerProductService.register(command);
        UUID variantId = queryProductService.findById(productId).variants().get(0).id();

        AdjustStockResult result = adjustStockService.adjust(
                new AdjustStockCommand(productId, variantId, 5, "RESTOCK", "adj-key-1"));

        assertThat(result.currentStock()).isEqualTo(15);
        int dbStock = queryProductService.findById(productId).variants().get(0).stock();
        assertThat(dbStock).isEqualTo(15);
    }

    @Test
    @DisplayName("재고 감소 후 DB에 반영된다")
    void adjustStock_decrease_reflectedInDb() {
        RegisterProductCommand command = new RegisterProductCommand(
                "재고 감소 상품", "설명", 10000L, null, null, null,
                List.of(new VariantCommand("기본", 10, 0)), "reg-key-2");
        UUID productId = registerProductService.register(command);
        UUID variantId = queryProductService.findById(productId).variants().get(0).id();

        AdjustStockResult result = adjustStockService.adjust(
                new AdjustStockCommand(productId, variantId, -3, "ADMIN_ADJUSTMENT", "adj-key-2"));

        assertThat(result.currentStock()).isEqualTo(7);
        int dbStock = queryProductService.findById(productId).variants().get(0).stock();
        assertThat(dbStock).isEqualTo(7);
    }

    @Test
    @DisplayName("재고 조정 성공 시 StockChanged 이벤트가 발행된다")
    void adjustStock_success_publishesStockChangedEvent() {
        RegisterProductCommand command = new RegisterProductCommand(
                "이벤트 재고 상품", "설명", 10000L, null, null, null,
                List.of(new VariantCommand("기본", 10, 0)), "reg-key-3");
        UUID productId = registerProductService.register(command);
        UUID variantId = queryProductService.findById(productId).variants().get(0).id();

        adjustStockService.adjust(new AdjustStockCommand(productId, variantId, 5, "RESTOCK", "adj-key-3"));

        // StockChanged is published via Kafka (mocked); events accumulate on the
        // context-shared mock across methods, so filter to this product's change.
        org.mockito.ArgumentCaptor<Object> captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate, atLeastOnce())
                .send(eq("product.product.stock-changed"), anyString(), captor.capture());
        StockChangedPayload payload = captor.getAllValues().stream()
                .map(ProductEvent.class::cast)
                .map(e -> (StockChangedPayload) e.payload())
                .filter(p -> p.productId().equals(productId.toString()))
                .reduce((first, second) -> second)
                .orElseThrow();
        assertThat(payload.productId()).isEqualTo(productId.toString());
        assertThat(payload.variantId()).isEqualTo(variantId.toString());
        assertThat(payload.previousStock()).isEqualTo(10);
        assertThat(payload.currentStock()).isEqualTo(15);
        assertThat(payload.delta()).isEqualTo(5);
        assertThat(payload.reason()).isEqualTo("RESTOCK");
    }

    @Test
    @DisplayName("재고가 0이 되면 상품 status가 SOLD_OUT으로 변경된다")
    void adjustStock_toZero_productBecomeSoldOut() {
        RegisterProductCommand command = new RegisterProductCommand(
                "품절 상품", "설명", 10000L, null, null, null,
                List.of(new VariantCommand("기본", 5, 0)), "reg-key-4");
        UUID productId = registerProductService.register(command);
        UUID variantId = queryProductService.findById(productId).variants().get(0).id();

        adjustStockService.adjust(new AdjustStockCommand(productId, variantId, -5, "ADMIN_ADJUSTMENT", "adj-key-4"));

        assertThat(queryProductService.findById(productId).status()).isEqualTo(ProductStatus.SOLD_OUT);
    }

    @Test
    @DisplayName("RESTOCK으로 stock이 0 초과가 되면 SOLD_OUT 상품이 ON_SALE로 변경된다")
    void adjustStock_restockFromSoldOut_productBecomesOnSale() {
        RegisterProductCommand command = new RegisterProductCommand(
                "재입고 상품", "설명", 10000L, null, null, null,
                List.of(new VariantCommand("기본", 5, 0)), "reg-key-5");
        UUID productId = registerProductService.register(command);
        UUID variantId = queryProductService.findById(productId).variants().get(0).id();

        adjustStockService.adjust(new AdjustStockCommand(productId, variantId, -5, "ADMIN_ADJUSTMENT", "adj-key-5a"));
        assertThat(queryProductService.findById(productId).status()).isEqualTo(ProductStatus.SOLD_OUT);

        adjustStockService.adjust(new AdjustStockCommand(productId, variantId, 10, "RESTOCK", "adj-key-5b"));
        assertThat(queryProductService.findById(productId).status()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("재고보다 많이 감소 시 InsufficientStockException 발생")
    void adjustStock_exceedsStock_throws() {
        RegisterProductCommand command = new RegisterProductCommand(
                "부족 재고 상품", "설명", 10000L, null, null, null,
                List.of(new VariantCommand("기본", 3, 0)), "reg-key-6");
        UUID productId = registerProductService.register(command);
        UUID variantId = queryProductService.findById(productId).variants().get(0).id();

        assertThatThrownBy(() ->
                adjustStockService.adjust(new AdjustStockCommand(productId, variantId, -5, "ADMIN_ADJUSTMENT", "adj-key-6")))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    @DisplayName("존재하지 않는 productId 요청 시 ProductNotFoundException 발생")
    void adjustStock_invalidProductId_throws() {
        UUID fakeProductId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();

        assertThatThrownBy(() ->
                adjustStockService.adjust(new AdjustStockCommand(fakeProductId, variantId, 5, "RESTOCK", "adj-key-7")))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    @DisplayName("존재하지 않는 variantId 요청 시 VariantNotFoundException 발생")
    void adjustStock_invalidVariantId_throws() {
        RegisterProductCommand command = new RegisterProductCommand(
                "상품", "설명", 10000L, null, null, null,
                List.of(new VariantCommand("기본", 10, 0)), "reg-key-7");
        UUID productId = registerProductService.register(command);
        UUID fakeVariantId = UUID.randomUUID();

        assertThatThrownBy(() ->
                adjustStockService.adjust(new AdjustStockCommand(productId, fakeVariantId, 5, "RESTOCK", "adj-key-8")))
                .isInstanceOf(VariantNotFoundException.class);
    }
}
