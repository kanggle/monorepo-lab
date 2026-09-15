package com.example.settlement.infrastructure.event;

import com.example.messaging.dedupe.EventDedupePort;
import com.example.settlement.application.service.RecordOrderSnapshotCommand;
import com.example.settlement.application.service.SettlementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderPlacedSnapshotConsumer — 쿠폰 할인이 스냅샷까지 온다 (TASK-BE-592)")
class OrderPlacedSnapshotConsumerDiscountTest {

    @Mock
    private SettlementService settlementService;
    @Mock
    private EventDedupePort eventDedupePort;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OrderPlacedSnapshotConsumer consumer;

    @BeforeEach
    void setUp() {
        lenient().when(eventDedupePort.process(any(), any(), any())).thenAnswer(inv -> {
            ((Runnable) inv.getArgument(2)).run();
            return EventDedupePort.Outcome.APPLIED;
        });
        consumer = new OrderPlacedSnapshotConsumer(settlementService, eventDedupePort, objectMapper);
    }

    private RecordOrderSnapshotCommand captured() {
        ArgumentCaptor<RecordOrderSnapshotCommand> captor = ArgumentCaptor.forClass(RecordOrderSnapshotCommand.class);
        verify(settlementService).recordSnapshot(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("🔴 실제 와이어 JSON 의 discountAmount·couponId 가 스냅샷 명령에 실린다")
    void wireJson_discountFieldsReachTheSnapshot() throws Exception {
        String json = """
                {
                  "event_id": "%s",
                  "event_type": "OrderPlaced",
                  "occurred_at": "2026-09-15T00:00:00Z",
                  "source": "order-service",
                  "tenant_id": "tenantA",
                  "payload": {
                    "orderId": "order-1",
                    "userId": "user-1",
                    "totalPrice": 25000,
                    "items": [{"productId": "p1", "variantId": "v1", "quantity": 1, "unitPrice": 30000, "sellerId": "seller-1"}],
                    "shippingAddress": {"recipient": "홍길동", "phone": "010", "zipCode": "1", "address1": "a", "address2": null},
                    "couponId": "coupon-1",
                    "discountAmount": 5000
                  }
                }
                """.formatted(UUID.randomUUID());

        consumer.onMessage(json);

        RecordOrderSnapshotCommand cmd = captured();
        assertThat(cmd.discountMinor()).isEqualTo(5_000L);
        assertThat(cmd.couponId()).isEqualTo("coupon-1");
        assertThat(cmd.lines()).singleElement().satisfies(l -> assertThat(l.grossMinor()).isEqualTo(30_000L));
    }

    @Test
    @DisplayName("쿠폰 필드가 없는 옛 이벤트는 할인 0, 쿠폰 없음")
    void wireJson_withoutCouponFields_isNoDiscount() throws Exception {
        String json = """
                {"event_id": "%s", "event_type": "OrderPlaced", "tenant_id": "tenantA",
                 "payload": {"orderId": "order-2", "totalPrice": 30000,
                             "items": [{"quantity": 1, "unitPrice": 30000, "sellerId": "seller-1"}]}}
                """.formatted(UUID.randomUUID());

        consumer.onMessage(json);

        RecordOrderSnapshotCommand cmd = captured();
        assertThat(cmd.discountMinor()).isZero();
        assertThat(cmd.couponId()).isNull();
    }

    @Test
    @DisplayName("음수 할인은 생산자 결함 — 할인 없음으로 기록한다")
    void negativeDiscount_isTreatedAsNone() {
        consumer.handle(new OrderPlacedEvent(UUID.randomUUID().toString(), "OrderPlaced", "tenantA",
                new OrderPlacedEvent.Payload("order-3",
                        List.of(new OrderPlacedEvent.Item(30_000L, 1, "seller-1")), "coupon-1", -5_000L)));

        assertThat(captured().discountMinor()).isZero();
    }
}
