package com.example.order.infrastructure.event;

import com.example.order.application.service.UserWithdrawalOrderService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@org.springframework.context.annotation.Profile("!standalone")
@RequiredArgsConstructor
public class UserWithdrawnEventConsumer {

    private final UserWithdrawalOrderService userWithdrawalOrderService;
    private final EventDeduplicationChecker eventDeduplicationChecker;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(topics = "user.user.withdrawn", groupId = "order-service")
    public void onMessage(@Payload String payload) throws JsonProcessingException {
        handle(objectMapper.readValue(payload, UserWithdrawnEvent.class));
    }

    void handle(UserWithdrawnEvent event) {
        if (eventDeduplicationChecker.isDuplicate(event.eventId(), "UserWithdrawn")) {
            return;
        }

        if (event.payload() == null) {
            log.warn("UserWithdrawn event has null payload, skipping. eventId={}", event.eventId());
            return;
        }

        if (EventFieldParser.isBlank(event.payload().userId())) {
            log.warn("UserWithdrawn event has no userId, skipping. eventId={}", event.eventId());
            return;
        }

        userWithdrawalOrderService.cancelOrdersForWithdrawnUser(event.payload().userId());
        log.info("UserWithdrawn event processed successfully: userId={}, eventId={}", event.payload().userId(), event.eventId());
    }
}
