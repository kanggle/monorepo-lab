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
 * <p>{@code account.created} is PII-masked (emailHash only — no raw email/name), so the
 * WELCOME carries no name/email personalization (empty vars). Dedup keys off the event
 * id (idempotent re-delivery). The send recipient is the {@code accountId} (= userId);
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
        if (event.payload() == null || event.payload().accountId() == null) {
            log.warn("account.created event missing payload/accountId, skipping. eventId={}", event.eventId());
            return;
        }

        // IAM carries the tenant in the payload (account-events.md); fall back to the
        // envelope, then default 'ecommerce' (D8 net-zero). Threaded via the command since
        // this Kafka thread has no HTTP TenantContext (M4).
        String tenantId = event.payload().tenantId() != null ? event.payload().tenantId() : event.tenantId();

        // No PII personalization: account.created is emailHash-only (ADR-MONO-037 P1). The
        // WELCOME template renders with blank name/email; personalization returns once the
        // profile is enriched from the OIDC token.
        SendNotificationCommand command = new SendNotificationCommand(
                TenantContext.resolveOrDefault(tenantId),
                event.payload().accountId(),
                event.eventId(),
                TemplateType.WELCOME,
                Map.of("name", "", "email", "")
        );

        notificationSendService.sendNotification(command);
    }
}
