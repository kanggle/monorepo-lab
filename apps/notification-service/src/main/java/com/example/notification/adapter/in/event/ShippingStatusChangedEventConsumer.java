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
public class ShippingStatusChangedEventConsumer {

    private final SendNotificationUseCase notificationSendService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "shipping.shipping.status-changed", groupId = "notification-service")
    public void onMessage(@Payload String payload) throws JsonProcessingException {
        ShippingStatusChangedEvent event = objectMapper.readValue(payload, ShippingStatusChangedEvent.class);
        handle(event);
    }

    void handle(ShippingStatusChangedEvent event) {
        if (event.payload() == null) {
            log.warn("ShippingStatusChanged event has null payload, skipping. eventId={}", event.eventId());
            return;
        }

        String userId = event.payload().userId();
        if (userId == null) {
            log.warn("ShippingStatusChanged event missing userId, skipping. eventId={}", event.eventId());
            return;
        }

        SendNotificationCommand command = new SendNotificationCommand(
                userId,
                event.eventId(),
                TemplateType.SHIPPING_STATUS_CHANGED,
                Map.of(
                        "orderId", event.payload().orderId() != null ? event.payload().orderId() : "",
                        "newStatus", event.payload().newStatus() != null ? event.payload().newStatus() : "",
                        "trackingNumber", event.payload().trackingNumber() != null ? event.payload().trackingNumber() : "",
                        "carrier", event.payload().carrier() != null ? event.payload().carrier() : ""
                )
        );

        notificationSendService.sendNotification(command);
    }
}
