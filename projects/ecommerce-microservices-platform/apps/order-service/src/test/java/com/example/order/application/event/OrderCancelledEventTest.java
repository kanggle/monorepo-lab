package com.example.order.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderCancelledEvent 단위 테스트")
class OrderCancelledEventTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-03-25T15:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("cancelledAt이 도메인 취소 시각과 일치한다")
    void of_cancelledAt_matchesDomainCancelTime() {
        Instant domainCancelledAt = Instant.parse("2026-03-25T14:30:00Z");

        OrderCancelledEvent event = OrderCancelledEvent.of("order-1", "user-1", domainCancelledAt, FIXED_CLOCK);

        assertThat(event.payload().cancelledAt()).isEqualTo(domainCancelledAt.toString());
    }

    @Test
    @DisplayName("occurred_at과 cancelledAt이 서로 다른 값일 수 있다")
    void of_occurredAtAndCancelledAt_canDiffer() {
        Instant pastCancelledAt = Instant.parse("2026-01-01T00:00:00Z");

        OrderCancelledEvent event = OrderCancelledEvent.of("order-1", "user-1", pastCancelledAt, FIXED_CLOCK);

        assertThat(event.occurredAt()).isNotEqualTo(event.payload().cancelledAt());
    }

    @Test
    @DisplayName("이벤트 JSON에 올바른 cancelledAt 값이 포함된다")
    void serialize_cancelledAt_isCorrectInJson() throws Exception {
        Instant domainCancelledAt = Instant.parse("2026-03-25T14:30:00Z");
        OrderCancelledEvent event = OrderCancelledEvent.of("order-1", "user-1", domainCancelledAt, FIXED_CLOCK);

        String json = objectMapper.writeValueAsString(event);

        assertThat(json).contains("\"cancelledAt\":\"" + domainCancelledAt.toString() + "\"");
    }
}
