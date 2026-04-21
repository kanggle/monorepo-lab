package com.example.product.application.service;

import com.example.product.application.command.UpdateProductCommand;
import com.example.product.domain.event.ProductEvent;
import com.example.product.domain.event.ProductEventPublisher;
import com.example.product.domain.event.ProductUpdatedPayload;
import com.example.product.domain.exception.ProductNotFoundException;
import com.example.product.domain.model.Price;
import com.example.product.domain.model.Product;
import com.example.product.domain.model.ProductStatus;
import com.example.product.domain.model.ProductVariant;
import com.example.product.domain.model.StockQuantity;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateProductService 단위 테스트")
class UpdateProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductEventPublisher productEventPublisher;

    @Mock
    private ProductMetricPort productMetrics;

    private EventPublishingHelper eventPublishingHelper;
    private UpdateProductService updateProductService;

    private Product existingProduct;

    @BeforeEach
    void setUp() {
        eventPublishingHelper = new EventPublishingHelper(productEventPublisher);
        updateProductService = new UpdateProductService(productRepository, eventPublishingHelper, productMetrics);

        existingProduct = Product.create(
                "기존 상품명", "기존 설명", new Price(10000L), null,
                List.of(ProductVariant.create("기본", new StockQuantity(10), new Price(0L))));
    }

    @Test
    @DisplayName("수정 성공 시 상품 ID를 반환한다")
    void update_success_returnsId() {
        UUID productId = existingProduct.getId();
        given(productRepository.findById(productId)).willReturn(Optional.of(existingProduct));
        given(productRepository.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0));

        UpdateProductCommand command = new UpdateProductCommand(productId, "새 상품명", null, null, null);
        UUID result = updateProductService.update(command);

        assertThat(result).isEqualTo(productId);
        verify(productRepository).save(any(Product.class));
    }

    @Test
    @DisplayName("수정 성공 시 ProductUpdated 이벤트가 발행된다")
    void update_success_publishesEvent() {
        UUID productId = existingProduct.getId();
        given(productRepository.findById(productId)).willReturn(Optional.of(existingProduct));
        given(productRepository.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0));

        UpdateProductCommand command = new UpdateProductCommand(productId, "새 상품명", null, 20000L, null);
        updateProductService.update(command);

        ArgumentCaptor<ProductEvent> captor = ArgumentCaptor.forClass(ProductEvent.class);
        verify(productEventPublisher).publish(captor.capture());

        ProductEvent event = captor.getValue();
        assertThat(event.eventType()).isEqualTo("ProductUpdated");
        assertThat(event.source()).isEqualTo("product-service");
        assertThat(event.eventId()).isNotNull();
        assertThat(event.payload()).isInstanceOf(ProductUpdatedPayload.class);

        ProductUpdatedPayload payload = (ProductUpdatedPayload) event.payload();
        assertThat(payload.productId()).isEqualTo(productId.toString());
        assertThat(payload.name()).isEqualTo("새 상품명");
        assertThat(payload.price()).isEqualTo(20000L);
    }

    @Test
    @DisplayName("존재하지 않는 상품 수정 시 ProductNotFoundException 발생")
    void update_notFound_throwsException() {
        UUID unknownId = UUID.randomUUID();
        given(productRepository.findById(unknownId)).willReturn(Optional.empty());

        UpdateProductCommand command = new UpdateProductCommand(unknownId, "이름", null, null, null);

        assertThatThrownBy(() -> updateProductService.update(command))
                .isInstanceOf(ProductNotFoundException.class);

        verify(productRepository, never()).save(any());
        verify(productEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("이벤트 발행 실패해도 수정은 적용된다")
    void update_eventPublishFails_productStillSaved() {
        UUID productId = existingProduct.getId();
        given(productRepository.findById(productId)).willReturn(Optional.of(existingProduct));
        given(productRepository.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("Event publish failed")).when(productEventPublisher).publish(any());

        UpdateProductCommand command = new UpdateProductCommand(productId, "새 이름", null, null, null);
        UUID result = updateProductService.update(command);

        assertThat(result).isEqualTo(productId);
        verify(productRepository).save(any(Product.class));
    }

    @Test
    @DisplayName("null 필드는 수정하지 않는다 (partial update)")
    void update_nullFields_notModified() {
        UUID productId = existingProduct.getId();
        given(productRepository.findById(productId)).willReturn(Optional.of(existingProduct));
        given(productRepository.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0));

        UpdateProductCommand command = new UpdateProductCommand(productId, null, null, null, null);
        updateProductService.update(command);

        ArgumentCaptor<ProductEvent> captor = ArgumentCaptor.forClass(ProductEvent.class);
        verify(productEventPublisher).publish(captor.capture());
        ProductUpdatedPayload payload = (ProductUpdatedPayload) captor.getValue().payload();

        assertThat(payload.name()).isEqualTo("기존 상품명");
        assertThat(payload.price()).isEqualTo(10000L);
    }

    @Test
    @DisplayName("status를 동일한 값으로 수정 시 정상 처리된다 (멱등)")
    void update_sameStatus_idempotent() {
        UUID productId = existingProduct.getId();
        given(productRepository.findById(productId)).willReturn(Optional.of(existingProduct));
        given(productRepository.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0));

        UpdateProductCommand command = new UpdateProductCommand(productId, null, null, null, ProductStatus.ON_SALE);
        UUID result = updateProductService.update(command);

        assertThat(result).isEqualTo(productId);
        verify(productRepository).save(any(Product.class));
    }
}
