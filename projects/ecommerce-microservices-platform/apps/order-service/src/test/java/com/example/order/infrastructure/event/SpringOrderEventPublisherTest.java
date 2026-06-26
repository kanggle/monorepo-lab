package com.example.order.infrastructure.event;

import com.example.order.application.event.OrderCancelledEvent;
import com.example.order.application.event.OrderPlacedEvent;
import com.example.order.infrastructure.persistence.OrderOutboxEntity;
import com.example.order.infrastructure.persistence.OrderOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Unit test for the {@link SpringOrderEventPublisher} write path (TASK-BE-448,
 * outbox v2). Asserts each event persists an {@code order_outbox} row whose
 * wire-relevant fields are preserved exactly: the row {@code event_id} reuses the
 * envelope {@code event_id}, the payload is the byte-identical serialized envelope,
 * {@code aggregate_id} is the {@code orderId} (Kafka key source), and the routing
 * key {@code eventType} is the literal event-type name.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SpringOrderEventPublisher 단위 테스트 (outbox v2)")
class SpringOrderEventPublisherTest {

    @InjectMocks
    private SpringOrderEventPublisher springOrderEventPublisher;

    @Mock
    private OrderOutboxRepository outboxRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-03-25T12:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("publishOrderPlaced 호출 시 order_outbox v2 행이 저장된다 (wire 보존)")
    void publishOrderPlaced_savesV2Row() throws Exception {
        OrderPlacedEvent event = OrderPlacedEvent.of(
                "order-1", "user-1", 10000L,
                List.of(new OrderPlacedEvent.Item("p1", "v1", 1, 10000L)),
                new OrderPlacedEvent.ShippingAddress("홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호"),
                FIXED_CLOCK
        );

        springOrderEventPublisher.publishOrderPlaced(event);

        OrderOutboxEntity row = capturedRow();
        assertThat(row.getEventId()).isEqualTo(UUID.fromString(event.eventId()));
        assertThat(row.getEventType()).isEqualTo("OrderPlaced");
        assertThat(row.getAggregateType()).isEqualTo("Order");
        assertThat(row.getAggregateId()).isEqualTo("order-1");
        assertThat(row.getPartitionKey()).isNull();
        assertThat(row.getOccurredAt()).isEqualTo(Instant.parse(event.occurredAt()));
        assertThat(row.getPayload()).isEqualTo(objectMapper.writeValueAsString(event));
        assertThat(row.getPublishedAt()).isNull();
    }

    @Test
    @DisplayName("publishOrderCancelled 호출 시 order_outbox v2 행이 저장된다")
    void publishOrderCancelled_savesV2Row() throws Exception {
        Instant cancelledAt = Instant.parse("2026-03-25T12:00:00Z");
        OrderCancelledEvent event = OrderCancelledEvent.of("order-1", "user-1", cancelledAt, FIXED_CLOCK);

        springOrderEventPublisher.publishOrderCancelled(event);

        OrderOutboxEntity row = capturedRow();
        assertThat(row.getEventId()).isEqualTo(UUID.fromString(event.eventId()));
        assertThat(row.getEventType()).isEqualTo("OrderCancelled");
        assertThat(row.getAggregateId()).isEqualTo("order-1");
        assertThat(row.getPayload()).isEqualTo(objectMapper.writeValueAsString(event));
    }

    private OrderOutboxEntity capturedRow() {
        ArgumentCaptor<OrderOutboxEntity> captor = ArgumentCaptor.forClass(OrderOutboxEntity.class);
        verify(outboxRepository).save(captor.capture());
        return captor.getValue();
    }
}
