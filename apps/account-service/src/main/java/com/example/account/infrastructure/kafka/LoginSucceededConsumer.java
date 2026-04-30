package com.example.account.infrastructure.kafka;

import com.example.account.application.service.UpdateLastLoginUseCase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Kafka consumer that updates {@code accounts.last_login_succeeded_at} when
 * auth-service publishes an {@code auth.login.succeeded} event.
 *
 * <p>Group ID {@code account-service} is independent from security-service's
 * own consumer of the same topic — both services receive every event.
 *
 * <p>Envelope contract: see specs/contracts/events/auth-events.md.
 *
 * <p>Failure handling:
 * <ul>
 *   <li>Blank {@code eventId} or {@code payload.accountId} — WARN + return
 *       (skip, never DLQ a malformed business id; auth-service is the source
 *       of truth, retrying won't fix it).</li>
 *   <li>Missing/unparseable {@code payload.timestamp} — fall back to
 *       {@code Instant.now()} with WARN log.</li>
 *   <li>JSON parse failure on the envelope — rethrow {@link RuntimeException}
 *       so Spring Kafka's {@code DefaultErrorHandler} retries / DLQs.</li>
 * </ul>
 *
 * <p>See TASK-BE-103.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginSucceededConsumer {

    private static final String TOPIC = "auth.login.succeeded";

    private final ObjectMapper objectMapper;
    private final UpdateLastLoginUseCase updateLastLoginUseCase;

    @KafkaListener(topics = TOPIC, groupId = "account-service")
    public void onMessage(ConsumerRecord<String, String> record) {
        try {
            JsonNode envelope = objectMapper.readTree(record.value());

            String eventId = envelope.path("eventId").asText("");
            if (eventId.isBlank()) {
                log.warn("auth.login.succeeded missing eventId — skipping. topic={}, key={}",
                        record.topic(), record.key());
                return;
            }

            JsonNode payload = envelope.path("payload");
            String accountId = payload.path("accountId").asText("");
            if (accountId.isBlank()) {
                log.warn("auth.login.succeeded missing payload.accountId — skipping. eventId={}",
                        eventId);
                return;
            }

            Instant occurredAt = parseTimestamp(payload, eventId);

            updateLastLoginUseCase.execute(eventId, accountId, occurredAt);
        } catch (JsonProcessingException e) {
            // Bubble up so Spring Kafka's error handler can retry / route to DLQ.
            log.error("Failed to deserialize auth.login.succeeded envelope from topic={}, key={}",
                    record.topic(), record.key(), e);
            throw new RuntimeException("auth.login.succeeded deserialization failed", e);
        }
    }

    /**
     * Parse the optional payload.timestamp; fall back to {@link Instant#now()}
     * on missing/invalid values to keep the consumer functioning. Auth-service
     * is the authority on event time but local now() is acceptable for
     * dormancy decisions on the account side.
     */
    private Instant parseTimestamp(JsonNode payload, String eventId) {
        String raw = payload.path("timestamp").asText("");
        if (raw.isBlank()) {
            log.warn("auth.login.succeeded missing payload.timestamp — using now(). eventId={}",
                    eventId);
            return Instant.now();
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            log.warn("auth.login.succeeded unparseable payload.timestamp={} — using now(). eventId={}",
                    raw, eventId);
            return Instant.now();
        }
    }
}
