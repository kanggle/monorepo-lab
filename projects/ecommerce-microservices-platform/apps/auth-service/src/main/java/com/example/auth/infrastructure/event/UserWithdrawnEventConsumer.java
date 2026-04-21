package com.example.auth.infrastructure.event;

import com.example.auth.application.service.UserWithdrawalService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@org.springframework.context.annotation.Profile("!standalone")
public class UserWithdrawnEventConsumer {

    private final UserWithdrawalService userWithdrawalService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "user.user.withdrawn", groupId = "auth-service")
    public void onMessage(@Payload String payload) {
        try {
            handle(objectMapper.readValue(payload, UserWithdrawnEvent.class));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize UserWithdrawnEvent", e);
        }
    }

    void handle(UserWithdrawnEvent event) {
        if (event.payload() == null) {
            log.warn("UserWithdrawn event has null payload, skipping. eventId={}", event.eventId());
            return;
        }

        String userId = event.payload().userId();
        if (userId == null || userId.isBlank()) {
            log.warn("UserWithdrawn event has no userId, skipping. eventId={}", event.eventId());
            return;
        }

        userWithdrawalService.handleUserWithdrawal(userId);
        log.info("UserWithdrawn event processed successfully: userId={}, eventId={}", userId, event.eventId());
    }
}
