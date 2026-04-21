package com.example.notification.adapter.in.event;

import com.example.notification.application.command.SendNotificationCommand;
import com.example.notification.application.port.in.SendNotificationUseCase;
import com.example.notification.domain.model.TemplateType;
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
public class OrderPlacedEventConsumer {

    private final SendNotificationUseCase notificationSendService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order.order.placed", groupId = "notification-service")
    public void onMessage(@Payload String payload) throws JsonProcessingException {
        OrderPlacedEvent event = objectMapper.readValue(payload, OrderPlacedEvent.class);
        handle(event);
    }

    void handle(OrderPlacedEvent event) {
        if (event.payload() == null) {
            log.warn("OrderPlaced event has null payload, skipping. eventId={}", event.eventId());
            return;
        }

        String userId = event.payload().userId();
        if (userId == null) {
            log.warn("OrderPlaced event missing userId, skipping. eventId={}", event.eventId());
            return;
        }

        SendNotificationCommand command = new SendNotificationCommand(
                userId,
                event.eventId(),
                TemplateType.ORDER_PLACED,
                Map.of(
                        "orderId", event.payload().orderId() != null ? event.payload().orderId() : "",
                        "totalPrice", String.valueOf(event.payload().totalPrice())
                )
        );

        notificationSendService.sendNotification(command);
    }
}
