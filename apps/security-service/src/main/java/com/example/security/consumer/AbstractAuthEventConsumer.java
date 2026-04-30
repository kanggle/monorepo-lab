package com.example.security.consumer;

import com.example.security.application.DetectSuspiciousActivityUseCase;
import com.example.security.application.RecordLoginHistoryUseCase;
import com.example.security.consumer.handler.EventDedupService;
import com.example.security.domain.detection.EvaluationContext;
import com.example.security.domain.history.LoginHistoryEntry;
import com.example.security.domain.history.LoginOutcome;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.time.Instant;

/**
 * Base class for auth event consumers. Handles dedup, mapping, delegation to
 * {@link RecordLoginHistoryUseCase}, and finally dispatches to
 * {@link DetectSuspiciousActivityUseCase} for rule evaluation.
 *
 * <p>Detection runs only after the login-history record is committed; if it is
 * a duplicate (Redis or DB), detection is skipped too — the previous delivery
 * already evaluated the rules.</p>
 *
 * <p>Trace propagation is handled by {@code EventContextRecordInterceptor} from
 * libs/java-observability (auto-configured). No manual MDC manipulation here.</p>
 */
@Slf4j
public abstract class AbstractAuthEventConsumer {

    protected final ObjectMapper objectMapper;
    protected final EventDedupService dedupService;
    protected final RecordLoginHistoryUseCase recordLoginHistoryUseCase;
    protected final DetectSuspiciousActivityUseCase detectUseCase;

    protected AbstractAuthEventConsumer(ObjectMapper objectMapper,
                                         EventDedupService dedupService,
                                         RecordLoginHistoryUseCase recordLoginHistoryUseCase,
                                         DetectSuspiciousActivityUseCase detectUseCase) {
        this.objectMapper = objectMapper;
        this.dedupService = dedupService;
        this.recordLoginHistoryUseCase = recordLoginHistoryUseCase;
        this.detectUseCase = detectUseCase;
    }

    protected void processEvent(ConsumerRecord<String, String> record, LoginOutcome defaultOutcome) {
        try {
            JsonNode envelope = objectMapper.readTree(record.value());
            String eventId = envelope.path("eventId").asText();
            String eventType = envelope.path("eventType").asText();

            if (eventId.isBlank()) {
                log.warn("Event missing eventId, skipping. topic={}", record.topic());
                return;
            }

            if (dedupService.isDuplicate(eventId)) {
                log.info("Duplicate event skipped (Redis fast-path): eventId={}, topic={}", eventId, record.topic());
                return;
            }

            LoginOutcome outcome = resolveOutcome(envelope, defaultOutcome);
            LoginHistoryEntry entry = AuthEventMapper.toLoginHistoryEntry(envelope, outcome);

            boolean processed = recordLoginHistoryUseCase.execute(entry, eventType);

            if (processed) {
                dedupService.markProcessedInRedis(eventId);
                log.info("Processed event: eventId={}, topic={}, outcome={}", eventId, record.topic(), outcome);
                // Detection runs outside the history-insert transaction so that an
                // HTTP call or Redis glitch does not fail the consumer.
                dispatchDetection(envelope, eventType);
            } else {
                log.info("Duplicate event skipped (DB constraint): eventId={}, topic={}", eventId, record.topic());
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize event from topic={}", record.topic(), e);
            throw new RuntimeException("Deserialization failed", e);
        }
    }

    protected LoginOutcome resolveOutcome(JsonNode envelope, LoginOutcome defaultOutcome) {
        return defaultOutcome;
    }

    private void dispatchDetection(JsonNode envelope, String eventType) {
        if (detectUseCase == null) {
            return;
        }
        try {
            EvaluationContext ctx = AuthEventMapper.toEvaluationContext(envelope, eventType);
            if (ctx.hasAccount()) {
                detectUseCase.detect(ctx);
            }
        } catch (RuntimeException e) {
            log.warn("Detection pipeline threw for eventId={}; history remains recorded",
                    envelope.path("eventId").asText(), e);
        }
    }

    /**
     * Convenience helper — kept for compatibility with callers that do not need
     * the detection use-case (not used internally).
     */
    @SuppressWarnings("unused")
    private static Instant nowIfNull(Instant t) {
        return t == null ? Instant.now() : t;
    }
}
