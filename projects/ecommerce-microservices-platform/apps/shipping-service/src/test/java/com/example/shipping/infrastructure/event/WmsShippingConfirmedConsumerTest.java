package com.example.shipping.infrastructure.event;

import com.example.messaging.dedupe.EventDedupePort;
import com.example.shipping.application.service.ShippingCommandService;
import com.example.shipping.domain.exception.ShippingNotFoundException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("WmsShippingConfirmedConsumer 단위 테스트 (ADR-MONO-058 D7)")
class WmsShippingConfirmedConsumerTest {

    @InjectMocks
    private WmsShippingConfirmedConsumer consumer;

    @Mock
    private ShippingCommandService shippingCommandService;

    @Mock
    private ShippingRepository shippingRepository;

    @Mock
    private EventDedupePort eventDedupePort;

    @Mock
    private ObjectMapper objectMapper;

    @BeforeEach
    void stubDedupeAppliesByDefault() {
        lenient().when(eventDedupePort.process(any(), eq("WmsShippingConfirmed"), any()))
                .thenAnswer(inv -> {
                    ((Runnable) inv.getArgument(2)).run();
                    return EventDedupePort.Outcome.APPLIED;
                });
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    // wms eventId is always a real UUID in production (wms owns a UUID-typed dedupe column) —
    // fixtures use randomUUID() rather than short literals to exercise the real UUID.fromString
    // parse path the consumer now runs (EventIds.parseOrNull), not just the mocked dedupe.
    private WmsShippingConfirmedEvent event(String orderNo) {
        return event(UUID.randomUUID().toString(), orderNo, "store-acme");
    }

    private WmsShippingConfirmedEvent event(String eventId, String orderNo, String tenantId) {
        return new WmsShippingConfirmedEvent(
                eventId, "outbound.shipping.confirmed", "2026-06-08T15:00:00Z",
                "outbound", "wms-internal-1", tenantId,
                new WmsShippingConfirmedEvent.Payload(
                        "wms-internal-1", orderNo, "SHP-20260608-0001", "CJ-LOGISTICS",
                        "2026-06-08T15:00:00Z"));
    }

    @Test
    @DisplayName("정상 이벤트 수신 시 orderNo로 markShippedByOrderId 호출")
    void handle_validEvent_marksShipped() {
        WmsShippingConfirmedEvent event = event("order-1");

        consumer.handle(event);

        verify(shippingCommandService).markShippedByOrderId(
                eq("order-1"), eq("SHP-20260608-0001"), eq("CJ-LOGISTICS"));
    }

    @Test
    @DisplayName("중복 이벤트는 무시된다 (eventId dedupe)")
    void handle_duplicate_skips() {
        WmsShippingConfirmedEvent event = event("order-1");
        given(eventDedupePort.process(any(), eq("WmsShippingConfirmed"), any()))
                .willReturn(EventDedupePort.Outcome.IGNORED_DUPLICATE);

        consumer.handle(event);

        verify(shippingCommandService, never()).markShippedByOrderId(any(), any(), any());
    }

    @Test
    @DisplayName("orderNo가 없으면 IllegalArgumentException (non-retryable -> DLT)")
    void handle_missingOrderNo_throwsIllegalArgument() {
        WmsShippingConfirmedEvent event = event("  ");

        assertThatThrownBy(() -> consumer.handle(event))
                .isInstanceOf(IllegalArgumentException.class);
        verify(shippingCommandService, never()).markShippedByOrderId(any(), any(), any());
    }

    @Test
    @DisplayName("payload가 null이면 IllegalArgumentException")
    void handle_nullPayload_throwsIllegalArgument() {
        WmsShippingConfirmedEvent event = new WmsShippingConfirmedEvent(
                UUID.randomUUID().toString(), "outbound.shipping.confirmed", "2026-06-08T15:00:00Z",
                "outbound", "wms-internal-1", "store-acme", null);

        assertThatThrownBy(() -> consumer.handle(event))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("봉투 tenantId 를 TenantContext 에 바인딩한 채 markShipped 실행, 이후 finally 로 clear")
    void handle_bindsTenantFromEnvelope_andClearsAfter() {
        WmsShippingConfirmedEvent event = event(UUID.randomUUID().toString(), "order-1", "store-acme");
        // Capture the tenant bound at the moment markShipped runs.
        String[] boundDuringCall = new String[1];
        org.mockito.BDDMockito.willAnswer(inv -> {
            boundDuringCall[0] = TenantContext.currentTenant();
            return null;
        }).given(shippingCommandService).markShippedByOrderId(anyString(), anyString(), anyString());

        consumer.handle(event);

        assertThat(boundDuringCall[0]).isEqualTo("store-acme");
        // Cleared in finally → reverts to the default tenant (no leak).
        assertThat(TenantContext.currentTenant()).isEqualTo(TenantContext.DEFAULT_TENANT_ID);
    }

    @Test
    @DisplayName("봉투 tenantId 부재 시 로컬 Shipping 행의 tenant 로 폴백 (D8)")
    void handle_absentEnvelopeTenant_fallsBackToLocalRow() {
        WmsShippingConfirmedEvent event = event(UUID.randomUUID().toString(), "order-9", null);
        com.example.shipping.domain.model.Shipping local = com.example.shipping.domain.model.Shipping.reconstitute(
                "ship-9", "store-zeta", "order-9", "user-9",
                com.example.shipping.domain.model.ShippingStatus.PREPARING,
                null, null, false, java.util.List.of(),
                java.time.Instant.now(), java.time.Instant.now());
        given(shippingRepository.findByOrderId("order-9")).willReturn(java.util.Optional.of(local));
        String[] boundDuringCall = new String[1];
        org.mockito.BDDMockito.willAnswer(inv -> {
            boundDuringCall[0] = TenantContext.currentTenant();
            return null;
        }).given(shippingCommandService).markShippedByOrderId(anyString(), anyString(), anyString());

        consumer.handle(event);

        assertThat(boundDuringCall[0]).isEqualTo("store-zeta");
        assertThat(TenantContext.currentTenant()).isEqualTo(TenantContext.DEFAULT_TENANT_ID);
    }

    @Test
    @DisplayName("미존재 주문이면 ShippingNotFound를 IllegalArgumentException으로 변환 (DLT)")
    void handle_unknownOrder_throwsIllegalArgument() {
        WmsShippingConfirmedEvent event = event("unknown-order");
        doThrow(new ShippingNotFoundException("unknown-order"))
                .when(shippingCommandService).markShippedByOrderId(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> consumer.handle(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown-order");
    }
}
