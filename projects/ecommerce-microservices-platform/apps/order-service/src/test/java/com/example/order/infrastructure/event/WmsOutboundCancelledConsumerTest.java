package com.example.order.infrastructure.event;

import com.example.order.application.service.OrderBackorderCancellationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("WmsOutboundCancelledConsumer (order-service) 단위 테스트 — ADR-MONO-022 §D4 v2(a)")
class WmsOutboundCancelledConsumerTest {

    @InjectMocks
    private WmsOutboundCancelledConsumer consumer;

    @Mock
    private OrderBackorderCancellationService backorderCancellationService;

    @Mock
    private EventDeduplicationChecker eventDeduplicationChecker;

    @Mock
    private ObjectMapper objectMapper;

    private WmsOutboundCancelledEvent event(String orderNo, String reason) {
        return new WmsOutboundCancelledEvent(
                UUID.randomUUID().toString(), "outbound.order.cancelled",
                "2026-06-08T10:00:00Z", "outbound-order", "wms-order-1",
                new WmsOutboundCancelledEvent.Payload(orderNo, "PICKING", reason, "2026-06-08T10:00:00Z"));
    }

    @Test
    @DisplayName("정상 backorder 이벤트는 cancelForBackorder(orderNo, reason) 로 위임된다")
    void validEvent_delegatesToBackorderCancel() {
        WmsOutboundCancelledEvent event = event("order-1", "INSUFFICIENT_STOCK");
        given(eventDeduplicationChecker.isDuplicate(event.eventId(), "WmsOutboundCancelled"))
                .willReturn(false);

        consumer.handle(event);

        verify(backorderCancellationService).cancelForBackorder("order-1", "INSUFFICIENT_STOCK");
    }

    @Test
    @DisplayName("중복 이벤트는 무시된다")
    void duplicate_skips() {
        WmsOutboundCancelledEvent event = event("order-1", "INSUFFICIENT_STOCK");
        given(eventDeduplicationChecker.isDuplicate(event.eventId(), "WmsOutboundCancelled"))
                .willReturn(true);

        consumer.handle(event);

        verify(backorderCancellationService, never()).cancelForBackorder(any(), any());
    }

    @Test
    @DisplayName("payload 가 null 이면 무시된다")
    void nullPayload_skips() {
        WmsOutboundCancelledEvent event = new WmsOutboundCancelledEvent(
                UUID.randomUUID().toString(), "outbound.order.cancelled",
                "2026-06-08T10:00:00Z", "outbound-order", "wms-order-1", null);
        given(eventDeduplicationChecker.isDuplicate(event.eventId(), "WmsOutboundCancelled"))
                .willReturn(false);

        consumer.handle(event);

        verify(backorderCancellationService, never()).cancelForBackorder(any(), any());
    }

    @Test
    @DisplayName("orderNo 가 비어 있으면 무시된다")
    void blankOrderNo_skips() {
        WmsOutboundCancelledEvent event = event("  ", "INSUFFICIENT_STOCK");
        given(eventDeduplicationChecker.isDuplicate(event.eventId(), "WmsOutboundCancelled"))
                .willReturn(false);

        consumer.handle(event);

        verify(backorderCancellationService, never()).cancelForBackorder(eq("  "), any());
    }
}
