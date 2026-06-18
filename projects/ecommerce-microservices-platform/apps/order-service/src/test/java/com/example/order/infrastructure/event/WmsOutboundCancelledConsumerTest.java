package com.example.order.infrastructure.event;

import com.example.order.application.service.OrderBackorderCancellationService;
import com.example.order.domain.repository.OrderRepository;
import com.example.order.domain.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
    private OrderRepository orderRepository;

    @Mock
    private EventDeduplicationChecker eventDeduplicationChecker;

    @Mock
    private ObjectMapper objectMapper;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private WmsOutboundCancelledEvent event(String orderNo, String reason) {
        return event(orderNo, reason, "store-acme");
    }

    private WmsOutboundCancelledEvent event(String orderNo, String reason, String tenantId) {
        return new WmsOutboundCancelledEvent(
                UUID.randomUUID().toString(), "outbound.order.cancelled",
                "2026-06-08T10:00:00Z", "outbound-order", "wms-order-1", tenantId,
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
                "2026-06-08T10:00:00Z", "outbound-order", "wms-order-1", "store-acme", null);
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

    @Test
    @DisplayName("봉투 tenantId 를 TenantContext 에 바인딩한 채 cancel 위임, 이후 finally clear")
    void bindsTenantFromEnvelope_andClearsAfter() {
        WmsOutboundCancelledEvent event = event("order-1", "INSUFFICIENT_STOCK", "store-acme");
        given(eventDeduplicationChecker.isDuplicate(event.eventId(), "WmsOutboundCancelled"))
                .willReturn(false);
        String[] boundDuringCall = new String[1];
        org.mockito.BDDMockito.willAnswer(inv -> {
            boundDuringCall[0] = TenantContext.currentTenant();
            return null;
        }).given(backorderCancellationService).cancelForBackorder("order-1", "INSUFFICIENT_STOCK");

        consumer.handle(event);

        assertThat(boundDuringCall[0]).isEqualTo("store-acme");
        assertThat(TenantContext.currentTenant()).isEqualTo(TenantContext.DEFAULT_TENANT_ID);
    }

    @Test
    @DisplayName("봉투 tenantId 부재 시 로컬 Order 행의 tenant 로 폴백 (D8)")
    void absentEnvelopeTenant_fallsBackToLocalRow() {
        WmsOutboundCancelledEvent event = event("order-9", "INSUFFICIENT_STOCK", null);
        given(eventDeduplicationChecker.isDuplicate(event.eventId(), "WmsOutboundCancelled"))
                .willReturn(false);
        given(orderRepository.findTenantIdByOrderId("order-9")).willReturn(Optional.of("store-zeta"));
        String[] boundDuringCall = new String[1];
        org.mockito.BDDMockito.willAnswer(inv -> {
            boundDuringCall[0] = TenantContext.currentTenant();
            return null;
        }).given(backorderCancellationService).cancelForBackorder("order-9", "INSUFFICIENT_STOCK");

        consumer.handle(event);

        assertThat(boundDuringCall[0]).isEqualTo("store-zeta");
        assertThat(TenantContext.currentTenant()).isEqualTo(TenantContext.DEFAULT_TENANT_ID);
    }
}
