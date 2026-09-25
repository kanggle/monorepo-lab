package com.example.auth.infrastructure.messaging;

import com.example.auth.application.RevokeSessionsOnAccountLockedUseCase;
import com.example.auth.application.command.RevokeSessionsOnAccountLockedCommand;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * TASK-BE-601 — consumes {@code account.locked} (account-service) and revokes the locked
 * account's sessions through {@link RevokeSessionsOnAccountLockedUseCase}.
 *
 * <p>Inbound Kafka adapter: it validates the envelope and hands a command to the
 * application layer (the same shape as account-service's {@code LoginSucceededConsumer}).
 *
 * <p><b>Envelope.</b> account-service publishes {@code account.locked} FLAT — the fields sit
 * at the root, no {@code payload} wrapper (account-events.md, TASK-BE-422/451). A nested
 * {@code payload} object is accepted too, as security-service's consumer does. Rules:
 * <ul>
 *   <li>{@code eventId} — required, a UUID (it is the dedupe key); otherwise
 *       {@link InvalidEventPayloadException}.</li>
 *   <li>{@code accountId} — required; otherwise {@link InvalidEventPayloadException}.</li>
 *   <li>{@code tenantId} — required; otherwise {@link MissingTenantIdException} (the
 *       security-service rule, TASK-BE-248 Phase 2b).</li>
 *   <li>{@code reasonCode} / {@code lockedAt} — optional, for the log line and the
 *       propagation-lag metric.</li>
 * </ul>
 * Both exceptions are non-retryable in {@code KafkaConsumerConfig}: the record goes
 * straight to {@code account.locked.dlq}. Anything the use case throws is retried, then
 * dead-lettered — never swallowed here.
 *
 * <p><b>Group.</b> {@code auth-service-account-locked}, independent of security-service's
 * group, so both services receive every lock.
 *
 * <p><b>Version.</b> An {@code eventVersion} / {@code schemaVersion} field, when present, must
 * be one this consumer knows ({@link #SUPPORTED_VERSIONS}); otherwise the record is
 * dead-lettered. Unknown fields are ignored (forward compatibility).
 */
@Slf4j
@Component
public class AccountLockedConsumer {

    static final String TOPIC = "account.locked";
    /** {@code <service>-<purpose>} (platform/service-types/event-consumer.md § Subscription Ownership). */
    static final String GROUP_ID = "auth-service-account-locked";
    /**
     * account-events.md declares {@code account.locked} schema version 2 (TASK-BE-228 added
     * {@code tenantId}); version 1 differs only by lacking it, which the tenant rule already
     * handles. The flat wire carries no version field today, so an absent one means "current".
     */
    static final java.util.Set<Integer> SUPPORTED_VERSIONS = java.util.Set.of(1, 2);

    static final String OUTCOME_METRIC = "auth.account_locked.sessions";
    static final String LAG_METRIC = "auth.account_locked.propagation.lag";

    private final ObjectMapper objectMapper;
    private final RevokeSessionsOnAccountLockedUseCase useCase;
    private final Counter revokedCounter;
    private final Counter duplicateCounter;
    private final Timer lagTimer;

    public AccountLockedConsumer(ObjectMapper objectMapper,
                                 RevokeSessionsOnAccountLockedUseCase useCase,
                                 MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.useCase = useCase;
        this.revokedCounter = Counter.builder(OUTCOME_METRIC)
                .description("account.locked events processed, by outcome")
                .tag("outcome", "revoked")
                .register(meterRegistry);
        this.duplicateCounter = Counter.builder(OUTCOME_METRIC)
                .description("account.locked events processed, by outcome")
                .tag("outcome", "duplicate")
                .register(meterRegistry);
        this.lagTimer = Timer.builder(LAG_METRIC)
                .description("Time from the lock (lockedAt) to its sessions being revoked — "
                        + "the window in which a pre-lock session can still refresh")
                .register(meterRegistry);
    }

    /**
     * The group id is overridable ({@code auth.kafka.account-locked.group-id}) only so an
     * integration test can isolate its listener from other cached test contexts; every
     * deployed profile uses the default {@value #GROUP_ID}.
     */
    @KafkaListener(topics = TOPIC, groupId = "${auth.kafka.account-locked.group-id:" + GROUP_ID + "}")
    public void onMessage(ConsumerRecord<String, String> record) {
        RevokeSessionsOnAccountLockedCommand command = toCommand(record);
        RevokeSessionsOnAccountLockedUseCase.Result result = useCase.execute(command);
        if (result.outcome() == RevokeSessionsOnAccountLockedUseCase.Outcome.REVOKED) {
            revokedCounter.increment();
            if (result.propagationLag() != null && !result.propagationLag().isNegative()) {
                lagTimer.record(result.propagationLag());
            }
        } else {
            duplicateCounter.increment();
        }
    }

    RevokeSessionsOnAccountLockedCommand toCommand(ConsumerRecord<String, String> record) {
        JsonNode root;
        try {
            root = objectMapper.readTree(record.value());
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new InvalidEventPayloadException(
                    "account.locked: unparseable record at " + record.topic() + "-"
                            + record.partition() + "@" + record.offset(), e);
        }
        if (root == null || !root.isObject()) {
            throw new InvalidEventPayloadException("account.locked: record is not a JSON object");
        }
        JsonNode payload = root.path("payload").isObject() ? root.path("payload") : root;

        // Schema-version branching (event-consumer.md § Schema Versioning): an unsupported
        // version is dead-lettered, never guessed at.
        String rawVersion = firstText(root, payload, "eventVersion");
        if (rawVersion == null) {
            rawVersion = firstText(root, payload, "schemaVersion");
        }
        if (rawVersion != null && !isSupportedVersion(rawVersion)) {
            throw new InvalidEventPayloadException("account.locked: unsupported event version " + rawVersion);
        }

        String rawEventId = firstText(root, payload, "eventId");
        if (rawEventId == null) {
            throw new InvalidEventPayloadException("account.locked: eventId missing");
        }
        UUID eventId;
        try {
            eventId = UUID.fromString(rawEventId);
        } catch (IllegalArgumentException e) {
            throw new InvalidEventPayloadException("account.locked: eventId is not a UUID: " + rawEventId, e);
        }

        String accountId = firstText(payload, root, "accountId");
        if (accountId == null) {
            throw new InvalidEventPayloadException("account.locked: accountId missing, eventId=" + eventId);
        }

        String tenantId = firstText(root, payload, "tenantId");
        if (tenantId == null) {
            throw new MissingTenantIdException(eventId.toString(), TOPIC);
        }

        String reasonCode = firstText(payload, root, "reasonCode");
        Instant lockedAt = parseInstant(firstText(payload, root, "lockedAt"), eventId);
        return new RevokeSessionsOnAccountLockedCommand(eventId, accountId, tenantId, reasonCode, lockedAt);
    }

    private static boolean isSupportedVersion(String raw) {
        try {
            return SUPPORTED_VERSIONS.contains(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String firstText(JsonNode first, JsonNode second, String field) {
        String value = text(first, field);
        return value != null ? value : text(second, field);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isValueNode()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }

    /** {@code lockedAt} only feeds the lag metric — an absent or bad value is not a reason to DLQ. */
    private static Instant parseInstant(String raw, UUID eventId) {
        if (raw == null) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            log.warn("account.locked: unparseable lockedAt='{}' — lag not recorded. eventId={}", raw, eventId);
            return null;
        }
    }
}
