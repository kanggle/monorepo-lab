package com.example.order.infrastructure.event;

import com.example.order.application.event.OrderCancelledEvent;
import com.example.order.application.event.OrderPlacedEvent;
import com.example.messaging.outbox.OutboxWriter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SpringOrderEventPublisher 단위 테스트")
class SpringOrderEventPublisherTest {

    @InjectMocks
    private SpringOrderEventPublisher springOrderEventPublisher;

    @Mock
    private OutboxWriter outboxWriter;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-03-25T12:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("publishOrderPlaced 호출 시 outbox 테이블에 OrderPlaced 이벤트가 저장된다")
    void publishOrderPlaced_savesToOutbox() throws JsonProcessingException {
        OrderPlacedEvent event = OrderPlacedEvent.of(
                "order-1", "user-1", 10000L,
                List.of(new OrderPlacedEvent.Item("p1", "v1", 1, 10000L)),
                new OrderPlacedEvent.ShippingAddress("홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호"),
                FIXED_CLOCK
        );

        springOrderEventPublisher.publishOrderPlaced(event);

        String expectedPayload = objectMapper.writeValueAsString(event);
        verify(outboxWriter).save(eq("Order"), eq("order-1"), eq("OrderPlaced"), eq(expectedPayload));
    }

    @Test
    @DisplayName("publishOrderCancelled 호출 시 outbox 테이블에 OrderCancelled 이벤트가 저장된다")
    void publishOrderCancelled_savesToOutbox() throws JsonProcessingException {
        Instant cancelledAt = Instant.parse("2026-03-25T12:00:00Z");
        OrderCancelledEvent event = OrderCancelledEvent.of("order-1", "user-1", cancelledAt, FIXED_CLOCK);

        springOrderEventPublisher.publishOrderCancelled(event);

        String expectedPayload = objectMapper.writeValueAsString(event);
        verify(outboxWriter).save(eq("Order"), eq("order-1"), eq("OrderCancelled"), eq(expectedPayload));
    }
}
