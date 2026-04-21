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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductUpdatedConsumer 단위 테스트")
class ProductUpdatedConsumerTest {

    @InjectMocks
    private ProductUpdatedConsumer consumer;

    @Mock
    private IndexSyncService indexSyncService;

    @Mock
    private ObjectMapper objectMapper;

    private ProductUpdatedEvent event(String productId, String name) {
        return new ProductUpdatedEvent(
                "event-id", "ProductUpdated", "2026-03-23T00:00:00Z", "product-service",
                new ProductUpdatedEvent.ProductUpdatedPayload(productId, name, "설명", 1000000L, "ON_SALE", "cat1", null)
        );
    }

    @Test
    @DisplayName("정상 이벤트 수신 시 upsertPreservingStock이 호출된다")
    void handle_validEvent_callsUpsertPreservingStock() {
        ProductUpdatedEvent e = event("p1", "노트북");

        consumer.handle(e);

        ArgumentCaptor<SearchDocument> captor = ArgumentCaptor.forClass(SearchDocument.class);
        verify(indexSyncService).upsertPreservingStock(captor.capture());

        SearchDocument doc = captor.getValue();
        assertThat(doc.productId()).isEqualTo("p1");
        assertThat(doc.name()).isEqualTo("노트북");
        assertThat(doc.description()).isEqualTo("설명");
        assertThat(doc.price()).isEqualTo(1000000L);
        assertThat(doc.status()).isEqualTo("ON_SALE");
        assertThat(doc.categoryId()).isEqualTo("cat1");
    }

    @Test
    @DisplayName("productId가 null인 이벤트는 무시된다")
    void handle_nullProductId_skips() {
        ProductUpdatedEvent e = event(null, "노트북");

        consumer.handle(e);

        verify(indexSyncService, never()).upsertPreservingStock(any());
    }

    @Test
    @DisplayName("name이 null인 이벤트는 무시된다")
    void handle_nullName_skips() {
        ProductUpdatedEvent e = event("p1", null);

        consumer.handle(e);

        verify(indexSyncService, never()).upsertPreservingStock(any());
    }

    @Test
    @DisplayName("payload가 null인 이벤트는 무시된다")
    void handle_nullPayload_skips() {
        ProductUpdatedEvent e = new ProductUpdatedEvent(
                "event-id", "ProductUpdated", "2026-03-23T00:00:00Z", "product-service", null);

        consumer.handle(e);

        verify(indexSyncService, never()).upsertPreservingStock(any());
    }

    @Test
    @DisplayName("역직렬화 실패 시 JsonProcessingException이 전파된다")
    void onMessage_invalidJson_throwsJsonProcessingException() throws JsonProcessingException {
        given(objectMapper.readValue("invalid", ProductUpdatedEvent.class))
                .willThrow(new JsonProcessingException("parse error") {});

        assertThatThrownBy(() -> consumer.onMessage("invalid"))
                .isInstanceOf(JsonProcessingException.class);
    }

    @Test
    @DisplayName("upsertPreservingStock 실패 시 예외가 전파된다")
    void handle_upsertThrows_propagatesException() {
        ProductUpdatedEvent e = event("p1", "노트북");
        doThrow(new RuntimeException("ES error")).when(indexSyncService).upsertPreservingStock(any());

        assertThatThrownBy(() -> consumer.handle(e))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("ES error");
    }
}
