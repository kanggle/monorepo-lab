package com.example.product.presentation.controller;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@Transactional
@RecordApplicationEvents
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

    @Autowired
    private ApplicationEvents applicationEvents;

    @Test
    @DisplayName("재고 증가 후 DB에 반영된다")
    void adjustStock_increase_reflectedInDb() {
        RegisterProductCommand command = new RegisterProductCommand(
                "재고 증가 상품", "설명", 10000L, null,
                List.of(new VariantCommand("기본", 10, 0)));
        UUID productId = registerProductService.register(command);
        UUID variantId = queryProductService.findById(productId).variants().get(0).id();

        AdjustStockResult result = adjustStockService.adjust(
                new AdjustStockCommand(productId, variantId, 5, "RESTOCK"));

        assertThat(result.currentStock()).isEqualTo(15);
        int dbStock = queryProductService.findById(productId).variants().get(0).stock();
        assertThat(dbStock).isEqualTo(15);
    }

    @Test
    @DisplayName("재고 감소 후 DB에 반영된다")
    void adjustStock_decrease_reflectedInDb() {
        RegisterProductCommand command = new RegisterProductCommand(
                "재고 감소 상품", "설명", 10000L, null,
                List.of(new VariantCommand("기본", 10, 0)));
        UUID productId = registerProductService.register(command);
        UUID variantId = queryProductService.findById(productId).variants().get(0).id();

        AdjustStockResult result = adjustStockService.adjust(
                new AdjustStockCommand(productId, variantId, -3, "ADMIN_ADJUSTMENT"));

        assertThat(result.currentStock()).isEqualTo(7);
        int dbStock = queryProductService.findById(productId).variants().get(0).stock();
        assertThat(dbStock).isEqualTo(7);
    }

    @Test
    @DisplayName("재고 조정 성공 시 StockChanged 이벤트가 발행된다")
    void adjustStock_success_publishesStockChangedEvent() {
        RegisterProductCommand command = new RegisterProductCommand(
                "이벤트 재고 상품", "설명", 10000L, null,
                List.of(new VariantCommand("기본", 10, 0)));
        UUID productId = registerProductService.register(command);
        UUID variantId = queryProductService.findById(productId).variants().get(0).id();
        applicationEvents.stream(ProductEvent.class).close(); // 등록 이벤트 무시

        adjustStockService.adjust(new AdjustStockCommand(productId, variantId, 5, "RESTOCK"));

        List<ProductEvent> events = applicationEvents.stream(ProductEvent.class)
                .filter(e -> "StockChanged".equals(e.eventType()))
                .toList();
        assertThat(events).hasSize(1);

        StockChangedPayload payload = (StockChangedPayload) events.get(0).payload();
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
                "품절 상품", "설명", 10000L, null,
                List.of(new VariantCommand("기본", 5, 0)));
        UUID productId = registerProductService.register(command);
        UUID variantId = queryProductService.findById(productId).variants().get(0).id();

        adjustStockService.adjust(new AdjustStockCommand(productId, variantId, -5, "ADMIN_ADJUSTMENT"));

        assertThat(queryProductService.findById(productId).status()).isEqualTo(ProductStatus.SOLD_OUT);
    }

    @Test
    @DisplayName("RESTOCK으로 stock이 0 초과가 되면 SOLD_OUT 상품이 ON_SALE로 변경된다")
    void adjustStock_restockFromSoldOut_productBecomesOnSale() {
        RegisterProductCommand command = new RegisterProductCommand(
                "재입고 상품", "설명", 10000L, null,
                List.of(new VariantCommand("기본", 5, 0)));
        UUID productId = registerProductService.register(command);
        UUID variantId = queryProductService.findById(productId).variants().get(0).id();

        adjustStockService.adjust(new AdjustStockCommand(productId, variantId, -5, "ADMIN_ADJUSTMENT"));
        assertThat(queryProductService.findById(productId).status()).isEqualTo(ProductStatus.SOLD_OUT);

        adjustStockService.adjust(new AdjustStockCommand(productId, variantId, 10, "RESTOCK"));
        assertThat(queryProductService.findById(productId).status()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("재고보다 많이 감소 시 InsufficientStockException 발생")
    void adjustStock_exceedsStock_throws() {
        RegisterProductCommand command = new RegisterProductCommand(
                "부족 재고 상품", "설명", 10000L, null,
                List.of(new VariantCommand("기본", 3, 0)));
        UUID productId = registerProductService.register(command);
        UUID variantId = queryProductService.findById(productId).variants().get(0).id();

        assertThatThrownBy(() ->
                adjustStockService.adjust(new AdjustStockCommand(productId, variantId, -5, "ADMIN_ADJUSTMENT")))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    @DisplayName("존재하지 않는 productId 요청 시 ProductNotFoundException 발생")
    void adjustStock_invalidProductId_throws() {
        UUID fakeProductId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();

        assertThatThrownBy(() ->
                adjustStockService.adjust(new AdjustStockCommand(fakeProductId, variantId, 5, "RESTOCK")))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    @DisplayName("존재하지 않는 variantId 요청 시 VariantNotFoundException 발생")
    void adjustStock_invalidVariantId_throws() {
        RegisterProductCommand command = new RegisterProductCommand(
                "상품", "설명", 10000L, null,
                List.of(new VariantCommand("기본", 10, 0)));
        UUID productId = registerProductService.register(command);
        UUID fakeVariantId = UUID.randomUUID();

        assertThatThrownBy(() ->
                adjustStockService.adjust(new AdjustStockCommand(productId, fakeVariantId, 5, "RESTOCK")))
                .isInstanceOf(VariantNotFoundException.class);
    }
}
