package com.example.membership.application.event;

import com.example.membership.domain.plan.PlanLevel;
import com.example.membership.domain.subscription.Subscription;
import com.example.membership.domain.subscription.status.SubscriptionStatus;
import com.example.messaging.outbox.OutboxWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link MembershipEventPublisher} verifying that each lifecycle
 * event writes a canonical envelope to the outbox with the expected payload
 * fields. Mockito-based — does not start the Spring context.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("MembershipEventPublisher 단위 테스트")
class MembershipEventPublisherTest {

    @Mock
    private OutboxWriter outboxWriter;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private MembershipEventPublisher publisher;

    private Subscription subscription(SubscriptionStatus status,
                                      LocalDateTime startedAt,
                                      LocalDateTime expiresAt,
                                      LocalDateTime cancelledAt) {
        return new Subscription(
                "sub-1",
                "acc-1",
                PlanLevel.FAN_CLUB,
                status,
                startedAt,
                expiresAt,
                cancelledAt,
                startedAt != null ? startedAt : LocalDateTime.parse("2026-04-14T10:00:00"),
                0);
    }

    @Test
    @DisplayName("publishActivated_activeSubscription_writesEnvelopeWithStartAndExpiry")
    void publishActivated_activeSubscription_writesEnvelopeWithStartAndExpiry() throws Exception {
        LocalDateTime startedAt = LocalDateTime.parse("2026-04-14T10:00:00");
        LocalDateTime expiresAt = LocalDateTime.parse("2026-05-14T10:00:00");
        Subscription sub = subscription(SubscriptionStatus.ACTIVE, startedAt, expiresAt, null);

        publisher.publishActivated(sub);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter).save(
                eq("membership"),
                eq("acc-1"),
                eq("membership.subscription.activated"),
                jsonCaptor.capture());

        JsonNode root = objectMapper.readTree(jsonCaptor.getValue());
        assertThat(root.get("eventId").asText()).isNotBlank();
        assertThat(root.get("eventType").asText()).isEqualTo("membership.subscription.activated");
        assertThat(root.get("source").asText()).isEqualTo("membership-service");
        assertThat(root.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(root.get("partitionKey").asText()).isEqualTo("acc-1");

        JsonNode payload = root.get("payload");
        assertThat(payload.get("subscriptionId").asText()).isEqualTo("sub-1");
        assertThat(payload.get("accountId").asText()).isEqualTo("acc-1");
        assertThat(payload.get("planLevel").asText()).isEqualTo("FAN_CLUB");
        assertThat(payload.get("startedAt").asText()).isEqualTo("2026-04-14T10:00:00Z");
        assertThat(payload.get("expiresAt").asText()).isEqualTo("2026-05-14T10:00:00Z");
    }

    @Test
    @DisplayName("publishActivated_freePlanWithoutExpiry_emitsNullExpiresAt")
    void publishActivated_freePlanWithoutExpiry_emitsNullExpiresAt() throws Exception {
        LocalDateTime startedAt = LocalDateTime.parse("2026-04-14T10:00:00");
        Subscription sub = new Subscription(
                "sub-free",
                "acc-free",
                PlanLevel.FREE,
                SubscriptionStatus.ACTIVE,
                startedAt,
                null,
                null,
                startedAt,
                0);

        publisher.publishActivated(sub);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter).save(
                eq("membership"),
                eq("acc-free"),
                eq("membership.subscription.activated"),
                jsonCaptor.capture());

        JsonNode payload = objectMapper.readTree(jsonCaptor.getValue()).get("payload");
        assertThat(payload.get("planLevel").asText()).isEqualTo("FREE");
        assertThat(payload.get("startedAt").asText()).isEqualTo("2026-04-14T10:00:00Z");
        assertThat(payload.get("expiresAt").isNull()).isTrue();
    }

    @Test
    @DisplayName("publishExpired_expiredSubscription_writesEnvelopeWithExpiredAt")
    void publishExpired_expiredSubscription_writesEnvelopeWithExpiredAt() throws Exception {
        LocalDateTime startedAt = LocalDateTime.parse("2026-04-14T10:00:00");
        LocalDateTime expiresAt = LocalDateTime.parse("2026-05-14T10:00:00");
        Subscription sub = subscription(SubscriptionStatus.EXPIRED, startedAt, expiresAt, null);

        publisher.publishExpired(sub);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter).save(
                eq("membership"),
                eq("acc-1"),
                eq("membership.subscription.expired"),
                jsonCaptor.capture());

        JsonNode root = objectMapper.readTree(jsonCaptor.getValue());
        assertThat(root.get("eventType").asText()).isEqualTo("membership.subscription.expired");

        JsonNode payload = root.get("payload");
        assertThat(payload.get("subscriptionId").asText()).isEqualTo("sub-1");
        assertThat(payload.get("accountId").asText()).isEqualTo("acc-1");
        assertThat(payload.get("planLevel").asText()).isEqualTo("FAN_CLUB");
        assertThat(payload.get("expiredAt").asText()).isEqualTo("2026-05-14T10:00:00Z");
    }

    @Test
    @DisplayName("publishCancelled_cancelledSubscription_writesEnvelopeWithCancelledAt")
    void publishCancelled_cancelledSubscription_writesEnvelopeWithCancelledAt() throws Exception {
        LocalDateTime startedAt = LocalDateTime.parse("2026-04-14T10:00:00");
        LocalDateTime expiresAt = LocalDateTime.parse("2026-05-14T10:00:00");
        LocalDateTime cancelledAt = LocalDateTime.parse("2026-04-20T12:00:00");
        Subscription sub = subscription(SubscriptionStatus.CANCELLED, startedAt, expiresAt, cancelledAt);

        publisher.publishCancelled(sub);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter).save(
                eq("membership"),
                eq("acc-1"),
                eq("membership.subscription.cancelled"),
                jsonCaptor.capture());

        JsonNode root = objectMapper.readTree(jsonCaptor.getValue());
        assertThat(root.get("eventType").asText()).isEqualTo("membership.subscription.cancelled");

        JsonNode payload = root.get("payload");
        assertThat(payload.get("subscriptionId").asText()).isEqualTo("sub-1");
        assertThat(payload.get("accountId").asText()).isEqualTo("acc-1");
        assertThat(payload.get("planLevel").asText()).isEqualTo("FAN_CLUB");
        assertThat(payload.get("cancelledAt").asText()).isEqualTo("2026-04-20T12:00:00Z");
    }
}
