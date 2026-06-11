package com.example.fanplatform.membership.application.event;

import com.example.fanplatform.membership.domain.membership.MembershipTier;
import com.example.messaging.outbox.OutboxWriter;
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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class MembershipEventPublisherTest {

    private static final Instant NOW = Instant.parse("2026-06-09T00:00:00Z");

    @Mock OutboxWriter outboxWriter;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("publishActivated writes a fan.membership.activated envelope to the outbox")
    void activatedEnvelope() throws Exception {
        MembershipEventPublisher publisher = new MembershipEventPublisher(outboxWriter, mapper);
        publisher.publishActivated("m1", "fan-platform", "acc1", MembershipTier.PREMIUM, 1,
                NOW, NOW.plusSeconds(100), NOW);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter).save(eq("membership"), eq("m1"),
                eq(MembershipEventPublisher.EVENT_ACTIVATED), payloadCaptor.capture());

        JsonNode envelope = mapper.readTree(payloadCaptor.getValue());
        assertThat(envelope.path("eventType").asText()).isEqualTo("fan.membership.activated");
        assertThat(envelope.path("source").asText()).isEqualTo("fan-platform-membership-service");
        assertThat(envelope.path("partitionKey").asText()).isEqualTo("m1");
        JsonNode payload = envelope.path("payload");
        assertThat(payload.path("membershipId").asText()).isEqualTo("m1");
        assertThat(payload.path("tier").asText()).isEqualTo("PREMIUM");
        assertThat(payload.path("planMonths").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("publishCanceled writes a fan.membership.canceled envelope to the outbox")
    void canceledEnvelope() throws Exception {
        MembershipEventPublisher publisher = new MembershipEventPublisher(outboxWriter, mapper);
        publisher.publishCanceled("m1", "fan-platform", "acc1", MembershipTier.PREMIUM,
                "done", NOW, NOW);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter).save(eq("membership"), eq("m1"),
                eq(MembershipEventPublisher.EVENT_CANCELED), payloadCaptor.capture());

        JsonNode envelope = mapper.readTree(payloadCaptor.getValue());
        assertThat(envelope.path("eventType").asText()).isEqualTo("fan.membership.canceled");
        assertThat(envelope.path("payload").path("reason").asText()).isEqualTo("done");
    }

    @Test
    @DisplayName("publishExpired writes a fan.membership.expired envelope (validTo, no plan/cancel fields)")
    void expiredEnvelope() throws Exception {
        MembershipEventPublisher publisher = new MembershipEventPublisher(outboxWriter, mapper);
        Instant validTo = NOW.plusSeconds(100);
        publisher.publishExpired("m1", "fan-platform", "acc1", MembershipTier.MEMBERS_ONLY, validTo, NOW);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter).save(eq("membership"), eq("m1"),
                eq(MembershipEventPublisher.EVENT_EXPIRED), payloadCaptor.capture());

        JsonNode envelope = mapper.readTree(payloadCaptor.getValue());
        assertThat(envelope.path("eventType").asText()).isEqualTo("fan.membership.expired");
        assertThat(envelope.path("partitionKey").asText()).isEqualTo("m1");
        JsonNode payload = envelope.path("payload");
        assertThat(payload.path("tier").asText()).isEqualTo("MEMBERS_ONLY");
        assertThat(payload.path("validTo").asText()).isEqualTo(validTo.toString());
        assertThat(payload.has("planMonths")).isFalse();
        assertThat(payload.has("canceledAt")).isFalse();
    }
}
