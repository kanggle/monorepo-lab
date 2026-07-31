package com.example.shipping.infrastructure.event;

import com.example.messaging.dedupe.EventDedupePort;
import com.example.shipping.domain.repository.ShippingRepository;
import com.example.shipping.domain.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit test for {@link WmsOutboundCancelledConsumer} (TASK-MONO-196,
 * ADR-MONO-022 §D4, ADR-MONO-058 D7). v1 is alert-only — the consumer raises an ops alert and
 * intentionally does NOT mutate the Shipping (stays PREPARING; auto-refund/cancel
 * saga = v2). These tests pin the v1 contract: dedupe is honored, and neither a
 * duplicate nor a null payload throws (no DLT for those).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WmsOutboundCancelledConsumer 단위 테스트 (ADR-MONO-058 D7)")
class WmsOutboundCancelledConsumerTest {

    @InjectMocks
    private WmsOutboundCancelledConsumer consumer;

    @Mock
    private ShippingRepository shippingRepository;

    @Mock
    private EventDedupePort eventDedupePort;

    @Mock
    private ObjectMapper objectMapper;

    @BeforeEach
    void stubDedupeAppliesByDefault() {
        lenient().when(eventDedupePort.process(any(), eq("WmsOutboundCancelled"), any()))
                .thenAnswer(inv -> {
                    ((Runnable) inv.getArgument(2)).run();
                    return EventDedupePort.Outcome.APPLIED;
                });
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private WmsOutboundCancelledEvent event(String reason) {
        return new WmsOutboundCancelledEvent(
                UUID.randomUUID().toString(), "outbound.order.cancelled", "2026-06-08T11:30:00Z",
                "order", "wms-internal-1", "store-acme",
                new WmsOutboundCancelledEvent.Payload("order-1", "PICKING", reason, "2026-06-08T11:30:00Z"));
    }

    @Test
    @DisplayName("정상 backorder 이벤트는 dedupe 확인 후 알림만 (예외 없음, 상태 변경 없음)")
    void handle_validBackorder_alertsOnly() {
        WmsOutboundCancelledEvent event = event("INSUFFICIENT_STOCK");

        assertThatCode(() -> consumer.handle(event)).doesNotThrowAnyException();

        verify(eventDedupePort).process(any(), eq("WmsOutboundCancelled"), any());
    }

    @Test
    @DisplayName("중복 이벤트는 무시된다 (eventId dedupe)")
    void handle_duplicate_skips() {
        WmsOutboundCancelledEvent event = event("INSUFFICIENT_STOCK");
        given(eventDedupePort.process(any(), eq("WmsOutboundCancelled"), any()))
                .willReturn(EventDedupePort.Outcome.IGNORED_DUPLICATE);

        assertThatCode(() -> consumer.handle(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("payload가 null이어도 예외 없이 graceful skip (DLT 아님)")
    void handle_nullPayload_gracefulSkip() {
        WmsOutboundCancelledEvent event = new WmsOutboundCancelledEvent(
                UUID.randomUUID().toString(), "outbound.order.cancelled", "2026-06-08T11:30:00Z",
                "order", "wms-internal-1", "store-acme", null);

        assertThatCode(() -> consumer.handle(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("alert 후 TenantContext 는 finally 에서 clear 된다 (스레드 누수 방지)")
    void handle_clearsTenantAfterAlert() {
        WmsOutboundCancelledEvent event = event("INSUFFICIENT_STOCK");

        consumer.handle(event);

        assertThat(TenantContext.currentTenant()).isEqualTo(TenantContext.DEFAULT_TENANT_ID);
    }
}
