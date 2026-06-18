package com.example.search.adapter.inbound.event;

import com.example.search.application.service.IndexSyncService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductDeletedConsumer 단위 테스트")
class ProductDeletedConsumerTest {

    @InjectMocks
    private ProductDeletedConsumer consumer;

    @Mock
    private IndexSyncService indexSyncService;

    @Mock
    private ObjectMapper objectMapper;

    private ProductDeletedEvent event(String productId) {
        return new ProductDeletedEvent(
                "event-id", "ProductDeleted", "2026-03-23T00:00:00Z", "product-service", "tenant-a",
                new ProductDeletedEvent.ProductDeletedPayload(productId)
        );
    }

    @Test
    @DisplayName("정상 이벤트 수신 시 IndexSyncService.delete가 호출된다")
    void handle_validEvent_callsDelete() {
        ProductDeletedEvent e = event("p1");

        consumer.handle(e);

        verify(indexSyncService).delete("p1");
    }

    @Test
    @DisplayName("productId가 null인 이벤트는 무시된다")
    void handle_nullProductId_skips() {
        ProductDeletedEvent e = event(null);

        consumer.handle(e);

        verify(indexSyncService, never()).delete(any());
    }

    @Test
    @DisplayName("payload가 null인 이벤트는 무시된다")
    void handle_nullPayload_skips() {
        ProductDeletedEvent e = new ProductDeletedEvent(
                "event-id", "ProductDeleted", "2026-03-23T00:00:00Z", "product-service", null, null);

        consumer.handle(e);

        verify(indexSyncService, never()).delete(any());
    }

    @Test
    @DisplayName("역직렬화 실패 시 JsonProcessingException이 전파된다")
    void onMessage_invalidJson_throwsJsonProcessingException() throws JsonProcessingException {
        given(objectMapper.readValue("invalid", ProductDeletedEvent.class))
                .willThrow(new JsonProcessingException("parse error") {});

        assertThatThrownBy(() -> consumer.onMessage("invalid"))
                .isInstanceOf(JsonProcessingException.class);
    }

    @Test
    @DisplayName("delete 실패 시 예외가 전파된다")
    void handle_deleteThrows_propagatesException() {
        ProductDeletedEvent e = event("p1");
        doThrow(new RuntimeException("ES error")).when(indexSyncService).delete("p1");

        assertThatThrownBy(() -> consumer.handle(e))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("ES error");
    }
}
