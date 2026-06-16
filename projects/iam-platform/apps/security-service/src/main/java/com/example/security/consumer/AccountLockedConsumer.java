package com.example.security.consumer;

import com.example.security.application.RecordAccountLockHistoryUseCase;
import com.example.security.domain.history.AccountLockHistory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Consumes the {@code account.locked} event published by account-service and appends an
 * immutable row to {@code account_lock_history} for downstream security analysis
 * (TASK-BE-041b).
 *
 * <p>This consumer is a thin Kafka transport adapter: it deserializes the envelope,
 * maps the payload to a domain {@link AccountLockHistory}, and delegates persistence +
 * idempotency to {@link RecordAccountLockHistoryUseCase} (declared dependency rule
 * {@code consumer → application → domain} — it does not touch
 * {@code infrastructure/persistence} directly).
 *
 * <p>Idempotency: handled by the use case via the {@code event_id} unique constraint. A
 * duplicate replay is swallowed there, so {@link #onMessage} only propagates genuine
 * failures — deserialization errors and invalid payloads (missing eventId/accountId/
 * reason/tenantId) bubble up as {@link RuntimeException} so the shared
 * {@code DefaultErrorHandler} routes them to {@code account.locked.dlq}
 * (platform/event-driven-policy.md).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountLockedConsumer {

    private static final String TOPIC = "account.locked";
    private static final String SYSTEM_LOCKED_BY = "00000000-0000-0000-0000-000000000000";

    private final ObjectMapper objectMapper;
    private final RecordAccountLockHistoryUseCase recordAccountLockHistoryUseCase;

    @KafkaListener(topics = TOPIC)
    public void onMessage(ConsumerRecord<String, String> record) {
        try {
            JsonNode root = objectMapper.readTree(record.value());
            JsonNode payload = root.has("payload") && root.path("payload").isObject()
                    ? root.path("payload")
                    : root;

            String eventId = ConsumerUtils.firstNonBlank(root.path("eventId").asText(null),
                    payload.path("eventId").asText(null));
            if (eventId == null || eventId.isBlank()) {
                // TASK-BE-041b-fix Critical 1: the account-events contract now mandates
                // eventId on the account.locked payload. Messages without it are treated
                // as invalid and routed to account.locked.dlq via DefaultErrorHandler
                // rather than given a synthetic UUID (which would break idempotency on
                // Kafka at-least-once redelivery by producing different event_id values
                // for the same logical message).
                throw new IllegalArgumentException("account.locked payload missing eventId");
            }

            String accountId = requireText(payload, "accountId", "account.locked payload missing accountId");
            String reason = ConsumerUtils.firstNonBlank(
                    payload.path("reason").asText(null),
                    payload.path("reasonCode").asText(null));
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("account.locked payload missing reason/reasonCode");
            }

            String actorType = payload.path("actorType").asText(null);
            String source = resolveSource(payload, actorType);
            String lockedBy = resolveLockedBy(payload, actorType);

            Instant occurredAt = resolveOccurredAt(root, payload);

            // TASK-BE-248 Phase 2b: tenant_id is now required on all account.locked events.
            // The lenient fallback to DEFAULT_TENANT_ID is removed — upstream publishers
            // (account-service AccountEventPublisher) always include tenantId since Phase 2b.
            // Messages without tenant_id are routed to account.locked.dlq via MissingTenantIdException.
            String tenantId = ConsumerUtils.firstNonBlank(
                    root.path("tenantId").asText(null),
                    payload.path("tenantId").asText(null));
            if (tenantId == null || tenantId.isBlank()) {
                throw new MissingTenantIdException(eventId, "account.locked");
            }

            AccountLockHistory history = new AccountLockHistory(
                    tenantId, eventId, accountId, truncate(reason, 255), lockedBy, source, occurredAt);

            recordAccountLockHistoryUseCase.execute(history);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize account.locked event: topic={}, key={}",
                    record.topic(), record.key(), e);
            throw new RuntimeException("Deserialization failed", e);
        }
    }

    private static String resolveSource(JsonNode payload, String actorType) {
        String explicit = payload.path("source").asText(null);
        if (explicit != null && !explicit.isBlank()) {
            return truncate(explicit, 32);
        }
        if ("operator".equalsIgnoreCase(actorType)) return "admin";
        if ("system".equalsIgnoreCase(actorType)) return "system";
        if ("user".equalsIgnoreCase(actorType)) return "user";
        return "unknown";
    }

    private static String resolveLockedBy(JsonNode payload, String actorType) {
        String lockedBy = ConsumerUtils.firstNonBlank(
                payload.path("lockedBy").asText(null),
                payload.path("actorId").asText(null));
        if (lockedBy == null || lockedBy.isBlank()) {
            // 041b edge case: actor id missing (system-driven lock). Use the
            // all-zero UUID as a conventional "system" actor so the NOT NULL
            // constraint holds without rejecting the audit row.
            return SYSTEM_LOCKED_BY;
        }
        return lockedBy;
    }

    private static Instant resolveOccurredAt(JsonNode root, JsonNode payload) {
        String ts = ConsumerUtils.firstNonBlank(
                payload.path("lockedAt").asText(null),
                payload.path("occurredAt").asText(null),
                root.path("occurredAt").asText(null));
        if (ts == null || ts.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(ts);
        } catch (RuntimeException e) {
            log.warn("account.locked invalid timestamp '{}'; using now()", ts);
            return Instant.now();
        }
    }

    private static String requireText(JsonNode node, String field, String msg) {
        String v = node.path(field).asText(null);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException(msg);
        }
        return v;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
