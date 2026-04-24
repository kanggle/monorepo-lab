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
public class PaymentCompletedEventConsumer {

    private final SendNotificationUseCase notificationSendService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payment.payment.completed", groupId = "notification-service")
    public void onMessage(@Payload String payload) throws JsonProcessingException {
        PaymentCompletedEvent event = objectMapper.readValue(payload, PaymentCompletedEvent.class);
        handle(event);
    }

    void handle(PaymentCompletedEvent event) {
        if (event.payload() == null) {
            log.warn("PaymentCompleted event has null payload, skipping. eventId={}", event.eventId());
            return;
        }

        String userId = event.payload().userId();
        if (userId == null) {
            log.warn("PaymentCompleted event missing userId, skipping. eventId={}", event.eventId());
            return;
        }

        SendNotificationCommand command = new SendNotificationCommand(
                userId,
                event.eventId(),
                TemplateType.PAYMENT_COMPLETED,
                Map.of(
                        "orderId", event.payload().orderId() != null ? event.payload().orderId() : "",
                        "amount", String.valueOf(event.payload().amount()),
                        "paidAt", event.payload().paidAt() != null ? event.payload().paidAt() : ""
                )
        );

        notificationSendService.sendNotification(command);
    }
}
