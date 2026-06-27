package com.example.account.infrastructure.outbox;

import com.example.account.application.event.TenantDomainSubscriptionEventPublisher;
import com.example.account.infrastructure.persistence.AccountOutboxJpaEntity;
import com.example.account.infrastructure.persistence.AccountOutboxJpaRepository;
import com.example.common.id.UuidV7;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * {@link TenantDomainSubscriptionEventPublisher} implementation (TASK-BE-451 —
 * outbox v1 → v2).
 *
 * <p>Reproduces the EXACT v1 FLAT payload (the v1 method used
 * {@code BaseEventPublisher.saveEvent} which serialised the {@link HashMap}
 * payload as-is — NO canonical envelope) and persists an {@code account_outbox}
 * row (the SAME table as {@code account.*} events) in the caller's transaction.
 * The {@code AccountOutboxPublisher} relay forwards it to Kafka asynchronously.
 *
 * <p><b>Judgment call — already self-builds its own {@code eventId}.</b> The v1
 * payload puts a {@code UuidV7.randomString()} {@code eventId} as a top-level flat
 * field (this is NOT a 7-field envelope — there is no {@code eventType}/{@code source}/
 * {@code schemaVersion}/{@code payload} wrapper; the subscription fields sit at the
 * root alongside {@code eventId}). To stay byte-identical we keep that exact flat map
 * and reuse its {@code eventId} as the {@code account_outbox} row PK so the relay's
 * additive {@code eventId} Kafka header matches the payload. We do NOT double-wrap.
 */
@Component
public class OutboxTenantDomainSubscriptionEventPublisher
        implements TenantDomainSubscriptionEventPublisher {

    private static final String AGGREGATE_TYPE = "TenantDomainSubscription";

    private final AccountOutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxTenantDomainSubscriptionEventPublisher(AccountOutboxJpaRepository outboxRepository,
                                                        ObjectMapper objectMapper,
                                                        Clock clock) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void publishSubscriptionChanged(String tenantId, String domainKey,
                                           String previousStatus, String currentStatus,
                                           String reason, String actorType, String actorId,
                                           Instant occurredAt) {
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(domainKey)) {
            throw new IllegalArgumentException("tenantId and domainKey required");
        }
        // VERBATIM from the v1 TenantDomainSubscriptionEventPublisher (flat payload).
        Map<String, Object> payload = new HashMap<>();
        String eventId = UuidV7.randomString();
        payload.put("eventId", eventId);
        payload.put("tenantId", tenantId);
        payload.put("domainKey", domainKey);
        payload.put("previousStatus", previousStatus); // null on create (allowed)
        payload.put("currentStatus", currentStatus);
        payload.put("actorType", actorType != null ? actorType : "operator");
        payload.put("occurredAt", occurredAt.toString());
        if (actorId != null) {
            payload.put("actorId", actorId);
        }
        if (reason != null) {
            payload.put("reason", reason);
        }
        writeFlat(eventId, AGGREGATE_TYPE, tenantId + ":" + domainKey, EVENT_TYPE, payload);
    }

    /**
     * Serialise the FLAT payload AS-IS (byte-identical to the v1
     * {@code saveEvent} wire) and persist a pending {@code account_outbox} row. The
     * row PK reuses the payload's own {@code eventId}; {@code partition_key =
     * aggregateId} (the v1 Kafka key).
     */
    private void writeFlat(String eventId, String aggregateType, String aggregateId,
                           String eventType, Map<String, Object> payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "failed to serialise " + eventType + " outbox payload", e);
        }
        outboxRepository.save(AccountOutboxJpaEntity.create(
                UUID.fromString(eventId), aggregateType, aggregateId, eventType, json,
                aggregateId, Instant.now(clock)));
    }
}
