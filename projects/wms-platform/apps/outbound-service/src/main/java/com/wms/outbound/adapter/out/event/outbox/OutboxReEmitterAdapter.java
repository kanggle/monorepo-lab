package com.wms.outbound.adapter.out.event.outbox;

import com.example.common.id.UuidV7;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wms.outbound.adapter.out.persistence.entity.OutboundOutboxEntity;
import com.wms.outbound.adapter.out.persistence.repository.OutboundOutboxRepository;
import com.wms.outbound.application.port.out.OutboxReEmitterPort;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saga sweeper outbox re-emitter (TASK-BE-050).
 *
 * <p>Locates the most-recent outbox row for the {@code (aggregateId, eventType)}
 * pair (saga sweeper's three event types: {@code outbound.picking.requested},
 * {@code outbound.picking.cancelled}, {@code outbound.shipping.confirmed})
 * and inserts a clone with:
 *
 * <ul>
 *   <li>fresh {@code eventId} (UUIDv7) — the row PK and the envelope's
 *       {@code eventId} field are kept in lock-step; consumer-side eventId
 *       dedupe sees a fresh value</li>
 *   <li>fresh {@code created_at}</li>
 *   <li>{@code status = PENDING}, {@code published_at = NULL}</li>
 *   <li>{@code retry_count = 0}</li>
 *   <li>identical payload aggregate / lines / partition key (so inventory
 *       still receives the same logical reservation request)</li>
 *   <li>{@code actorId} in the envelope rewritten to {@code system:saga-sweeper}
 *       per {@code outbound-events.md} § Saga Sweeper Re-emission</li>
 * </ul>
 *
 * <p>This adapter is invoked from the {@code SagaRecoveryHandler}'s
 * {@code @Transactional(REQUIRES_NEW)} method, so the cloned row commits
 * atomically with the saga's {@code re_emit_count} bump.
 */
@Component
public class OutboxReEmitterAdapter implements OutboxReEmitterPort {

    private static final Logger log = LoggerFactory.getLogger(OutboxReEmitterAdapter.class);

    private static final String STATUS_PENDING = "PENDING";
    private static final String SWEEPER_ACTOR = "system:saga-sweeper";

    private final OutboundOutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxReEmitterAdapter(OutboundOutboxRepository repository,
                                  ObjectMapper objectMapper,
                                  Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean reEmit(UUID aggregateId, String eventType) {
        Optional<OutboundOutboxEntity> original = repository
                .findLatestByAggregateAndType(aggregateId, eventType);
        if (original.isEmpty()) {
            log.warn("saga_sweeper_no_original_outbox aggregateId={} eventType={}",
                    aggregateId, eventType);
            return false;
        }
        OutboundOutboxEntity src = original.get();

        UUID newEventId = UuidV7.randomUuid();
        String rewrittenPayload = rewriteEnvelope(src.getPayload(), newEventId, clock);

        OutboundOutboxEntity clone = new OutboundOutboxEntity(
                newEventId,
                src.getAggregateType(),
                src.getAggregateUuid(),
                src.getEventType(),
                src.getEventVersion(),
                rewrittenPayload,
                src.getPartitionKey(),
                STATUS_PENDING,
                clock.instant());
        repository.save(clone);
        return true;
    }

    /**
     * Rewrite the envelope's {@code eventId} to match the new row PK so
     * consumer-side dedupe sees a fresh value, and stamp {@code actorId}
     * as {@code system:saga-sweeper} per outbound-events.md § Saga Sweeper
     * Re-emission. {@code occurredAt} is updated to the re-emission time
     * (DB clock equivalent — consumers treat it as informational, dedupe
     * is on eventId).
     */
    private String rewriteEnvelope(String originalJson, UUID newEventId, Clock clock) {
        try {
            JsonNode root = objectMapper.readTree(originalJson);
            if (!(root instanceof ObjectNode envelope)) {
                // Defensive: payload is not a JSON object — preserve as-is.
                return originalJson;
            }
            envelope.put("eventId", newEventId.toString());
            envelope.put("actorId", SWEEPER_ACTOR);
            envelope.put("occurredAt", clock.instant().toString());
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            log.warn("saga_sweeper_envelope_rewrite_failed eventId={} reason={} — emitting original payload",
                    newEventId, e.toString());
            return originalJson;
        }
    }
}
