package com.example.settlement.infrastructure.event;

import com.example.settlement.application.service.ReversePaymentCommand;
import com.example.settlement.application.service.SettlementService;
import com.example.settlement.domain.repository.ProcessedEventStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes {@code payment.payment.refunded} → appends REVERSAL rows that net the
 * order's accruals to zero (AC-5; v1 = full order reversal). Idempotent on the
 * envelope {@code event_id} and on {@code (order_id, payment_id)} (AC-6). No prior
 * accruals (cancel-before-capture, or already reversed) → no-op.
 */
@Slf4j
@Component
@org.springframework.context.annotation.Profile("!standalone")
@RequiredArgsConstructor
public class PaymentRefundedReversalConsumer {

    private final SettlementService settlementService;
    private final ProcessedEventStore processedEventStore;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(topics = "payment.payment.refunded", groupId = "settlement-service")
    public void onMessage(@Payload String payload) throws JsonProcessingException {
        handle(objectMapper.readValue(payload, PaymentEvent.class));
    }

    public void handle(PaymentEvent event) {
        if (processedEventStore.isDuplicate(event.eventId(), "PaymentRefunded")) {
            return;
        }
        if (event.payload() == null
                || EventFieldParser.isBlank(event.payload().orderId())
                || EventFieldParser.isBlank(event.payload().paymentId())) {
            log.warn("PaymentRefunded missing orderId/paymentId — skipping. eventId={}", event.eventId());
            return;
        }

        // Back-compat: a legacy refund event without fullyRefunded (null) is a full refund.
        boolean fullyRefunded = event.payload().fullyRefunded() == null || event.payload().fullyRefunded();
        settlementService.reverse(new ReversePaymentCommand(
                event.payload().orderId(),
                event.payload().paymentId(),
                event.payload().amount(),
                fullyRefunded,
                EventFieldParser.parseInstantOrNow(event.payload().refundedAt())));
    }
}
