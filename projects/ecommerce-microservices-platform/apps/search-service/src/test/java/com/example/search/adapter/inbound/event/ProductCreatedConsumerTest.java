package com.example.search.adapter.inbound.event;

import com.example.search.application.service.IndexSyncService;
import com.example.search.domain.model.SearchDocument;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductCreatedConsumer 단위 테스트")
class ProductCreatedConsumerTest {

    @InjectMocks
    private ProductCreatedConsumer consumer;

    @Mock
    private IndexSyncService indexSyncService;

    @Mock
    private ObjectMapper objectMapper;

    private ProductCreatedEvent event(String productId, String name, List<ProductCreatedEvent.VariantPayload> variants) {
        return new ProductCreatedEvent(
                "event-id", "ProductCreated", "2026-03-23T00:00:00Z", "product-service", "tenant-a",
                new ProductCreatedEvent.ProductCreatedPayload(productId, name, "설명", 1000000L, "ON_SALE", "cat1", null, variants)
        );
    }

    @Test
    @DisplayName("ProductCreatedEvent 수신 시 IndexSyncService.upsert가 호출된다")
    void handle_validEvent_callsIndexSyncService() {
        ProductCreatedEvent e = event("p1", "노트북",
                List.of(new ProductCreatedEvent.VariantPayload("v1", "블랙", 5, 0),
                        new ProductCreatedEvent.VariantPayload("v2", "화이트", 3, 0)));

        consumer.handle(e);

        ArgumentCaptor<SearchDocument> captor = ArgumentCaptor.forClass(SearchDocument.class);
        verify(indexSyncService).upsert(captor.capture());

        SearchDocument doc = captor.getValue();
        assertThat(doc.productId()).isEqualTo("p1");
        assertThat(doc.name()).isEqualTo("노트북");
        assertThat(doc.totalStock()).isEqualTo(8);
    }

    @Test
    @DisplayName("productId가 null인 이벤트는 무시된다")
    void handle_nullProductId_skips() {
        ProductCreatedEvent e = event(null, "노트북", List.of());

        consumer.handle(e);

        verify(indexSyncService, never()).upsert(any());
    }

    @Test
    @DisplayName("variants가 없으면 totalStock이 0이다")
    void handle_noVariants_totalStockZero() {
        ProductCreatedEvent e = event("p2", "상품", List.of());

        consumer.handle(e);

        ArgumentCaptor<SearchDocument> captor = ArgumentCaptor.forClass(SearchDocument.class);
        verify(indexSyncService).upsert(captor.capture());
        assertThat(captor.getValue().totalStock()).isEqualTo(0);
    }

    @Test
    @DisplayName("역직렬화 실패 시 JsonProcessingException이 전파된다")
    void onMessage_invalidJson_throwsJsonProcessingException() throws JsonProcessingException {
        given(objectMapper.readValue("invalid", ProductCreatedEvent.class))
                .willThrow(new JsonProcessingException("parse error") {});

        assertThatThrownBy(() -> consumer.onMessage("invalid"))
                .isInstanceOf(JsonProcessingException.class);
    }

    @Test
    @DisplayName("upsert 실패 시 예외가 전파된다")
    void handle_upsertThrows_propagatesException() {
        ProductCreatedEvent e = event("p1", "노트북", List.of());
        doThrow(new RuntimeException("ES error")).when(indexSyncService).upsert(any());

        assertThatThrownBy(() -> consumer.handle(e))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("ES error");
    }
}
