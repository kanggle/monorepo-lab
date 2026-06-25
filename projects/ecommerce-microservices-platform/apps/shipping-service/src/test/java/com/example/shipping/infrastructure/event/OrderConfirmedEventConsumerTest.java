package com.example.shipping.infrastructure.event;

import com.example.shipping.application.command.CreateShippingCommand;
import com.example.shipping.application.port.ShippingEventPublisher;
import com.example.shipping.application.service.ShippingCommandService;
import com.example.shipping.infrastructure.config.FulfillmentProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderConfirmedEventConsumer 단위 테스트")
class OrderConfirmedEventConsumerTest {

    @InjectMocks
    private OrderConfirmedEventConsumer consumer;

    @Mock
    private ShippingCommandService shippingCommandService;

    @Mock
    private EventDeduplicationChecker eventDeduplicationChecker;

    @Mock
    private ShippingEventPublisher shippingEventPublisher;

    @Mock
    private FulfillmentAcl fulfillmentAcl;

    @Mock
    private FulfillmentProperties fulfillmentProperties;

    @Mock
    private ObjectMapper objectMapper;

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-08T10:00:00Z"), ZoneOffset.UTC);

    private OrderConfirmedEvent event(String eventId, String orderId, String userId) {
        return new OrderConfirmedEvent(
                eventId, "OrderConfirmed", "2026-06-08T10:00:00Z", "order-service", "tenant-a",
                new OrderConfirmedEvent.OrderConfirmedPayload(
                        orderId, userId, "2026-06-08T10:00:00Z",
                        List.of(new OrderConfirmedEvent.Line("v1", "p1", "v1", 2)),
                        new OrderConfirmedEvent.ShippingAddress("홍길동", "서울시 강남구 101호", "010-1234-5678"))
        );
    }

    private FulfillmentRequestedMessage fulfillmentMessage(String orderId) {
        return new FulfillmentRequestedMessage(
                "evt-fulfill", "ecommerce.fulfillment.requested", "2026-06-08T10:00:00Z",
                "fulfillment", orderId, "tenant-a",
                new FulfillmentRequestedMessage.Payload(
                        orderId, "ECOMMERCE-STORE", "WH-MAIN", null, null,
                        List.of(new FulfillmentRequestedMessage.Line(1, "v1", null, 2)))
        );
    }

    @Test
    @DisplayName("유효한 OrderConfirmed 이벤트 처리 시 배송 생성 + fulfillment 발행")
    void handle_validEvent_createsShippingAndPublishesFulfillment() throws JsonProcessingException {
        OrderConfirmedEvent event = event("evt-1", "order-1", "user-1");
        given(eventDeduplicationChecker.isDuplicate("evt-1", "OrderConfirmed")).willReturn(false);
        given(fulfillmentProperties.enabled()).willReturn(true);
        given(fulfillmentAcl.toFulfillmentRequested(any(), any())).willReturn(fulfillmentMessage("order-1"));
        given(objectMapper.writeValueAsString(any())).willReturn("{\"json\":true}");

        consumer.handle(event);

        verify(shippingCommandService).createShipping(new CreateShippingCommand("tenant-a", "order-1", "user-1"));
        verify(shippingEventPublisher).publishFulfillmentRequested(eq("order-1"), eq("{\"json\":true}"));
        // Fulfillment actually published ⇒ order is wmsRouted ⇒ mark in same tx.
        verify(shippingCommandService).markShippingWmsRouted("order-1");
    }

    @Test
    @DisplayName("fulfillment.enabled=false면 발행하지 않는다 (standalone degradation)")
    void handle_fulfillmentDisabled_doesNotPublish() {
        OrderConfirmedEvent event = event("evt-dis", "order-1", "user-1");
        given(eventDeduplicationChecker.isDuplicate("evt-dis", "OrderConfirmed")).willReturn(false);
        given(fulfillmentProperties.enabled()).willReturn(false);

        consumer.handle(event);

        verify(shippingCommandService).createShipping(new CreateShippingCommand("tenant-a", "order-1", "user-1"));
        verify(shippingEventPublisher, never()).publishFulfillmentRequested(any(), any());
        // Not published ⇒ not wmsRouted ⇒ no mark.
        verify(shippingCommandService, never()).markShippingWmsRouted(any());
    }

    @Test
    @DisplayName("매핑되지 않은 SKU(require-sku-mapping)면 발행하지 않고 알림만 남긴다")
    void handle_unmappedSku_doesNotPublish() {
        OrderConfirmedEvent event = event("evt-unmapped", "order-1", "user-1");
        given(eventDeduplicationChecker.isDuplicate("evt-unmapped", "OrderConfirmed")).willReturn(false);
        given(fulfillmentProperties.enabled()).willReturn(true);
        given(fulfillmentAcl.toFulfillmentRequested(any(), any()))
                .willThrow(new UnmappedSkuException("no mapping for v1"));

        consumer.handle(event);

        verify(shippingCommandService).createShipping(new CreateShippingCommand("tenant-a", "order-1", "user-1"));
        verify(shippingEventPublisher, never()).publishFulfillmentRequested(any(), any());
        // Unmapped SKU blocks publish ⇒ not wmsRouted ⇒ no mark.
        verify(shippingCommandService, never()).markShippingWmsRouted(any());
    }

    @Test
    @DisplayName("중복 이벤트는 무시된다")
    void handle_duplicateEvent_skips() {
        OrderConfirmedEvent event = event("evt-1", "order-1", "user-1");
        given(eventDeduplicationChecker.isDuplicate("evt-1", "OrderConfirmed")).willReturn(true);

        consumer.handle(event);

        verify(shippingCommandService, never()).createShipping(any());
        verify(shippingEventPublisher, never()).publishFulfillmentRequested(any(), any());
    }

    @Test
    @DisplayName("payload가 null이면 무시된다")
    void handle_nullPayload_skips() {
        OrderConfirmedEvent event = new OrderConfirmedEvent(
                "evt-2", "OrderConfirmed", "2026-06-08T10:00:00Z", "order-service", "tenant-a", null);
        given(eventDeduplicationChecker.isDuplicate("evt-2", "OrderConfirmed")).willReturn(false);

        consumer.handle(event);

        verify(shippingCommandService, never()).createShipping(any());
    }

    @Test
    @DisplayName("orderId가 없으면 무시된다")
    void handle_missingOrderId_skips() {
        OrderConfirmedEvent event = event("evt-3", null, "user-1");
        given(eventDeduplicationChecker.isDuplicate("evt-3", "OrderConfirmed")).willReturn(false);

        consumer.handle(event);

        verify(shippingCommandService, never()).createShipping(any());
    }

    @Test
    @DisplayName("userId가 없으면 무시된다")
    void handle_missingUserId_skips() {
        OrderConfirmedEvent event = event("evt-4", "order-1", "");
        given(eventDeduplicationChecker.isDuplicate("evt-4", "OrderConfirmed")).willReturn(false);

        consumer.handle(event);

        verify(shippingCommandService, never()).createShipping(any());
    }

    @Test
    @DisplayName("enriched payload 없이도(빈 lines) 무리없이 처리된다 — Map.of 직렬화 라운드트립")
    void handle_minimalPayload_stillProcesses() {
        // Ensures the consumer tolerates a producer that hasn't enriched lines/address.
        // No envelope tenant_id (pre-BE-357 producer) → defaults to ecommerce (net-zero, D8).
        OrderConfirmedEvent event = new OrderConfirmedEvent(
                "evt-min", "OrderConfirmed", "2026-06-08T10:00:00Z", "order-service", null,
                new OrderConfirmedEvent.OrderConfirmedPayload(
                        "order-min", "user-min", "2026-06-08T10:00:00Z", null, null));
        given(eventDeduplicationChecker.isDuplicate("evt-min", "OrderConfirmed")).willReturn(false);
        given(fulfillmentProperties.enabled()).willReturn(false);

        consumer.handle(event);

        verify(shippingCommandService).createShipping(new CreateShippingCommand("ecommerce", "order-min", "user-min"));
    }

    @Test
    @DisplayName("deserialization helper sanity: Map 직렬화/역직렬화")
    void objectMapper_roundTrip_sanity() throws Exception {
        ObjectMapper real = new ObjectMapper();
        String json = real.writeValueAsString(Map.of("event_id", "x"));
        assertThat(json).contains("event_id");
    }
}
