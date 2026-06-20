package com.example.notification.adapter.in.event;

import com.example.notification.application.command.SendNotificationCommand;
import com.example.notification.application.port.in.SendNotificationUseCase;
import com.example.notification.domain.model.TemplateType;
import com.example.notification.domain.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumes IAM {@code account.created} → sends the onboarding WELCOME (ADR-MONO-037 P1).
 * Replaces the dead-topic {@code UserSignedUpEventConsumer} ({@code auth.user.signed-up},
 * retired with the decommissioned ecommerce auth-service, TASK-BE-132).
 *
 * <p>The wire is FLAT (TASK-BE-422): fields are read from the JSON root, not a nested
 * {@code payload}. {@code account.created} is PII-masked (emailHash only — no raw
 * email/name), so the WELCOME carries no name/email personalization (empty vars). The flat
 * payload has NO {@code eventId}, so dedup keys off a stable {@code account.created:<accountId>}
 * composite (idempotent re-delivery). The send recipient is the {@code accountId} (= userId);
 * the channel sender resolves the address — a not-yet-enriched profile degrades fail-soft.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountCreatedEventConsumer {

    private final SendNotificationUseCase notificationSendService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "account.created", groupId = "notification-service")
    public void onMessage(@Payload String payload) throws JsonProcessingException {
        AccountCreatedEvent event = objectMapper.readValue(payload, AccountCreatedEvent.class);
        handle(event);
    }

    void handle(AccountCreatedEvent event) {
        if (event.accountId() == null) {
            log.warn("account.created event missing accountId, skipping.");
            return;
        }

        // IAM carries the tenant at the top level (account-events.md, flat wire); a null
        // tenant → default 'ecommerce' (D8 net-zero). Threaded via the command since this
        // Kafka thread has no HTTP TenantContext (M4).
        String tenantId = event.tenantId();

        // The flat account.created payload carries no eventId (TASK-BE-422); derive a stable
        // dedup key from the accountId (account.created is emitted once per account).
        String dedupKey = "account.created:" + event.accountId();

        // No PII personalization: account.created is emailHash-only (ADR-MONO-037 P1). The
        // WELCOME template renders with blank name/email; personalization returns once the
        // profile is enriched from the OIDC token.
        SendNotificationCommand command = new SendNotificationCommand(
                TenantContext.resolveOrDefault(tenantId),
                event.accountId(),
                dedupKey,
                TemplateType.WELCOME,
                Map.of("name", "", "email", "")
        );

        notificationSendService.sendNotification(command);
    }
}
