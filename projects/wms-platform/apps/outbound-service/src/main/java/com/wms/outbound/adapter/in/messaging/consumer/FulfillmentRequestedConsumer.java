package com.wms.outbound.adapter.in.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.outbound.application.command.ReceiveOrderCommand;
import com.wms.outbound.application.command.ReceiveOrderLineCommand;
import com.wms.outbound.application.port.in.ReceiveOrderUseCase;
import com.wms.outbound.application.port.out.EventDedupePort;
import com.wms.outbound.application.port.out.MasterReadModelPort;
import com.wms.outbound.domain.exception.OrderNoDuplicateException;
import com.wms.outbound.domain.model.OrderSource;
import com.wms.outbound.domain.model.ShipToAddress;
import com.wms.outbound.domain.model.masterref.LotSnapshot;
import com.wms.outbound.domain.model.masterref.PartnerSnapshot;
import com.wms.outbound.domain.model.masterref.SkuSnapshot;
import com.wms.outbound.domain.model.masterref.WarehouseSnapshot;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes {@code ecommerce.fulfillment.requested.v1} (cross-project event from
 * ecommerce-platform, ADR-MONO-022 D1) and creates an outbound order via the
 * existing {@link ReceiveOrderUseCase} — exactly as the ERP webhook does, but
 * sourced from a Kafka event and carrying an additive drop-ship
 * {@link ShipToAddress shipTo}.
 *
 * <p>By ACL design the producer emits this event in the <strong>wms envelope
 * convention</strong> (camelCase {@code eventId} / {@code eventType} /
 * {@code occurredAt} / {@code aggregateId} / {@code aggregateType} /
 * {@code payload}), so {@link EventEnvelopeParser} is reused unchanged.
 *
 * <h2>Idempotency (layered)</h2>
 * <ol>
 *   <li><b>Outer (eventId dedupe, T8).</b> {@link EventDedupePort#process}
 *       short-circuits re-delivery of the same envelope {@code eventId}.</li>
 *   <li><b>Inner (orderNo).</b> {@link ReceiveOrderUseCase} guards on
 *       {@code existsByOrderNo}; a re-sent fulfillment for an existing
 *       {@code orderNo} surfaces {@link OrderNoDuplicateException}, which is
 *       caught here and treated as a safe no-op (matching the webhook).</li>
 * </ol>
 *
 * <h2>Failure → DLT</h2>
 *
 * <p>Unparseable envelope, unresolved/inactive master (partner / warehouse /
 * sku / lot), or a malformed payload field throw {@link IllegalArgumentException},
 * which the shared {@code DefaultErrorHandler} treats as non-retryable and
 * routes to {@code <topic>.DLT}.
 *
 * <p>{@code @Transactional} on the listener method opens the outer TX so the
 * dedupe row and the order-intake writes commit atomically (the order-intake
 * use-case participates in this TX).
 */
@Component
@Profile("!standalone")
public class FulfillmentRequestedConsumer {

    private static final Logger log = LoggerFactory.getLogger(FulfillmentRequestedConsumer.class);

    private static final String SYSTEM_ACTOR = "system:fulfillment-ecommerce";

    private final EventEnvelopeParser parser;
    private final EventDedupePort dedupe;
    private final ReceiveOrderUseCase receiveOrderUseCase;
    private final MasterReadModelPort masterReadModel;

    public FulfillmentRequestedConsumer(EventEnvelopeParser parser,
                                        EventDedupePort dedupe,
                                        ReceiveOrderUseCase receiveOrderUseCase,
                                        MasterReadModelPort masterReadModel) {
        this.parser = parser;
        this.dedupe = dedupe;
        this.receiveOrderUseCase = receiveOrderUseCase;
        this.masterReadModel = masterReadModel;
    }

    @KafkaListener(
            topics = "${outbound.kafka.topics.fulfillment-requested:ecommerce.fulfillment.requested.v1}",
            groupId = "${spring.kafka.consumer.group-id:outbound-service}"
    )
    @Transactional
    public void onMessage(@Payload String rawJson,
                          @Header(name = "kafka_receivedMessageKey", required = false) String key) {
        EventEnvelope envelope = parser.parse(rawJson);
        MDC.put("eventId", envelope.eventId().toString());
        MDC.put("consumer", "fulfillment-requested");
        try {
            EventDedupePort.Outcome outcome = dedupe.process(
                    envelope.eventId(),
                    envelope.eventType(),
                    () -> applyEvent(envelope));
            if (outcome == EventDedupePort.Outcome.IGNORED_DUPLICATE) {
                log.debug("fulfillment.requested eventId={} already applied; skipping",
                        envelope.eventId());
            }
        } finally {
            MDC.remove("eventId");
            MDC.remove("consumer");
        }
    }

    private void applyEvent(EventEnvelope envelope) {
        ReceiveOrderCommand command = toCommand(envelope.payload(), envelope.tenantId());
        try {
            receiveOrderUseCase.receive(command);
            log.info("fulfillment_order_received orderNo={} eventId={}",
                    command.orderNo(), envelope.eventId());
        } catch (OrderNoDuplicateException duplicate) {
            // Idempotent re-fulfillment of an existing orderNo — safe no-op,
            // matching the ERP-webhook path semantics.
            log.info("fulfillment_duplicate_orderNo orderNo={} eventId={} -> no-op",
                    command.orderNo(), envelope.eventId());
        }
    }

    /**
     * Resolves the fulfillment payload's business codes to uuids via
     * {@link MasterReadModelPort} (exactly as the ERP webhook does) and builds
     * the {@link ReceiveOrderCommand}.
     *
     * @param tenantId the originating ecommerce tenant captured from the inbound
     *        envelope (ADR-MONO-022 facet d) — carried as opaque correlation onto
     *        the order; {@code null} for standalone/pre-M5 producers.
     * @throws IllegalArgumentException if a required field is missing or any
     *         master code is unresolved / inactive (→ non-retryable → DLT)
     */
    private ReceiveOrderCommand toCommand(JsonNode payload, String tenantId) {
        String orderNo = requireText(payload, "orderNo");
        String customerPartnerCode = requireText(payload, "customerPartnerCode");
        String warehouseCode = requireText(payload, "warehouseCode");
        LocalDate requiredShipDate = optionalDate(payload, "requiredShipDate");

        PartnerSnapshot partner = masterReadModel.findPartnerByCode(customerPartnerCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Partner not found in read model: code=" + customerPartnerCode));
        if (!partner.canReceive()) {
            throw new IllegalArgumentException("Partner not eligible to receive: code="
                    + customerPartnerCode + " status=" + partner.status()
                    + " type=" + partner.partnerType());
        }

        WarehouseSnapshot warehouse = masterReadModel.findWarehouseByCode(warehouseCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Warehouse not found in read model: code=" + warehouseCode));
        if (!warehouse.isActive()) {
            throw new IllegalArgumentException("Warehouse inactive: code=" + warehouseCode);
        }

        List<ReceiveOrderLineCommand> lines = toLineCommands(payload.get("lines"));
        ShipToAddress shipTo = toShipTo(payload.get("shipTo"));

        return new ReceiveOrderCommand(
                orderNo,
                OrderSource.FULFILLMENT_ECOMMERCE.name(),
                partner.id(),
                warehouse.id(),
                requiredShipDate,
                null /* notes */,
                shipTo,
                tenantId /* opaque correlation, ADR-MONO-022 facet d */,
                lines,
                SYSTEM_ACTOR,
                Set.of());
    }

    private List<ReceiveOrderLineCommand> toLineCommands(JsonNode linesNode) {
        if (linesNode == null || !linesNode.isArray() || linesNode.isEmpty()) {
            throw new IllegalArgumentException("fulfillment event has no lines");
        }
        List<ReceiveOrderLineCommand> lines = new ArrayList<>(linesNode.size());
        for (JsonNode lineNode : linesNode) {
            int lineNo = requireInt(lineNode, "lineNo");
            String skuCode = requireText(lineNode, "skuCode");
            int qtyOrdered = requireInt(lineNode, "qtyOrdered");

            SkuSnapshot sku = masterReadModel.findSkuByCode(skuCode)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "SKU not found in read model: code=" + skuCode));
            if (!sku.isActive()) {
                throw new IllegalArgumentException("SKU inactive: code=" + skuCode);
            }

            UUID lotId = null;
            String lotNo = optionalText(lineNode, "lotNo");
            if (lotNo != null && !lotNo.isBlank()) {
                LotSnapshot lot = masterReadModel.findLotBySkuAndLotNo(sku.id(), lotNo)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Lot not found in read model: sku=" + skuCode + " lotNo=" + lotNo));
                lotId = lot.id();
            }
            lines.add(new ReceiveOrderLineCommand(lineNo, sku.id(), lotId, qtyOrdered));
        }
        return lines;
    }

    /**
     * Builds the optional drop-ship recipient from the {@code shipTo} payload
     * object. Returns {@code null} when absent (B2B fallback).
     */
    private static ShipToAddress toShipTo(JsonNode shipToNode) {
        if (shipToNode == null || shipToNode.isNull()) {
            return null;
        }
        String recipientName = requireText(shipToNode, "recipientName");
        String address = requireText(shipToNode, "address");
        String phone = optionalText(shipToNode, "phone");
        return new ShipToAddress(recipientName, address, phone);
    }

    private static String requireText(JsonNode node, String field) {
        JsonNode f = node.get(field);
        if (f == null || f.isNull() || !f.isTextual() || f.asText().isBlank()) {
            throw new IllegalArgumentException("fulfillment event missing required field: " + field);
        }
        return f.asText();
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode f = node.get(field);
        if (f == null || f.isNull() || !f.isTextual()) {
            return null;
        }
        return f.asText();
    }

    private static int requireInt(JsonNode node, String field) {
        JsonNode f = node.get(field);
        if (f == null || f.isNull() || !f.isNumber()) {
            throw new IllegalArgumentException("fulfillment event missing numeric field: " + field);
        }
        return f.asInt();
    }

    private static LocalDate optionalDate(JsonNode node, String field) {
        JsonNode f = node.get(field);
        if (f == null || f.isNull() || !f.isTextual() || f.asText().isBlank()) {
            return null;
        }
        return LocalDate.parse(f.asText());
    }
}
