package com.example.review.domain.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ReviewEvent 단위 테스트")
class ReviewEventTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-01-01T00:00:00Z");
    private final Clock fixedClock = Clock.fixed(FIXED_TIME, ZoneOffset.UTC);

    @Test
    @DisplayName("ReviewEvent.created() 는 고정 Clock 기준 occurredAt 을 설정한다")
    void created_usesClockForOccurredAt() {
        ReviewCreatedPayload payload = new ReviewCreatedPayload(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                5,
                FIXED_TIME.toString()
        );

        ReviewEvent event = ReviewEvent.created(payload, "ecommerce", fixedClock);

        assertThat(event.occurredAt()).isEqualTo(FIXED_TIME);
        assertThat(event.eventType()).isEqualTo("ReviewCreated");
        assertThat(event.source()).isEqualTo("review-service");
        assertThat(event.eventId()).isNotNull();
        assertThat(event.payload()).isEqualTo(payload);
        assertThat(event.tenantId()).isEqualTo("ecommerce");
    }

    @Test
    @DisplayName("ReviewEvent.updated() 는 고정 Clock 기준 occurredAt 을 설정한다")
    void updated_usesClockForOccurredAt() {
        ReviewUpdatedPayload payload = new ReviewUpdatedPayload(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                4,
                FIXED_TIME.toString()
        );

        ReviewEvent event = ReviewEvent.updated(payload, "ecommerce", fixedClock);

        assertThat(event.occurredAt()).isEqualTo(FIXED_TIME);
        assertThat(event.eventType()).isEqualTo("ReviewUpdated");
        assertThat(event.source()).isEqualTo("review-service");
    }

    @Test
    @DisplayName("ReviewEvent.deleted() 는 고정 Clock 기준 occurredAt 을 설정한다")
    void deleted_usesClockForOccurredAt() {
        ReviewDeletedPayload payload = new ReviewDeletedPayload(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                FIXED_TIME.toString()
        );

        ReviewEvent event = ReviewEvent.deleted(payload, "ecommerce", fixedClock);

        assertThat(event.occurredAt()).isEqualTo(FIXED_TIME);
        assertThat(event.eventType()).isEqualTo("ReviewDeleted");
        assertThat(event.source()).isEqualTo("review-service");
    }

    @Test
    @DisplayName("Clock 이 null 이면 IllegalArgumentException 이 발생한다")
    void of_nullClock_throwsException() {
        ReviewCreatedPayload payload = new ReviewCreatedPayload(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                5,
                FIXED_TIME.toString()
        );

        assertThatThrownBy(() -> ReviewEvent.created(payload, "ecommerce", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("clock must not be null");
    }

    @Test
    @DisplayName("각 이벤트는 고유한 eventId 를 가진다")
    void created_hasUniqueEventId() {
        ReviewCreatedPayload payload = new ReviewCreatedPayload(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                5,
                FIXED_TIME.toString()
        );

        ReviewEvent event1 = ReviewEvent.created(payload, "ecommerce", fixedClock);
        ReviewEvent event2 = ReviewEvent.created(payload, "ecommerce", fixedClock);

        assertThat(event1.eventId()).isNotEqualTo(event2.eventId());
    }
}
