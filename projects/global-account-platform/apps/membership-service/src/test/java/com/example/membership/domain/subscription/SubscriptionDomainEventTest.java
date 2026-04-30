package com.example.membership.domain.subscription;

import com.example.membership.domain.event.MembershipDomainEvent;
import com.example.membership.domain.plan.PlanLevel;
import com.example.membership.domain.subscription.status.SubscriptionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Subscription 도메인 이벤트 팩토리 메서드 단위 테스트")
class SubscriptionDomainEventTest {

    private static final String ACCOUNT_ID = "acc-1";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 4, 26, 12, 0, 0);
    private static final LocalDateTime EXPIRES = NOW.plusDays(30);

    private Subscription fanClubSubscription() {
        return new Subscription(
                "sub-1", ACCOUNT_ID, PlanLevel.FAN_CLUB, SubscriptionStatus.ACTIVE,
                NOW, EXPIRES, null, NOW, 0);
    }

    private Subscription expiredSubscription() {
        Subscription s = new Subscription(
                "sub-2", ACCOUNT_ID, PlanLevel.FAN_CLUB, SubscriptionStatus.EXPIRED,
                NOW, EXPIRES, null, NOW, 0);
        return s;
    }

    private Subscription cancelledSubscription() {
        return new Subscription(
                "sub-3", ACCOUNT_ID, PlanLevel.FAN_CLUB, SubscriptionStatus.CANCELLED,
                NOW, EXPIRES, NOW.plusDays(5), NOW, 0);
    }

    @Test
    @DisplayName("buildActivatedEvent — 계약 필드(subscriptionId, accountId, planLevel, startedAt, expiresAt) 포함")
    void buildActivatedEvent_containsAllContractFields() {
        Subscription s = fanClubSubscription();

        MembershipDomainEvent event = s.buildActivatedEvent();

        assertThat(event.eventType()).isEqualTo("membership.subscription.activated");
        Map<String, Object> p = event.payload();
        assertThat(p.get("subscriptionId")).isEqualTo("sub-1");
        assertThat(p.get("accountId")).isEqualTo(ACCOUNT_ID);
        assertThat(p.get("planLevel")).isEqualTo("FAN_CLUB");
        assertThat(p.get("startedAt")).isEqualTo("2026-04-26T12:00:00Z");
        assertThat(p.get("expiresAt")).isEqualTo("2026-05-26T12:00:00Z");
    }

    @Test
    @DisplayName("buildActivatedEvent — expiresAt null이면 payload에 null로 포함된다 (FREE 플랜)")
    void buildActivatedEvent_nullExpiresAt_nullInPayload() {
        Subscription s = new Subscription(
                "sub-free", ACCOUNT_ID, PlanLevel.FREE, SubscriptionStatus.ACTIVE,
                NOW, null, null, NOW, 0);

        MembershipDomainEvent event = s.buildActivatedEvent();

        assertThat(event.payload().get("expiresAt")).isNull();
    }

    @Test
    @DisplayName("buildExpiredEvent — expiredAt이 expiresAt 필드 기준으로 포함된다")
    void buildExpiredEvent_containsExpiredAt() {
        Subscription s = expiredSubscription();

        MembershipDomainEvent event = s.buildExpiredEvent();

        assertThat(event.eventType()).isEqualTo("membership.subscription.expired");
        assertThat(event.payload().get("expiredAt")).isEqualTo("2026-05-26T12:00:00Z");
        assertThat(event.payload().get("subscriptionId")).isEqualTo("sub-2");
        assertThat(event.payload().get("planLevel")).isEqualTo("FAN_CLUB");
    }

    @Test
    @DisplayName("buildCancelledEvent — cancelledAt 포함")
    void buildCancelledEvent_containsCancelledAt() {
        Subscription s = cancelledSubscription();

        MembershipDomainEvent event = s.buildCancelledEvent();

        assertThat(event.eventType()).isEqualTo("membership.subscription.cancelled");
        assertThat(event.payload().get("cancelledAt")).isEqualTo("2026-05-01T12:00:00Z");
        assertThat(event.payload().get("accountId")).isEqualTo(ACCOUNT_ID);
    }
}
