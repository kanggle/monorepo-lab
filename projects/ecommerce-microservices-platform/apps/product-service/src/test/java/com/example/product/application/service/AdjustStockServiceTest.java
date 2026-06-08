package com.example.product.application.service;

import com.example.product.application.command.AdjustStockCommand;
import com.example.product.application.dto.AdjustStockResult;
import com.example.product.domain.event.ProductEvent;
import com.example.product.domain.event.ProductEventPublisher;
import com.example.product.domain.event.StockChangedPayload;
import com.example.product.domain.exception.InsufficientStockException;
import com.example.product.domain.exception.ProductNotFoundException;
import com.example.product.domain.exception.VariantNotFoundException;
import com.example.product.domain.model.Inventory;
import com.example.product.domain.model.Price;
import com.example.product.domain.model.Product;
import com.example.product.domain.model.ProductStatus;
import com.example.product.domain.model.ProductVariant;
import com.example.product.domain.model.StockQuantity;
import com.example.product.domain.repository.InventoryRepository;
import com.example.product.domain.repository.ProductRepository;
import com.example.product.application.port.ProductMetricPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdjustStockService 단위 테스트")
class AdjustStockServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private ProductEventPublisher productEventPublisher;

    @Mock
    private ProductMetricPort productMetrics;

    private EventPublishingHelper eventPublishingHelper;
    private AdjustStockService adjustStockService;

    @BeforeEach
    void setUp() {
        eventPublishingHelper = new EventPublishingHelper(productEventPublisher);
        adjustStockService = new AdjustStockService(
                productRepository, inventoryRepository, eventPublishingHelper, productMetrics);
    }

    private Product makeProductWithVariant(UUID productId, UUID variantId, int stock, ProductStatus status) {
        ProductVariant variant = ProductVariant.reconstitute(variantId, productId, "기본", new StockQuantity(stock), new Price(0), null);
        return Product.reconstitute(productId, "테스트 상품", "설명", new Price(10000), status, null,
                java.time.Instant.now(), java.time.Instant.now(), List.of(variant));
    }

    @Test
    @DisplayName("재고 증가 성공 시 currentStock과 이벤트 반환")
    void adjust_increase_success() {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Product product = makeProductWithVariant(productId, variantId, 10, ProductStatus.ON_SALE);
        Inventory inventory = Inventory.create(variantId, new StockQuantity(10));

        given(productRepository.findById(productId)).willReturn(Optional.of(product));
        given(inventoryRepository.findByVariantId(variantId)).willReturn(Optional.of(inventory));
        given(inventoryRepository.save(any())).willReturn(inventory);

        AdjustStockResult result = adjustStockService.adjust(
                new AdjustStockCommand(productId, variantId, 5, "RESTOCK"));

        assertThat(result.variantId()).isEqualTo(variantId);
        assertThat(result.currentStock()).isEqualTo(15);
    }

    @Test
    @DisplayName("재고 감소 성공 시 currentStock 반환")
    void adjust_decrease_success() {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Product product = makeProductWithVariant(productId, variantId, 10, ProductStatus.ON_SALE);
        Inventory inventory = Inventory.create(variantId, new StockQuantity(10));

        given(productRepository.findById(productId)).willReturn(Optional.of(product));
        given(inventoryRepository.findByVariantId(variantId)).willReturn(Optional.of(inventory));
        given(inventoryRepository.save(any())).willReturn(inventory);

        AdjustStockResult result = adjustStockService.adjust(
                new AdjustStockCommand(productId, variantId, -3, "ADMIN_ADJUSTMENT"));

        assertThat(result.currentStock()).isEqualTo(7);
    }

    @Test
    @DisplayName("재고 조정 후 stock이 0이 되면 상품 status가 SOLD_OUT으로 변경된다")
    void adjust_stockToZero_productBecomeSoldOut() {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Product product = makeProductWithVariant(productId, variantId, 5, ProductStatus.ON_SALE);
        Inventory inventory = Inventory.create(variantId, new StockQuantity(5));

        given(productRepository.findById(productId)).willReturn(Optional.of(product));
        given(inventoryRepository.findByVariantId(variantId)).willReturn(Optional.of(inventory));
        given(inventoryRepository.save(any())).willReturn(inventory);
        given(productRepository.save(any())).willReturn(product);

        adjustStockService.adjust(new AdjustStockCommand(productId, variantId, -5, "ADMIN_ADJUSTMENT"));

        assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
        verify(productRepository).save(product);
    }

    @Test
    @DisplayName("RESTOCK으로 stock이 0 초과가 되면 SOLD_OUT 상품이 ON_SALE로 변경된다")
    void adjust_restockFromSoldOut_productBecomesOnSale() {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Product product = makeProductWithVariant(productId, variantId, 0, ProductStatus.SOLD_OUT);
        Inventory inventory = Inventory.create(variantId, new StockQuantity(0));

        given(productRepository.findById(productId)).willReturn(Optional.of(product));
        given(inventoryRepository.findByVariantId(variantId)).willReturn(Optional.of(inventory));
        given(inventoryRepository.save(any())).willReturn(inventory);
        given(productRepository.save(any())).willReturn(product);

        adjustStockService.adjust(new AdjustStockCommand(productId, variantId, 10, "RESTOCK"));

        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
        verify(productRepository).save(product);
    }

    @Test
    @DisplayName("재고 조정 결과가 음수가 되면 InsufficientStockException 발생")
    void adjust_resultNegative_throws() {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Product product = makeProductWithVariant(productId, variantId, 3, ProductStatus.ON_SALE);
        Inventory inventory = Inventory.create(variantId, new StockQuantity(3));

        given(productRepository.findById(productId)).willReturn(Optional.of(product));
        given(inventoryRepository.findByVariantId(variantId)).willReturn(Optional.of(inventory));

        assertThatThrownBy(() ->
                adjustStockService.adjust(new AdjustStockCommand(productId, variantId, -5, "ADMIN_ADJUSTMENT")))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    @DisplayName("quantity가 0이면 IllegalArgumentException 발생")
    void adjust_quantityZero_throws() {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();

        assertThatThrownBy(() ->
                adjustStockService.adjust(new AdjustStockCommand(productId, variantId, 0, "RESTOCK")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be zero");

        verify(productRepository, never()).findById(any());
    }

    @Test
    @DisplayName("존재하지 않는 productId 요청 시 ProductNotFoundException 발생")
    void adjust_productNotFound_throws() {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();

        given(productRepository.findById(productId)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                adjustStockService.adjust(new AdjustStockCommand(productId, variantId, 5, "RESTOCK")))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    @DisplayName("variantId가 해당 상품에 속하지 않으면 VariantNotFoundException 발생")
    void adjust_variantNotBelongsToProduct_throws() {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID otherVariantId = UUID.randomUUID();
        Product product = makeProductWithVariant(productId, otherVariantId, 10, ProductStatus.ON_SALE);

        given(productRepository.findById(productId)).willReturn(Optional.of(product));

        assertThatThrownBy(() ->
                adjustStockService.adjust(new AdjustStockCommand(productId, variantId, 5, "RESTOCK")))
                .isInstanceOf(VariantNotFoundException.class);
    }

    @Test
    @DisplayName("재고 조정 성공 시 StockChanged 이벤트가 발행된다")
    void adjust_success_publishesStockChangedEvent() {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Product product = makeProductWithVariant(productId, variantId, 10, ProductStatus.ON_SALE);
        Inventory inventory = Inventory.create(variantId, new StockQuantity(10));

        given(productRepository.findById(productId)).willReturn(Optional.of(product));
        given(inventoryRepository.findByVariantId(variantId)).willReturn(Optional.of(inventory));
        given(inventoryRepository.save(any())).willReturn(inventory);

        adjustStockService.adjust(new AdjustStockCommand(productId, variantId, 5, "RESTOCK"));

        ArgumentCaptor<ProductEvent> captor = ArgumentCaptor.forClass(ProductEvent.class);
        verify(productEventPublisher).publish(captor.capture());

        ProductEvent event = captor.getValue();
        assertThat(event.eventType()).isEqualTo("StockChanged");
        StockChangedPayload payload = (StockChangedPayload) event.payload();
        assertThat(payload.productId()).isEqualTo(productId.toString());
        assertThat(payload.variantId()).isEqualTo(variantId.toString());
        assertThat(payload.previousStock()).isEqualTo(10);
        assertThat(payload.currentStock()).isEqualTo(15);
        assertThat(payload.delta()).isEqualTo(5);
        assertThat(payload.reason()).isEqualTo("RESTOCK");
    }

    @Test
    @DisplayName("이벤트 발행 실패 시 재고는 반영되고 예외는 전파되지 않는다")
    void adjust_eventPublishFails_stockStillSaved() {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Product product = makeProductWithVariant(productId, variantId, 10, ProductStatus.ON_SALE);
        Inventory inventory = Inventory.create(variantId, new StockQuantity(10));

        given(productRepository.findById(productId)).willReturn(Optional.of(product));
        given(inventoryRepository.findByVariantId(variantId)).willReturn(Optional.of(inventory));
        given(inventoryRepository.save(any())).willReturn(inventory);
        willThrow(new RuntimeException("kafka down")).given(productEventPublisher).publish(any());

        AdjustStockResult result = adjustStockService.adjust(
                new AdjustStockCommand(productId, variantId, 5, "RESTOCK"));

        assertThat(result.currentStock()).isEqualTo(15);
        verify(inventoryRepository).save(inventory);
    }
}
