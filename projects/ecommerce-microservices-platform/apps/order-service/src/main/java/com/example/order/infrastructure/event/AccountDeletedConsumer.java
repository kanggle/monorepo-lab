package com.example.order.infrastructure.event;

import com.example.order.application.service.OrderPiiAnonymizationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes IAM {@code account.deleted} → cascades the deletion into order-held PII
 * (ADR-MONO-037 P3-B — the deferred follow-up now implemented), aligning order-service
 * to the standing IAM consumer obligation (account-events.md TASK-BE-258 +
 * consumer-integration-guide § GDPR downstream) for the order store.
 *
 * <p>Branches on the event's own {@code anonymized} flag:
 * <ul>
 *   <li>{@code anonymized=true} (post-grace) →
 *       {@link OrderPiiAnonymizationService#anonymizeOrdersForAccount} (shipping-address
 *       PII masked on every historical order; {@code orderId}/{@code userId} FK +
 *       business data preserved).</li>
 *   <li>{@code anonymized=false}/null (grace entry) → no-op. Active-order cancellation is
 *       already driven by the {@code user.user.withdrawn} reaction
 *       ({@link UserWithdrawnEventConsumer}); this consumer does not duplicate it.</li>
 * </ul>
 *
 * <p>Idempotent + fail-soft (ADR-MONO-037 P5). The wire is FLAT (TASK-BE-422): fields are
 * read from the JSON root, not a nested {@code payload}. The flat {@code account.deleted}
 * payload carries NO {@code eventId}, so dedup via the shared {@link EventDeduplicationChecker}
 * is re-keyed to a stable composite {@code accountId + ":" + (anonymized ? "anon" : "grace")}
 * — this lets the post-grace anonymize phase dedup independently of the grace entry while
 * remaining stable across re-delivery of the same phase. A missing {@code accountId} is
 * logged and skipped; a malformed message routes to {@code account.deleted.dlq} (the
 * {@code DefaultErrorHandler} marks {@link JsonProcessingException} /
 * {@link IllegalArgumentException} non-retryable). The lifecycle partition never blocks on
 * a poison message. It does NOT self-schedule on {@code gracePeriodEndsAt} — the producer
 * re-emits at grace end.
 */
@Slf4j
@Component
@Profile("!standalone")
@RequiredArgsConstructor
public class AccountDeletedConsumer {

    private static final String EVENT_TYPE = "AccountDeleted";

    private final OrderPiiAnonymizationService orderPiiAnonymizationService;
    private final EventDeduplicationChecker eventDeduplicationChecker;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(topics = "account.deleted", groupId = "order-service-account-sync")
    public void onMessage(@Payload String payload) {
        handle(deserialize(payload));
    }

    private AccountDeletedEvent deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, AccountDeletedEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize account.deleted event", e);
        }
    }

    void handle(AccountDeletedEvent event) {
        if (event.accountId() == null) {
            log.warn("account.deleted event missing accountId, skipping.");
            return;
        }

        String userId = event.accountId().toString();
        boolean anonymized = Boolean.TRUE.equals(event.anonymized());

        // The flat account.deleted payload carries no eventId (TASK-BE-422), so dedup keys
        // off a stable accountId+phase composite — the anonymize phase dedups independently
        // of the grace entry, and re-delivery of the same phase is a no-op.
        String dedupKey = userId + ":" + (anonymized ? "anon" : "grace");
        if (eventDeduplicationChecker.isDuplicate(dedupKey, EVENT_TYPE)) {
            return;
        }

        if (!anonymized) {
            // Grace entry — order cancellation is the UserWithdrawn reaction's job; no PII action here.
            log.debug("account.deleted(anonymized=false) grace entry, no order-PII action. dedupKey={}", dedupKey);
            return;
        }

        orderPiiAnonymizationService.anonymizeOrdersForAccount(userId);
        log.info("account.deleted(anonymized=true) processed: order-PII cascade for userId={}, dedupKey={}",
                userId, dedupKey);
    }
}
