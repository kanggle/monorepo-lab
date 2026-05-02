package com.example.security.consumer;

import com.example.security.application.pii.PiiMaskingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code account.deleted} events and masks PII in security-service tables
 * when {@code anonymized=true} (TASK-BE-258).
 *
 * <p>Filter logic:
 * <ul>
 *   <li>{@code anonymized=false} (grace-period entry) — skipped silently.</li>
 *   <li>{@code anonymized=true} (grace-period expired) — PII masking triggered.</li>
 * </ul>
 *
 * <p>Idempotency: {@link PiiMaskingService#maskPii} checks {@code pii_masking_log.event_id}
 * before executing any UPDATE. A duplicate delivery results in a no-op.
 *
 * <p>DLQ routing: any {@link RuntimeException} propagates to the shared
 * {@code DefaultErrorHandler} (configured in {@code KafkaConsumerConfig}), which
 * applies exponential back-off (3 retries) and then routes to
 * {@code account.deleted.dlq}.
 *
 * <p>Tenant validation: events without a {@code tenant_id} are rejected immediately
 * via {@link MissingTenantIdException} (non-retryable → DLQ).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountDeletedAnonymizedConsumer {

    private static final String TOPIC = "account.deleted";
    private static final String GROUP_ID = "security-service";

    private final ObjectMapper objectMapper;
    private final PiiMaskingService piiMaskingService;

    @KafkaListener(topics = TOPIC, groupId = GROUP_ID)
    public void onMessage(ConsumerRecord<String, String> record) {
        try {
            JsonNode root = objectMapper.readTree(record.value());

            // Support both envelope-wrapped and flat payload forms (same as AccountLockedConsumer).
            JsonNode payload = root.has("payload") && root.path("payload").isObject()
                    ? root.path("payload")
                    : root;

            String eventId = firstNonBlank(
                    root.path("eventId").asText(null),
                    payload.path("eventId").asText(null));
            if (eventId == null || eventId.isBlank()) {
                throw new IllegalArgumentException("account.deleted payload missing eventId");
            }

            // TASK-BE-258: only process anonymized=true events.
            boolean anonymized = payload.path("anonymized").asBoolean(false);
            if (!anonymized) {
                log.debug("account.deleted skipped (anonymized=false): eventId={}", eventId);
                return;
            }

            String tenantId = firstNonBlank(
                    root.path("tenantId").asText(null),
                    payload.path("tenantId").asText(null));
            if (tenantId == null || tenantId.isBlank()) {
                throw new MissingTenantIdException(eventId, TOPIC);
            }

            String accountId = firstNonBlank(
                    payload.path("accountId").asText(null),
                    root.path("accountId").asText(null));
            if (accountId == null || accountId.isBlank()) {
                throw new IllegalArgumentException(
                        "account.deleted(anonymized=true) payload missing accountId: eventId=" + eventId);
            }

            log.info("Processing account.deleted(anonymized=true): eventId={}, tenantId={}, accountId={}",
                    eventId, tenantId, accountId);

            boolean masked = piiMaskingService.maskPii(eventId, tenantId, accountId);

            if (masked) {
                log.info("PII masking complete: eventId={}, tenantId={}, accountId={}",
                        eventId, tenantId, accountId);
            } else {
                log.info("PII masking skipped (already processed): eventId={}", eventId);
            }

        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize account.deleted event: topic={}, key={}",
                    record.topic(), record.key(), e);
            throw new RuntimeException("Deserialization failed", e);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank() && !"null".equals(v)) return v;
        }
        return null;
    }
}
