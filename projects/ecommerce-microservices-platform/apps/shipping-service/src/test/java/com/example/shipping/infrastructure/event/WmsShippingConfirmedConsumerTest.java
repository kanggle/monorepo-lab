package com.example.shipping.infrastructure.event;

import com.example.shipping.application.service.ShippingCommandService;
import com.example.shipping.domain.exception.ShippingNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("WmsShippingConfirmedConsumer 단위 테스트")
class WmsShippingConfirmedConsumerTest {

    @InjectMocks
    private WmsShippingConfirmedConsumer consumer;

    @Mock
    private ShippingCommandService shippingCommandService;

    @Mock
    private EventDeduplicationChecker eventDeduplicationChecker;

    @Mock
    private ObjectMapper objectMapper;

    private WmsShippingConfirmedEvent event(String eventId, String orderNo) {
        return new WmsShippingConfirmedEvent(
                eventId, "outbound.shipping.confirmed", "2026-06-08T15:00:00Z",
                "outbound", "wms-internal-1",
                new WmsShippingConfirmedEvent.Payload(
                        "wms-internal-1", orderNo, "SHP-20260608-0001", "CJ-LOGISTICS",
                        "2026-06-08T15:00:00Z"));
    }

    @Test
    @DisplayName("정상 이벤트 수신 시 orderNo로 markShippedByOrderId 호출")
    void handle_validEvent_marksShipped() {
        WmsShippingConfirmedEvent event = event("evt-1", "order-1");
        given(eventDeduplicationChecker.isDuplicate("evt-1", "WmsShippingConfirmed")).willReturn(false);

        consumer.handle(event);

        verify(shippingCommandService).markShippedByOrderId(
                eq("order-1"), eq("SHP-20260608-0001"), eq("CJ-LOGISTICS"));
    }

    @Test
    @DisplayName("중복 이벤트는 무시된다 (eventId dedupe)")
    void handle_duplicate_skips() {
        WmsShippingConfirmedEvent event = event("evt-1", "order-1");
        given(eventDeduplicationChecker.isDuplicate("evt-1", "WmsShippingConfirmed")).willReturn(true);

        consumer.handle(event);

        verify(shippingCommandService, never()).markShippedByOrderId(any(), any(), any());
    }

    @Test
    @DisplayName("orderNo가 없으면 IllegalArgumentException (non-retryable -> DLT)")
    void handle_missingOrderNo_throwsIllegalArgument() {
        WmsShippingConfirmedEvent event = event("evt-2", "  ");
        given(eventDeduplicationChecker.isDuplicate("evt-2", "WmsShippingConfirmed")).willReturn(false);

        assertThatThrownBy(() -> consumer.handle(event))
                .isInstanceOf(IllegalArgumentException.class);
        verify(shippingCommandService, never()).markShippedByOrderId(any(), any(), any());
    }

    @Test
    @DisplayName("payload가 null이면 IllegalArgumentException")
    void handle_nullPayload_throwsIllegalArgument() {
        WmsShippingConfirmedEvent event = new WmsShippingConfirmedEvent(
                "evt-3", "outbound.shipping.confirmed", "2026-06-08T15:00:00Z",
                "outbound", "wms-internal-1", null);
        given(eventDeduplicationChecker.isDuplicate("evt-3", "WmsShippingConfirmed")).willReturn(false);

        assertThatThrownBy(() -> consumer.handle(event))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("미존재 주문이면 ShippingNotFound를 IllegalArgumentException으로 변환 (DLT)")
    void handle_unknownOrder_throwsIllegalArgument() {
        WmsShippingConfirmedEvent event = event("evt-4", "unknown-order");
        given(eventDeduplicationChecker.isDuplicate("evt-4", "WmsShippingConfirmed")).willReturn(false);
        doThrow(new ShippingNotFoundException("unknown-order"))
                .when(shippingCommandService).markShippedByOrderId(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> consumer.handle(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown-order");
    }
}
