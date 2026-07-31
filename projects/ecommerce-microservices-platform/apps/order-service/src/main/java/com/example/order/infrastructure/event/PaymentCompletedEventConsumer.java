package com.example.order.infrastructure.event;

import com.example.messaging.dedupe.EventDedupePort;
import com.example.order.application.service.PaymentConfirmationService;
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
public class PaymentCompletedEventConsumer {

    private final PaymentConfirmationService paymentConfirmationService;
    private final EventDedupePort eventDedupePort;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(topics = "payment.payment.completed", groupId = "order-service")
    public void onMessage(@Payload String payload) throws JsonProcessingException {
        handle(objectMapper.readValue(payload, PaymentCompletedEvent.class));
    }

    void handle(PaymentCompletedEvent event) {
        eventDedupePort.process(
                EventFieldParser.parseUuidOrNull(event.eventId()), "PaymentCompleted", () -> handleWork(event));
    }

    private void handleWork(PaymentCompletedEvent event) {
        if (event.payload() == null) {
            log.warn("PaymentCompleted event has null payload, skipping. eventId={}", event.eventId());
            return;
        }

        if (EventFieldParser.isBlank(event.payload().orderId())) {
            log.warn("PaymentCompleted event has no orderId, skipping. eventId={}", event.eventId());
            return;
        }

        if (EventFieldParser.isBlank(event.payload().paymentId())) {
            log.warn("PaymentCompleted event has no paymentId, skipping. eventId={}", event.eventId());
            return;
        }

        String orderId = event.payload().orderId();
        String paymentId = event.payload().paymentId();

        paymentConfirmationService.markPaymentCompleted(
                orderId, paymentId, EventFieldParser.parseInstant(event.payload().paidAt(), "paidAt"));
    }
}
