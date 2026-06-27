package com.example.membership.infrastructure.outbox;

import com.example.common.id.UuidV7;
import com.example.membership.application.event.MembershipEventPublisher;
import com.example.membership.domain.event.MembershipDomainEvent;
import com.example.membership.domain.subscription.Subscription;
import com.example.membership.infrastructure.persistence.MembershipOutboxJpaEntity;
import com.example.membership.infrastructure.persistence.MembershipOutboxJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link MembershipEventPublisher} implementation (TASK-BE-454 — outbox v1 → v2).
 *
 * <p>Builds the canonical event envelope and persists a {@code membership_outbox}
 * row in the caller's transaction (the {@code AbstractOutboxPublisher} /
 * {@code OutboxRow} path — ADR-MONO-004 § 5, mirroring the in-worktree auth-service's
 * {@code OutboxAuthEventPublisher} + finance account-service's
 * {@code OutboxAccountEventPublisher}). The {@code MembershipOutboxPublisher} relay
 * forwards the row to Kafka asynchronously; downstream consumers dedupe on the
 * envelope {@code eventId} (at-least-once).
 *
 * <p><b>Wire-shape preserved.</b> The envelope is the EXACT 7-field shape the
 * previous {@code BaseEventPublisher.writeEvent} path emitted —
 * {@code {eventId, eventType, source, occurredAt, schemaVersion, partitionKey,
 * payload}}, {@code source = "membership-service"}. The payload + eventType are
 * produced VERBATIM by the domain factories
 * ({@link Subscription#buildActivatedEvent()} / {@code buildExpiredEvent()} /
 * {@code buildCancelledEvent()}) — exactly as the v1 publisher used them — so
 * consumers are unaffected. The only change: the envelope {@code eventId} now equals
 * the {@code membership_outbox} PK (both UUIDv7) so the Kafka {@code eventId} header
 * matches the payload.
 */
@Component
public class OutboxMembershipEventPublisher implements MembershipEventPublisher {

    private static final String AGGREGATE_TYPE = "membership";
    private static final String SOURCE = "membership-service";
    private static final int SCHEMA_VERSION = 1;

    private final MembershipOutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxMembershipEventPublisher(MembershipOutboxJpaRepository outboxRepository,
                                          ObjectMapper objectMapper,
                                          Clock clock) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void publishActivated(Subscription s) {
        write(s.getAccountId(), s.buildActivatedEvent());
    }

    @Override
    public void publishExpired(Subscription s) {
        write(s.getAccountId(), s.buildExpiredEvent());
    }

    @Override
    public void publishCancelled(Subscription s) {
        write(s.getAccountId(), s.buildCancelledEvent());
    }

    private void write(String aggregateId, MembershipDomainEvent event) {
        writeEvent(AGGREGATE_TYPE, aggregateId, event.eventType(), event.payload());
    }

    /**
     * Wrap {@code payload} in the canonical 7-field envelope (preserved from the
     * v1 {@code BaseEventPublisher.writeEvent} path), serialise it, and persist a
     * pending {@code membership_outbox} row in the caller's transaction. The generated
     * {@link UuidV7} doubles as the envelope {@code eventId} and the row PK;
     * {@code partition_key = aggregateId} (the v1 Kafka key).
     */
    private void writeEvent(String aggregateType, String aggregateId,
                            String eventType, Map<String, Object> payload) {
        UUID eventId = UuidV7.randomUuid();
        Instant occurredAt = Instant.now(clock);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", eventType);
        envelope.put("source", SOURCE);
        envelope.put("occurredAt", occurredAt.toString());
        envelope.put("schemaVersion", SCHEMA_VERSION);
        envelope.put("partitionKey", aggregateId);
        envelope.put("payload", payload);

        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "failed to serialise " + eventType + " outbox envelope", e);
        }

        outboxRepository.save(MembershipOutboxJpaEntity.create(
                eventId, aggregateType, aggregateId, eventType,
                json, aggregateId, occurredAt));
    }
}
