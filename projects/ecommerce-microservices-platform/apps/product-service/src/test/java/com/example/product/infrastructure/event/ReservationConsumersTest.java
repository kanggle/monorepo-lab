package com.example.product.infrastructure.event;

import com.example.product.application.service.ReservationService;
import com.example.product.domain.model.reservation.StockReservationLine;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for the three reservation saga consumers (TASK-BE-428): envelope parse + delegate +
 * dedup. A real tolerant {@link ObjectMapper} (envelope deserialization is part of the contract)
 * + mocked {@link ReservationService}/{@link ReservationEventDedupe}.
 */
@DisplayName("Reservation saga consumers 단위 테스트 (TASK-BE-428)")
class ReservationConsumersTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final String EVENT_ID = UUID.randomUUID().toString();
    private static final String ORDER_ID = UUID.randomUUID().toString();
    private static final String PRODUCT_ID = UUID.randomUUID().toString();
    private static final String VARIANT_ID = UUID.randomUUID().toString();

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("OrderPlacedReservationConsumer")
    class OrderPlaced {
        @Mock ReservationService service;
        @Mock ReservationEventDedupe dedupe;
        OrderPlacedReservationConsumer consumer;

        private OrderPlacedReservationConsumer consumer() {
            if (consumer == null) {
                consumer = new OrderPlacedReservationConsumer(service, dedupe, MAPPER);
            }
            return consumer;
        }

        // ENVELOPE wire (order-events.md): event_id/tenant_id + nested payload.items[].
        private String wire() {
            return """
                    {
                      "event_id": "%s",
                      "event_type": "OrderPlaced",
                      "source": "order-service",
                      "tenant_id": "ecommerce",
                      "payload": {
                        "orderId": "%s",
                        "userId": "u-1",
                        "totalPrice": 30000,
                        "items": [
                          {"productId": "%s", "variantId": "%s", "quantity": 2, "unitPrice": 15000, "sellerId": "s-1"}
                        ]
                      }
                    }
                    """.formatted(EVENT_ID, ORDER_ID, PRODUCT_ID, VARIANT_ID);
        }

        @Test
        @DisplayName("정상 OrderPlaced → recordOrderPlaced 에 라인 위임")
        void valid_delegates() throws Exception {
            given(dedupe.isDuplicate(any(), eq("order.order.placed"))).willReturn(false);

            consumer().onMessage(wire());

            ArgumentCaptor<List<StockReservationLine>> captor = ArgumentCaptor.forClass(List.class);
            verify(service).recordOrderPlaced(eq(ORDER_ID), eq("ecommerce"), captor.capture());
            List<StockReservationLine> lines = captor.getValue();
            assertThat(lines).hasSize(1);
            assertThat(lines.get(0).variantId()).isEqualTo(UUID.fromString(VARIANT_ID));
            assertThat(lines.get(0).productId()).isEqualTo(UUID.fromString(PRODUCT_ID));
            assertThat(lines.get(0).quantity()).isEqualTo(2);
        }

        @Test
        @DisplayName("중복 event_id → skip")
        void duplicate_skips() throws Exception {
            given(dedupe.isDuplicate(any(), eq("order.order.placed"))).willReturn(true);
            consumer().onMessage(wire());
            verify(service, never()).recordOrderPlaced(anyString(), anyString(), anyList());
        }

        @Test
        @DisplayName("라인에 variantId 없으면 그 라인 skip(예약 불가) — 남는 라인 없으면 위임 안 함")
        void lineWithoutVariant_skipped() throws Exception {
            given(dedupe.isDuplicate(any(), eq("order.order.placed"))).willReturn(false);
            String wire = """
                    {
                      "event_id": "%s",
                      "tenant_id": "ecommerce",
                      "payload": {
                        "orderId": "%s",
                        "items": [ {"productId": "%s", "variantId": null, "quantity": 2} ]
                      }
                    }
                    """.formatted(EVENT_ID, ORDER_ID, PRODUCT_ID);

            consumer().onMessage(wire);

            verify(service, never()).recordOrderPlaced(anyString(), anyString(), anyList());
        }

        @Test
        @DisplayName("잘못된 JSON → IllegalArgument 계열로 DLQ 라우팅(예외 전파)")
        void malformed_throws() {
            assertThatThrownBy(() -> consumer().onMessage("{ not json }"))
                    .isInstanceOf(Exception.class);
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("PaymentCompletedReservationConsumer")
    class PaymentCompleted {
        @Mock ReservationService service;
        @Mock ReservationEventDedupe dedupe;
        PaymentCompletedReservationConsumer consumer;

        private PaymentCompletedReservationConsumer consumer() {
            if (consumer == null) {
                consumer = new PaymentCompletedReservationConsumer(service, dedupe, MAPPER);
            }
            return consumer;
        }

        // payment-events.md: payload carries orderId only (no items).
        private String wire() {
            return """
                    {
                      "event_id": "%s",
                      "event_type": "PaymentCompleted",
                      "source": "payment-service",
                      "tenant_id": "ecommerce",
                      "payload": {
                        "paymentId": "pay-1",
                        "orderId": "%s",
                        "userId": "u-1",
                        "amount": 30000,
                        "paidAt": "2026-06-23T10:00:00Z"
                      }
                    }
                    """.formatted(EVENT_ID, ORDER_ID);
        }

        @Test
        @DisplayName("정상 PaymentCompleted → recordPaymentCompleted(orderId, tenant) 위임")
        void valid_delegates() throws Exception {
            given(dedupe.isDuplicate(any(), eq("payment.payment.completed"))).willReturn(false);
            consumer().onMessage(wire());
            verify(service).recordPaymentCompleted(ORDER_ID, "ecommerce");
        }

        @Test
        @DisplayName("중복 event_id → skip")
        void duplicate_skips() throws Exception {
            given(dedupe.isDuplicate(any(), eq("payment.payment.completed"))).willReturn(true);
            consumer().onMessage(wire());
            verify(service, never()).recordPaymentCompleted(anyString(), anyString());
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("OrderCancelledReservationConsumer")
    class OrderCancelled {
        @Mock ReservationService service;
        @Mock ReservationEventDedupe dedupe;
        OrderCancelledReservationConsumer consumer;

        private OrderCancelledReservationConsumer consumer() {
            if (consumer == null) {
                consumer = new OrderCancelledReservationConsumer(service, dedupe, MAPPER);
            }
            return consumer;
        }

        private String wire() {
            return """
                    {
                      "event_id": "%s",
                      "event_type": "OrderCancelled",
                      "source": "order-service",
                      "tenant_id": "ecommerce",
                      "payload": {
                        "orderId": "%s",
                        "userId": "u-1",
                        "cancelledAt": "2026-06-23T10:00:00Z"
                      }
                    }
                    """.formatted(EVENT_ID, ORDER_ID);
        }

        @Test
        @DisplayName("정상 OrderCancelled → release(orderId) 위임")
        void valid_delegates() throws Exception {
            given(dedupe.isDuplicate(any(), eq("order.order.cancelled"))).willReturn(false);
            consumer().onMessage(wire());
            verify(service).release(ORDER_ID);
        }

        @Test
        @DisplayName("중복 event_id → skip")
        void duplicate_skips() throws Exception {
            given(dedupe.isDuplicate(any(), eq("order.order.cancelled"))).willReturn(true);
            consumer().onMessage(wire());
            verify(service, never()).release(anyString());
        }
    }
}
