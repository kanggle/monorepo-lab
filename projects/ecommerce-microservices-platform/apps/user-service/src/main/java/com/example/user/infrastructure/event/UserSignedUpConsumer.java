package com.example.user.infrastructure.event;

import com.example.user.application.service.UserSignedUpHandler;
import com.example.user.domain.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!standalone")
@RequiredArgsConstructor
public class UserSignedUpConsumer {

    private final UserSignedUpHandler userSignedUpHandler;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "auth.user.signed-up", groupId = "user-service")
    public void onMessage(@Payload String payload) {
        UserSignedUpEvent event = deserialize(payload);
        handle(event);
    }

    private UserSignedUpEvent deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, UserSignedUpEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize UserSignedUp event", e);
        }
    }

    void handle(UserSignedUpEvent event) {
        if (event.payload() == null) {
            log.warn("UserSignedUp event has null payload, skipping. eventId={}", event.eventId());
            return;
        }

        var eventPayload = event.payload();
        if (eventPayload.userId() == null || eventPayload.email() == null) {
            log.warn("UserSignedUp event missing required fields, skipping. eventId={}", event.eventId());
            return;
        }

        // Bind the originating tenant for the create (M5/net-zero): the envelope's
        // tenant_id scopes the stamped row. auth-service may not have migrated yet, so
        // tenant_id can be null → TenantContext.set(null) clears → currentTenant()
        // resolves to the default tenant (D8 net-zero). Cleared in finally per message.
        try {
            TenantContext.set(event.tenantId());
            userSignedUpHandler.handle(eventPayload.userId(), eventPayload.email(), eventPayload.name());
        } finally {
            TenantContext.clear();
        }
    }
}
