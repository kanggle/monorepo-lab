package com.example.fanplatform.membership.infrastructure.outbox;

import com.example.common.id.UuidV7;
import com.example.fanplatform.membership.application.event.MembershipEventPublisher;
import com.example.fanplatform.membership.domain.membership.MembershipTier;
import com.example.fanplatform.membership.infrastructure.jpa.MembershipOutboxJpaEntity;
import com.example.fanplatform.membership.infrastructure.jpa.MembershipOutboxJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * membership-service outbox write path (TASK-FAN-BE-020, outbox v2).
 *
 * <p>Persists one {@link MembershipOutboxJpaEntity} ({@code membership_outbox}
 * table) per domain event inside the caller's transaction, so the business
 * mutation and the outbox row commit atomically. {@code MembershipOutboxPublisher}
 * drains the table to Kafka.
 *
 * <p>Replaces the v1 path ({@code MembershipEventPublisher extends
 * BaseEventPublisher} + lib {@code OutboxWriter} → {@code OutboxJpaEntity},
 * server-assigned {@code BIGSERIAL}, {@code status} string). <b>Wire is preserved
 * exactly</b>:
 * <ul>
 *   <li>The Kafka record <b>value</b> is the canonical 7-field envelope JSON
 *       ({@code eventId, eventType, source, occurredAt, schemaVersion=1,
 *       partitionKey, payload}) built here in the same field order the lib
 *       {@code BaseEventPublisher.writeEvent} used — byte-identical.</li>
 *   <li>Per-event {@code payload} maps are copied verbatim from the v1 publisher
 *       (same keys, same order, same value formatting).</li>
 *   <li>{@code aggregate_type}/{@code aggregate_id}/{@code event_type} match the
 *       v1 {@code writeEvent(...)} arguments. {@code aggregate_id} becomes the
 *       Kafka record key (partition_key is left null → the publisher falls back to
 *       aggregateId), preserving the v1 {@code kafkaTemplate.send(topic,
 *       aggregateId, payload)} key.</li>
 *   <li>{@code eventId} is a fresh UUIDv7 (as v1's {@code UuidV7.randomString()}),
 *       reused as both the envelope {@code eventId} and the row PK, so the Kafka
 *       header {@code eventId} matches the payload {@code eventId}.</li>
 * </ul>
 */
@Component
public class OutboxMembershipEventPublisher implements MembershipEventPublisher {

    private static final String AGGREGATE_TYPE = "membership";
    private static final String SOURCE = "fan-platform-membership-service";
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
    public void publishActivated(String membershipId, String tenantId, String accountId,
                                 MembershipTier tier, int planMonths,
                                 Instant validFrom, Instant validTo, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("membershipId", membershipId);
        payload.put("tenantId", tenantId);
        payload.put("accountId", accountId);
        payload.put("tier", tier.name());
        payload.put("planMonths", planMonths);
        payload.put("validFrom", validFrom.toString());
        payload.put("validTo", validTo.toString());
        payload.put("occurredAt", occurredAt.toString());
        writeEvent(AGGREGATE_TYPE, membershipId, EVENT_ACTIVATED, payload);
    }

    @Override
    public void publishCanceled(String membershipId, String tenantId, String accountId,
                                MembershipTier tier, String reason,
                                Instant canceledAt, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("membershipId", membershipId);
        payload.put("tenantId", tenantId);
        payload.put("accountId", accountId);
        payload.put("tier", tier.name());
        payload.put("reason", reason);
        payload.put("canceledAt", canceledAt.toString());
        payload.put("occurredAt", occurredAt.toString());
        writeEvent(AGGREGATE_TYPE, membershipId, EVENT_CANCELED, payload);
    }

    @Override
    public void publishExpired(String membershipId, String tenantId, String accountId,
                               MembershipTier tier, Instant validTo, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("membershipId", membershipId);
        payload.put("tenantId", tenantId);
        payload.put("accountId", accountId);
        payload.put("tier", tier.name());
        payload.put("validTo", validTo.toString());
        payload.put("occurredAt", occurredAt.toString());
        writeEvent(AGGREGATE_TYPE, membershipId, EVENT_EXPIRED, payload);
    }

    /**
     * Wrap the payload in the canonical 7-field envelope (v1 shape, same field
     * order as the lib {@code BaseEventPublisher.writeEvent}), serialise to JSON,
     * and persist a pending {@code membership_outbox} row in the caller's
     * transaction. The fresh UUIDv7 doubles as the envelope {@code eventId} and
     * the row PK.
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
            throw new IllegalStateException("failed to serialise " + eventType + " outbox envelope", e);
        }

        outboxRepository.save(new MembershipOutboxJpaEntity(
                eventId, eventType, aggregateType, aggregateId,
                null, // partition_key: publisher falls back to aggregateId (the v1 Kafka key)
                json, occurredAt));
    }
}
