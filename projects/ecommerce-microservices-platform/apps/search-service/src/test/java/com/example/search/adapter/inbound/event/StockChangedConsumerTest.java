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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("StockChangedConsumer 단위 테스트")
class StockChangedConsumerTest {

    @InjectMocks
    private StockChangedConsumer consumer;

    @Mock
    private IndexSyncService indexSyncService;

    @Mock
    private ObjectMapper objectMapper;

    private StockChangedEvent event(String productId, int currentStock) {
        return new StockChangedEvent(
                "event-id", "StockChanged", "2026-03-23T00:00:00Z", "product-service", "tenant-a",
                new StockChangedEvent.StockChangedPayload(
                        productId, "v1", 100, currentStock, currentStock - 100, "RESTOCK", null)
        );
    }

    @Test
    @DisplayName("정상 이벤트 수신 시 IndexSyncService.updateStock이 호출된다")
    void handle_validEvent_callsUpdateStock() {
        StockChangedEvent e = event("p1", 150);

        consumer.handle(e);

        verify(indexSyncService).updateStock("p1", 150);
    }

    @Test
    @DisplayName("재고가 0인 이벤트도 정상 처리된다")
    void handle_zeroStock_callsUpdateStock() {
        StockChangedEvent e = event("p1", 0);

        consumer.handle(e);

        verify(indexSyncService).updateStock("p1", 0);
    }

    @Test
    @DisplayName("productId가 null인 이벤트는 무시된다")
    void handle_nullProductId_skips() {
        StockChangedEvent e = event(null, 100);

        consumer.handle(e);

        verify(indexSyncService, never()).updateStock(any(), anyInt());
    }

    @Test
    @DisplayName("payload가 null인 이벤트는 무시된다")
    void handle_nullPayload_skips() {
        StockChangedEvent e = new StockChangedEvent(
                "event-id", "StockChanged", "2026-03-23T00:00:00Z", "product-service", null, null);

        consumer.handle(e);

        verify(indexSyncService, never()).updateStock(any(), anyInt());
    }

    @Test
    @DisplayName("역직렬화 실패 시 JsonProcessingException이 전파된다")
    void onMessage_invalidJson_throwsJsonProcessingException() throws JsonProcessingException {
        given(objectMapper.readValue("invalid", StockChangedEvent.class))
                .willThrow(new JsonProcessingException("parse error") {});

        assertThatThrownBy(() -> consumer.onMessage("invalid"))
                .isInstanceOf(JsonProcessingException.class);
    }

    @Test
    @DisplayName("updateStock 실패 시 예외가 전파된다")
    void handle_updateStockThrows_propagatesException() {
        StockChangedEvent e = event("p1", 150);
        doThrow(new RuntimeException("ES error")).when(indexSyncService).updateStock("p1", 150);

        assertThatThrownBy(() -> consumer.handle(e))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("ES error");
    }
}
