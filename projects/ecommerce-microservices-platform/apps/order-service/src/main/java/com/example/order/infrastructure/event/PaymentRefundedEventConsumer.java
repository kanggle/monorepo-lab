package com.example.order.infrastructure.event;

import com.example.order.application.service.PaymentRefundConfirmationService;
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
public class PaymentRefundedEventConsumer {

    private final PaymentRefundConfirmationService paymentRefundConfirmationService;
    private final EventDeduplicationChecker eventDeduplicationChecker;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(topics = "payment.payment.refunded", groupId = "order-service")
    public void onMessage(@Payload String payload) throws JsonProcessingException {
        handle(objectMapper.readValue(payload, PaymentRefundedEvent.class));
    }

    void handle(PaymentRefundedEvent event) {
        if (eventDeduplicationChecker.isDuplicate(event.eventId(), "PaymentRefunded")) {
            return;
        }

        if (event.payload() == null) {
            log.warn("PaymentRefunded event has null payload, skipping. eventId={}", event.eventId());
            return;
        }

        if (EventFieldParser.isBlank(event.payload().orderId())) {
            log.warn("PaymentRefunded event has no orderId, skipping. eventId={}", event.eventId());
            return;
        }

        // Partial refunds (fullyRefunded == false) do not change the order's status —
        // the order is marked REFUNDED only when the payment is fully refunded. A legacy
        // event without the flag (null) is treated as a full refund (back-compat).
        Boolean fullyRefunded = event.payload().fullyRefunded();
        boolean isFullRefund = (fullyRefunded == null) || fullyRefunded;
        if (!isFullRefund) {
            log.info("Partial PaymentRefunded for orderId={} — no order-status change (totalRefunded={})",
                    event.payload().orderId(), event.payload().totalRefunded());
            return;
        }

        paymentRefundConfirmationService.markRefunded(
                event.payload().orderId(), EventFieldParser.parseInstant(event.payload().refundedAt(), "refundedAt"));
    }
}
