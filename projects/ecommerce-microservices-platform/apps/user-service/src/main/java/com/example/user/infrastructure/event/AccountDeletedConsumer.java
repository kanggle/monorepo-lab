package com.example.user.infrastructure.event;

import com.example.user.application.service.UserProfileService;
import com.example.user.domain.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consumes IAM {@code account.deleted} → projects the deletion into ecommerce
 * (ADR-MONO-037 P2/P3), aligning to the standing IAM consumer obligation
 * (account-events.md TASK-BE-258 + consumer-integration-guide § GDPR downstream).
 *
 * <p>Two-phase, branching on the event's own {@code anonymized} flag:
 * <ul>
 *   <li>{@code anonymized=false} (grace entry) → {@link UserProfileService#withdrawProfile}
 *       (status WITHDRAWN + {@code UserWithdrawn}).</li>
 *   <li>{@code anonymized=true} (post-grace) → {@link UserProfileService#anonymizeProfile}
 *       (profile PII cleared).</li>
 * </ul>
 * It does NOT self-schedule on {@code gracePeriodEndsAt} — the producer re-emits at
 * grace end. Fail-soft (P5): a null payload / missing accountId is logged and skipped;
 * the downstream reactions are idempotent.
 */
@Slf4j
@Component
@Profile("!standalone")
@RequiredArgsConstructor
public class AccountDeletedConsumer {

    private final UserProfileService userProfileService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "account.deleted", groupId = "user-service")
    public void onMessage(@Payload String payload) {
        AccountDeletedEvent event = deserialize(payload);
        handle(event);
    }

    private AccountDeletedEvent deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, AccountDeletedEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize account.deleted event", e);
        }
    }

    void handle(AccountDeletedEvent event) {
        if (event.payload() == null || event.payload().accountId() == null) {
            log.warn("account.deleted event missing payload/accountId, skipping. eventId={}", event.eventId());
            return;
        }

        var payload = event.payload();
        String tenantId = payload.tenantId() != null ? payload.tenantId() : event.tenantId();
        boolean anonymized = Boolean.TRUE.equals(payload.anonymized());
        try {
            TenantContext.set(tenantId);
            if (anonymized) {
                userProfileService.anonymizeProfile(payload.accountId());
            } else {
                userProfileService.withdrawProfile(payload.accountId());
            }
        } finally {
            TenantContext.clear();
        }
    }
}
