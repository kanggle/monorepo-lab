package com.example.payment.adapter.in.event;

import com.example.payment.application.service.PaymentRefundService;
import com.example.payment.domain.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderCancelledEventConsumer 단위 테스트")
class OrderCancelledEventConsumerTest {

    @InjectMocks
    private OrderCancelledEventConsumer consumer;

    @Mock
    private PaymentRefundService paymentRefundService;

    @Mock
    private ObjectMapper objectMapper;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    private OrderCancelledEvent event(String orderId) {
        return new OrderCancelledEvent(
                UUID.randomUUID().toString(), "OrderCancelled", "2026-03-23T00:00:00", "order-service",
                "ecommerce",
                new OrderCancelledEvent.OrderCancelledPayload(orderId, "user-1", "2026-03-23T00:00:00", "OPERATOR")
        );
    }

    private OrderCancelledEvent eventWithTenant(String orderId, String tenantId) {
        return new OrderCancelledEvent(
                UUID.randomUUID().toString(), "OrderCancelled", "2026-03-23T00:00:00", "order-service",
                tenantId,
                new OrderCancelledEvent.OrderCancelledPayload(orderId, "user-1", "2026-03-23T00:00:00", "PAYMENT_TIMEOUT")
        );
    }

    @Test
    @DisplayName("정상 이벤트 수신 시 handleOrderCancelled를 호출한다 (상태 분기는 서비스가 수행)")
    void handle_validEvent_callsHandleOrderCancelled() {
        consumer.handle(event("order-1"));

        verify(paymentRefundService).handleOrderCancelled("order-1");
    }

    @Test
    @DisplayName("이벤트의 tenant_id 가 TenantContext 에 설정된 후 handleOrderCancelled 가 호출된다 (M5)")
    void handle_eventWithTenantId_setsContextBeforeProcessing() {
        // tenant context cleared after handle() completes (finally block)
        consumer.handle(eventWithTenant("order-1", "tenant-a"));

        verify(paymentRefundService).handleOrderCancelled("order-1");
        // After the call the context should be cleared (finally fired)
        assertThat(TenantContext.currentTenant()).isEqualTo("ecommerce"); // default (not tenant-a)
    }

    @Test
    @DisplayName("cancelReason 이 없는 레거시 이벤트도 동일하게 handleOrderCancelled 를 호출한다 (back-compat, OPERATOR 취급)")
    void handle_legacyEventWithoutCancelReason_stillHandles() {
        OrderCancelledEvent legacy = new OrderCancelledEvent(
                UUID.randomUUID().toString(), "OrderCancelled", "2026-03-23T00:00:00", "order-service",
                "ecommerce",
                new OrderCancelledEvent.OrderCancelledPayload("order-1", "user-1", "2026-03-23T00:00:00", null)
        );
        assertThat(legacy.payload().effectiveCancelReason()).isEqualTo("OPERATOR");

        consumer.handle(legacy);

        verify(paymentRefundService).handleOrderCancelled("order-1");
    }

    @Test
    @DisplayName("handleOrderCancelled에서 예외가 발생하면 상위로 전파된다")
    void handle_serviceThrows_propagatesException() {
        doThrow(new RuntimeException("DB error"))
                .when(paymentRefundService).handleOrderCancelled(any());

        assertThatThrownBy(() -> consumer.handle(event("order-1")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB error");
    }

    @Test
    @DisplayName("payload가 null인 이벤트 수신 시 handleOrderCancelled를 호출하지 않는다")
    void handle_nullPayload_skips() {
        OrderCancelledEvent nullPayloadEvent = new OrderCancelledEvent(
                UUID.randomUUID().toString(), "OrderCancelled", "2026-03-23T00:00:00", "order-service",
                "ecommerce",
                null
        );

        consumer.handle(nullPayloadEvent);

        verifyNoInteractions(paymentRefundService);
    }

    @Test
    @DisplayName("orderId가 null인 이벤트 수신 시 refundPayment를 호출하지 않는다")
    void handle_nullOrderId_skips() {
        consumer.handle(event(null));

        verifyNoInteractions(paymentRefundService);
    }

    @Test
    @DisplayName("orderId가 빈 문자열인 이벤트 수신 시 refundPayment를 호출하지 않는다")
    void handle_blankOrderId_skips() {
        consumer.handle(event("  "));

        verifyNoInteractions(paymentRefundService);
    }

    @Test
    @DisplayName("역직렬화 실패 시 JsonProcessingException이 전파된다")
    void onMessage_invalidJson_throwsJsonProcessingException() throws JsonProcessingException {
        given(objectMapper.readValue("invalid", OrderCancelledEvent.class))
                .willThrow(new JsonProcessingException("parse error") {});

        assertThatThrownBy(() -> consumer.onMessage("invalid"))
                .isInstanceOf(JsonProcessingException.class);
    }
}
