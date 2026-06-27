package com.example.membership.infrastructure.outbox;

import com.example.membership.domain.plan.PlanLevel;
import com.example.membership.domain.subscription.Subscription;
import com.example.membership.domain.subscription.status.SubscriptionStatus;
import com.example.membership.infrastructure.persistence.MembershipOutboxJpaEntity;
import com.example.membership.infrastructure.persistence.MembershipOutboxJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Unit test for {@link OutboxMembershipEventPublisher} (TASK-BE-454 — outbox v1 → v2
 * write adapter). Replaces the v1 {@code MembershipEventPublisherTest} which mocked
 * the lib {@code OutboxWriter}; now we mock the per-service
 * {@link MembershipOutboxJpaRepository}, capture the persisted row, and assert the
 * envelope JSON is the byte-identical 7-field shape the v1
 * {@code BaseEventPublisher.writeEvent} path produced (wire-preservation invariant):
 * {@code eventId == row.id}, {@code source == "membership-service"},
 * {@code schemaVersion == 1}, {@code partitionKey == aggregateId}, payload fields
 * (from the domain factories) verbatim.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("OutboxMembershipEventPublisher 단위 테스트")
class OutboxMembershipEventPublisherTest {

    @Mock
    private MembershipOutboxJpaRepository outboxRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OutboxMembershipEventPublisher publisher() {
        return new OutboxMembershipEventPublisher(outboxRepository, objectMapper,
                Clock.fixed(Instant.parse("2026-04-14T10:00:00Z"), ZoneOffset.UTC));
    }

    private Subscription subscription(SubscriptionStatus status, PlanLevel plan,
                                      LocalDateTime startedAt, LocalDateTime expiresAt,
                                      LocalDateTime cancelledAt) {
        return new Subscription(
                "sub-1",
                "acc-1",
                plan,
                status,
                startedAt,
                expiresAt,
                cancelledAt,
                startedAt != null ? startedAt : LocalDateTime.parse("2026-04-14T10:00:00"),
                0);
    }

    private MembershipOutboxJpaEntity captureRow() {
        ArgumentCaptor<MembershipOutboxJpaEntity> captor =
                ArgumentCaptor.forClass(MembershipOutboxJpaEntity.class);
        verify(outboxRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("publishActivated writes the canonical envelope; eventId == row id")
    void publishActivated_writesEnvelopeWithStartAndExpiry() throws Exception {
        LocalDateTime startedAt = LocalDateTime.parse("2026-04-14T10:00:00");
        LocalDateTime expiresAt = LocalDateTime.parse("2026-05-14T10:00:00");
        Subscription sub = subscription(SubscriptionStatus.ACTIVE, PlanLevel.FAN_CLUB,
                startedAt, expiresAt, null);

        publisher().publishActivated(sub);

        MembershipOutboxJpaEntity row = captureRow();
        assertThat(row.getEventType()).isEqualTo("membership.subscription.activated");
        assertThat(row.getAggregateType()).isEqualTo("membership");
        assertThat(row.getAggregateId()).isEqualTo("acc-1");
        assertThat(row.getPartitionKey()).isEqualTo("acc-1");

        JsonNode root = objectMapper.readTree(row.getPayload());
        assertThat(root.get("eventId").asText()).isEqualTo(row.getId().toString());
        assertThat(root.get("eventType").asText()).isEqualTo("membership.subscription.activated");
        assertThat(root.get("source").asText()).isEqualTo("membership-service");
        assertThat(root.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(root.get("partitionKey").asText()).isEqualTo("acc-1");
        assertThat(root.get("occurredAt").asText()).isEqualTo("2026-04-14T10:00:00Z");

        JsonNode payload = root.get("payload");
        assertThat(payload.get("subscriptionId").asText()).isEqualTo("sub-1");
        assertThat(payload.get("accountId").asText()).isEqualTo("acc-1");
        assertThat(payload.get("planLevel").asText()).isEqualTo("FAN_CLUB");
        assertThat(payload.get("startedAt").asText()).isEqualTo("2026-04-14T10:00:00Z");
        assertThat(payload.get("expiresAt").asText()).isEqualTo("2026-05-14T10:00:00Z");
    }

    @Test
    @DisplayName("publishActivated FREE plan without expiry emits null expiresAt")
    void publishActivated_freePlanWithoutExpiry() throws Exception {
        LocalDateTime startedAt = LocalDateTime.parse("2026-04-14T10:00:00");
        Subscription sub = subscription(SubscriptionStatus.ACTIVE, PlanLevel.FREE,
                startedAt, null, null);

        publisher().publishActivated(sub);

        JsonNode payload = objectMapper.readTree(captureRow().getPayload()).get("payload");
        assertThat(payload.get("planLevel").asText()).isEqualTo("FREE");
        assertThat(payload.get("startedAt").asText()).isEqualTo("2026-04-14T10:00:00Z");
        assertThat(payload.get("expiresAt").isNull()).isTrue();
    }

    @Test
    @DisplayName("publishExpired writes envelope with expiredAt")
    void publishExpired_writesEnvelopeWithExpiredAt() throws Exception {
        LocalDateTime startedAt = LocalDateTime.parse("2026-04-14T10:00:00");
        LocalDateTime expiresAt = LocalDateTime.parse("2026-05-14T10:00:00");
        Subscription sub = subscription(SubscriptionStatus.EXPIRED, PlanLevel.FAN_CLUB,
                startedAt, expiresAt, null);

        publisher().publishExpired(sub);

        MembershipOutboxJpaEntity row = captureRow();
        JsonNode root = objectMapper.readTree(row.getPayload());
        assertThat(root.get("eventType").asText()).isEqualTo("membership.subscription.expired");

        JsonNode payload = root.get("payload");
        assertThat(payload.get("subscriptionId").asText()).isEqualTo("sub-1");
        assertThat(payload.get("accountId").asText()).isEqualTo("acc-1");
        assertThat(payload.get("planLevel").asText()).isEqualTo("FAN_CLUB");
        assertThat(payload.get("expiredAt").asText()).isEqualTo("2026-05-14T10:00:00Z");
    }

    @Test
    @DisplayName("publishCancelled writes envelope with cancelledAt")
    void publishCancelled_writesEnvelopeWithCancelledAt() throws Exception {
        LocalDateTime startedAt = LocalDateTime.parse("2026-04-14T10:00:00");
        LocalDateTime expiresAt = LocalDateTime.parse("2026-05-14T10:00:00");
        LocalDateTime cancelledAt = LocalDateTime.parse("2026-04-20T12:00:00");
        Subscription sub = subscription(SubscriptionStatus.CANCELLED, PlanLevel.FAN_CLUB,
                startedAt, expiresAt, cancelledAt);

        publisher().publishCancelled(sub);

        MembershipOutboxJpaEntity row = captureRow();
        JsonNode root = objectMapper.readTree(row.getPayload());
        assertThat(root.get("eventType").asText()).isEqualTo("membership.subscription.cancelled");

        JsonNode payload = root.get("payload");
        assertThat(payload.get("subscriptionId").asText()).isEqualTo("sub-1");
        assertThat(payload.get("accountId").asText()).isEqualTo("acc-1");
        assertThat(payload.get("planLevel").asText()).isEqualTo("FAN_CLUB");
        assertThat(payload.get("cancelledAt").asText()).isEqualTo("2026-04-20T12:00:00Z");
    }
}
