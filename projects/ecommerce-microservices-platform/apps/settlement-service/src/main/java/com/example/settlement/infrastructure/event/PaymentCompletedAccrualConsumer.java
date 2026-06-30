package com.example.settlement.infrastructure.event;

import com.example.settlement.application.service.AccruePaymentCommand;
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
 * Consumes {@code payment.payment.completed} → joins the cached OrderPlaced snapshot
 * by {@code orderId}, splits each line by its seller's effective rate, appends
 * ACCRUAL rows (AC-3). Idempotent on the envelope {@code event_id} and on
 * {@code (order_id, payment_id)} (AC-6). A missing snapshot raises (F2) → retry → DLQ.
 */
@Slf4j
@Component
@org.springframework.context.annotation.Profile("!standalone")
@RequiredArgsConstructor
public class PaymentCompletedAccrualConsumer {

    private final SettlementService settlementService;
    private final ProcessedEventStore processedEventStore;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(topics = "payment.payment.completed", groupId = "settlement-service")
    public void onMessage(@Payload String payload) throws JsonProcessingException {
        handle(objectMapper.readValue(payload, PaymentEvent.class));
    }

    // @Transactional so handle()'s MANDATORY processed-event dedupe + ledger write are
    // atomic when handle() is invoked directly through the proxy (the IT drives handle()
    // without the @KafkaListener onMessage tx boundary). A no-op in prod, where onMessage
    // self-invokes handle() so this proxy advice is bypassed and onMessage's tx remains the
    // single boundary — behaviour unchanged (TASK-BE-461).
    @Transactional
    public void handle(PaymentEvent event) {
        if (processedEventStore.isDuplicate(event.eventId(), "PaymentCompleted")) {
            return;
        }
        if (event.payload() == null
                || EventFieldParser.isBlank(event.payload().orderId())
                || EventFieldParser.isBlank(event.payload().paymentId())) {
            log.warn("PaymentCompleted missing orderId/paymentId — skipping. eventId={}", event.eventId());
            return;
        }

        settlementService.accrue(new AccruePaymentCommand(
                event.payload().orderId(),
                event.payload().paymentId(),
                EventFieldParser.parseInstantOrNow(event.payload().paidAt())));
    }
}
