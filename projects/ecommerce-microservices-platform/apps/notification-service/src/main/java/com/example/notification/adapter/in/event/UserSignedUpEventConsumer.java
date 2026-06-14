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

@Slf4j
@Component
@RequiredArgsConstructor
public class UserSignedUpEventConsumer {

    private final SendNotificationUseCase notificationSendService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "auth.user.signed-up", groupId = "notification-service")
    public void onMessage(@Payload String payload) throws JsonProcessingException {
        UserSignedUpEvent event = objectMapper.readValue(payload, UserSignedUpEvent.class);
        handle(event);
    }

    void handle(UserSignedUpEvent event) {
        if (event.payload() == null) {
            log.warn("UserSignedUp event has null payload, skipping. eventId={}", event.eventId());
            return;
        }

        String userId = event.payload().userId();
        if (userId == null) {
            log.warn("UserSignedUp event missing userId, skipping. eventId={}", event.eventId());
            return;
        }

        // auth-service emits tenant_id on signup (user-service already reads it); a
        // blank/missing value defaults to 'ecommerce' (D8 net-zero). Threaded via the
        // command since this Kafka thread has no HTTP TenantContext (M4).
        SendNotificationCommand command = new SendNotificationCommand(
                TenantContext.resolveOrDefault(event.tenantId()),
                userId,
                event.eventId(),
                TemplateType.WELCOME,
                Map.of(
                        "name", event.payload().name() != null ? event.payload().name() : "",
                        "email", event.payload().email() != null ? event.payload().email() : ""
                )
        );

        notificationSendService.sendNotification(command);
    }
}
