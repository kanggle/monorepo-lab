package com.example.product.application.service;

import com.example.product.domain.event.ProductDeletedPayload;
import com.example.product.domain.event.ProductEvent;
import com.example.product.domain.event.ProductEventPublisher;
import com.example.product.domain.exception.ProductNotFoundException;
import com.example.product.domain.model.Price;
import com.example.product.domain.model.Product;
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
@DisplayName("DeleteProductService 단위 테스트")
class DeleteProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductEventPublisher productEventPublisher;

    @Mock
    private ProductMetricPort productMetrics;

    private EventPublishingHelper eventPublishingHelper;
    private DeleteProductService deleteProductService;

    private Product existingProduct;

    @BeforeEach
    void setUp() {
        eventPublishingHelper = new EventPublishingHelper(productEventPublisher);
        deleteProductService = new DeleteProductService(productRepository, eventPublishingHelper, productMetrics);

        existingProduct = Product.create(
                "삭제할 상품", "설명", new Price(10000L), null,
                List.of(ProductVariant.create("기본", new StockQuantity(10), new Price(0L))));
    }

    @Test
    @DisplayName("삭제 성공 시 softDelete가 호출된다")
    void delete_success_callsSoftDelete() {
        UUID productId = existingProduct.getId();
        given(productRepository.findById(productId)).willReturn(Optional.of(existingProduct));

        deleteProductService.delete(productId);

        verify(productRepository).softDelete(productId);
    }

    @Test
    @DisplayName("삭제 성공 시 ProductDeleted 이벤트가 발행된다")
    void delete_success_publishesEvent() {
        UUID productId = existingProduct.getId();
        given(productRepository.findById(productId)).willReturn(Optional.of(existingProduct));

        deleteProductService.delete(productId);

        ArgumentCaptor<ProductEvent> captor = ArgumentCaptor.forClass(ProductEvent.class);
        verify(productEventPublisher).publish(captor.capture());

        ProductEvent event = captor.getValue();
        assertThat(event.eventType()).isEqualTo("ProductDeleted");
        assertThat(event.source()).isEqualTo("product-service");
        assertThat(event.eventId()).isNotNull();
        assertThat(event.payload()).isInstanceOf(ProductDeletedPayload.class);

        ProductDeletedPayload payload = (ProductDeletedPayload) event.payload();
        assertThat(payload.productId()).isEqualTo(productId.toString());
    }

    @Test
    @DisplayName("존재하지 않는 상품 삭제 시 ProductNotFoundException 발생")
    void delete_notFound_throwsException() {
        UUID unknownId = UUID.randomUUID();
        given(productRepository.findById(unknownId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> deleteProductService.delete(unknownId))
                .isInstanceOf(ProductNotFoundException.class);

        verify(productRepository, never()).softDelete(any());
        verify(productEventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("이벤트 발행 실패해도 삭제는 적용된다")
    void delete_eventPublishFails_productStillDeleted() {
        UUID productId = existingProduct.getId();
        given(productRepository.findById(productId)).willReturn(Optional.of(existingProduct));
        doThrow(new RuntimeException("Event publish failed")).when(productEventPublisher).publish(any());

        deleteProductService.delete(productId);

        verify(productRepository).softDelete(productId);
    }
}
