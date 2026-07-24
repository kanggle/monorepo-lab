package com.example.scmplatform.logistics.adapter.inbound.messaging;

import com.example.scmplatform.logistics.application.usecase.ConsumeShippingConfirmedCommand;
import com.example.scmplatform.logistics.application.usecase.ConsumeShippingConfirmedUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Inbound Kafka adapter for the live seam {@code wms.outbound.shipping.confirmed.v1} — the trigger
 * that finally feeds the dispatch machinery (ADR-053 §D4; subscriptions contract). Mirrors the
 * sibling demand-planning-service {@code WmsLowStockAlertConsumer} (wms→scm cross-project consumer).
 *
 * <p><b>Error handling is the {@code DefaultErrorHandler}</b> wired in {@code KafkaConsumerConfig}
 * (backoff {@code [1s,2s,4s]} → DLT {@code wms.outbound.shipping.confirmed.v1.DLT}), so this listener
 * simply <b>lets exceptions propagate</b> — it never catch-and-swallows:
 * <ul>
 *   <li><b>Malformed envelope</b> (null value / unparseable / null {@code eventId}/{@code payload}/
 *       {@code shipmentId}) → {@link NonRetryableConsumerException} → the error handler routes it to
 *       the DLT <b>immediately</b> (non-retryable, no backoff) + ops alert (never silently dropped).</li>
 *   <li><b>Transient DB/infra fault</b> inside the use case → propagates → retried {@code [1s,2s,4s]}
 *       → DLT on exhaustion.</li>
 *   <li><b>Vendor dispatch failure</b> → <b>NOT</b> a consume failure. {@link ConsumeShippingConfirmedUseCase}
 *       records {@code DISPATCH_FAILED} and returns normally, so control reaches {@code ack.acknowledge()}
 *       below: the offset advances, the event is NOT sent to DLT, and recovery is the operator
 *       {@code :retry} endpoint (S5, Cat C; task's key invariant / Failure Scenario B).</li>
 * </ul>
 *
 * <p><b>Manual ack</b> only on the success path (after the dispatch TX commits).
 */
@Component
public class ShippingConfirmedConsumer {

    private static final Logger log = LoggerFactory.getLogger(ShippingConfirmedConsumer.class);

    public static final String TOPIC = "wms.outbound.shipping.confirmed.v1";
    public static final String GROUP = "scm-logistics-v1";

    private final ConsumeShippingConfirmedUseCase consumeUseCase;
    private final ObjectMapper objectMapper;

    public ShippingConfirmedConsumer(ConsumeShippingConfirmedUseCase consumeUseCase,
                                     ObjectMapper objectMapper) {
        this.consumeUseCase = consumeUseCase;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = TOPIC, groupId = GROUP, containerFactory = "kafkaListenerContainerFactory")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        ShippingConfirmedEnvelope envelope = parse(record);

        // May throw a transient exception (DB/infra) → propagate → retry→DLT. A VENDOR failure is
        // swallowed into DISPATCH_FAILED inside the use case and returns normally (S5).
        consumeUseCase.consume(toCommand(envelope));

        // ── ACK SITE ──────────────────────────────────────────────────────────────────────────
        // Reached only when consume() returned normally — INCLUDING a DISPATCH_FAILED outcome
        // (vendor failure ≠ consume failure). Offset advances; the event is NOT sent to DLT.
        ack.acknowledge();
    }

    /**
     * Parse + validate the wms camelCase envelope. A malformed envelope is <b>non-retryable</b>
     * ({@link NonRetryableConsumerException}) so the {@code DefaultErrorHandler} routes it straight
     * to the DLT (no ack here — the error handler commits the offset after publishing to the DLT).
     */
    private ShippingConfirmedEnvelope parse(ConsumerRecord<String, String> record) {
        if (record.value() == null) {
            log.error("Null message value on topic={} partition={} offset={} — routing to DLT (non-retryable)",
                    record.topic(), record.partition(), record.offset());
            throw new NonRetryableConsumerException("Null message value on " + record.topic());
        }
        ShippingConfirmedEnvelope envelope;
        try {
            envelope = objectMapper.readValue(record.value(), ShippingConfirmedEnvelope.class);
        } catch (Exception parseEx) {
            log.error("Malformed envelope on topic={} partition={} offset={}: {} — routing to DLT (non-retryable)",
                    record.topic(), record.partition(), record.offset(), parseEx.getMessage());
            throw new NonRetryableConsumerException("Malformed envelope on " + record.topic(), parseEx);
        }
        if (!envelope.isValid()) {
            log.error("Invalid envelope (null eventId/payload/shipmentId) on topic={} partition={} offset={} "
                            + "— routing to DLT (non-retryable)",
                    record.topic(), record.partition(), record.offset());
            throw new NonRetryableConsumerException(
                    "Invalid envelope: null eventId/payload/shipmentId on " + record.topic());
        }
        return envelope;
    }

    private static ConsumeShippingConfirmedCommand toCommand(ShippingConfirmedEnvelope envelope) {
        ShippingConfirmedEnvelope.Payload payload = envelope.payload();
        return new ConsumeShippingConfirmedCommand(
                envelope.eventId(),
                envelope.tenantId(),
                payload.shipmentId(),
                payload.shipmentNo(),
                payload.orderId(),
                payload.orderNo(),
                payload.carrierCode());
    }
}
