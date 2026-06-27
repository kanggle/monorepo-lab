package com.example.admin.infrastructure.outbox;

import com.example.admin.application.event.AdminEventPublisher;
import com.example.admin.infrastructure.persistence.AdminOutboxJpaEntity;
import com.example.admin.infrastructure.persistence.AdminOutboxJpaRepository;
import com.example.common.id.UuidV7;
import com.example.security.pii.PiiMaskingUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link AdminEventPublisher} implementation (TASK-BE-452 — outbox v1 → v2).
 *
 * <p>Builds the v1 FLAT canonical-action payload and persists an
 * {@code admin_outbox} row in the caller's transaction (the
 * {@code AbstractOutboxPublisher} / {@code OutboxRow} path — ADR-MONO-004 § 5). The
 * {@code AdminOutboxPublisher} relay forwards the row to Kafka asynchronously.
 *
 * <p><b>Wire-shape preserved (FLAT — no double-wrap).</b> admin-service's v1
 * {@code AdminEventPublisher} used {@code BaseEventPublisher.saveEvent} which
 * serialised the action payload map AS-IS (this is the canonical envelope declared in
 * {@code data-model.md}: {@code eventId + occurredAt + actor + action + target +
 * outcome + reason} — at the JSON root, NOT wrapped under a 7-field
 * {@code {eventType,source,schemaVersion,payload}} envelope). This adapter reproduces
 * the EXACT v1 bytes: same {@link LinkedHashMap} field order, same centralised PII
 * {@code displayHint} masking via {@link PiiMaskingUtils}, serialised directly into
 * the row {@code payload} column.
 *
 * <p><b>Row PK / eventId.</b> The v1 payload already self-mints a top-level
 * {@code eventId} (UUIDv7); that value is reused as the {@code admin_outbox} row PK so
 * the relay's additive {@code eventId} Kafka header matches the payload.
 */
@Component
public class OutboxAdminEventPublisher implements AdminEventPublisher {

    private static final String AGGREGATE_TYPE = "AdminAction";
    private static final String EVENT_TYPE = "admin.action.performed";

    private final AdminOutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxAdminEventPublisher(AdminOutboxJpaRepository outboxRepository,
                                     ObjectMapper objectMapper,
                                     Clock clock) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void publishAdminActionPerformed(Envelope env) {
        // VERBATIM from the v1 AdminEventPublisher (flat canonical-action payload).
        Map<String, Object> payload = new LinkedHashMap<>();
        // UUID v7 carries a 48-bit ms timestamp in its MSBs; improves outbox
        // index locality and natural time ordering (TASK-BE-028c).
        String eventId = UuidV7.randomString();
        payload.put("eventId", eventId);
        payload.put("occurredAt", env.occurredAt() == null ? null : env.occurredAt().toString());

        Map<String, Object> actor = new LinkedHashMap<>();
        actor.put("type", "operator");
        actor.put("id", env.operatorId());
        actor.put("sessionId", env.sessionId());
        payload.put("actor", actor);

        Map<String, Object> action = new LinkedHashMap<>();
        action.put("permission", env.permission());
        action.put("endpoint", env.endpoint());
        action.put("method", env.method());
        payload.put("action", action);

        Map<String, Object> target = new LinkedHashMap<>();
        target.put("type", env.targetType());
        target.put("id", env.targetId());
        target.put("displayHint", displayHintFor(env.targetType(), env.targetId()));
        payload.put("target", target);

        payload.put("outcome", env.outcome() == null ? null : env.outcome().name());
        payload.put("reason", env.reason());

        String aggregateId = env.targetId() != null ? env.targetId() : "-";
        writeFlat(eventId, AGGREGATE_TYPE, aggregateId, EVENT_TYPE, payload);
    }

    private static String displayHintFor(String targetType, String targetId) {
        if (targetType == null || targetId == null) return null;
        if (!"ACCOUNT".equals(targetType)) return null;
        if (targetId.contains("@")) return PiiMaskingUtils.maskEmail(targetId);
        // UUID / numeric account ids are not PII; do not leak them into displayHint.
        return null;
    }

    /**
     * Serialise the FLAT payload AS-IS (byte-identical to the v1
     * {@code saveEvent} wire) and persist a pending {@code admin_outbox} row. The
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
        outboxRepository.save(AdminOutboxJpaEntity.create(
                UUID.fromString(eventId), aggregateType, aggregateId, eventType, json,
                aggregateId, Instant.now(clock)));
    }
}
