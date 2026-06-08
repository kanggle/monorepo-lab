package com.example.shipping.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit test for {@link WmsOutboundCancelledConsumer} (TASK-MONO-196,
 * ADR-MONO-022 §D4). v1 is alert-only — the consumer raises an ops alert and
 * intentionally does NOT mutate the Shipping (stays PREPARING; auto-refund/cancel
 * saga = v2). These tests pin the v1 contract: dedupe is honored, and neither a
 * duplicate nor a null payload throws (no DLT for those).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WmsOutboundCancelledConsumer 단위 테스트")
class WmsOutboundCancelledConsumerTest {

    @InjectMocks
    private WmsOutboundCancelledConsumer consumer;

    @Mock
    private EventDeduplicationChecker eventDeduplicationChecker;

    @Mock
    private ObjectMapper objectMapper;

    private WmsOutboundCancelledEvent event(String eventId, String reason) {
        return new WmsOutboundCancelledEvent(
                eventId, "outbound.order.cancelled", "2026-06-08T11:30:00Z",
                "order", "wms-internal-1",
                new WmsOutboundCancelledEvent.Payload("order-1", "PICKING", reason, "2026-06-08T11:30:00Z"));
    }

    @Test
    @DisplayName("정상 backorder 이벤트는 dedupe 확인 후 알림만 (예외 없음, 상태 변경 없음)")
    void handle_validBackorder_alertsOnly() {
        WmsOutboundCancelledEvent event = event("evt-1", "INSUFFICIENT_STOCK");
        given(eventDeduplicationChecker.isDuplicate("evt-1", "WmsOutboundCancelled")).willReturn(false);

        assertThatCode(() -> consumer.handle(event)).doesNotThrowAnyException();

        verify(eventDeduplicationChecker).isDuplicate("evt-1", "WmsOutboundCancelled");
    }

    @Test
    @DisplayName("중복 이벤트는 무시된다 (eventId dedupe)")
    void handle_duplicate_skips() {
        WmsOutboundCancelledEvent event = event("evt-1", "INSUFFICIENT_STOCK");
        given(eventDeduplicationChecker.isDuplicate("evt-1", "WmsOutboundCancelled")).willReturn(true);

        assertThatCode(() -> consumer.handle(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("payload가 null이어도 예외 없이 graceful skip (DLT 아님)")
    void handle_nullPayload_gracefulSkip() {
        WmsOutboundCancelledEvent event = new WmsOutboundCancelledEvent(
                "evt-2", "outbound.order.cancelled", "2026-06-08T11:30:00Z",
                "order", "wms-internal-1", null);
        given(eventDeduplicationChecker.isDuplicate("evt-2", "WmsOutboundCancelled")).willReturn(false);

        assertThatCode(() -> consumer.handle(event)).doesNotThrowAnyException();

        verify(eventDeduplicationChecker, never()).isDuplicate("other", "x");
    }
}
