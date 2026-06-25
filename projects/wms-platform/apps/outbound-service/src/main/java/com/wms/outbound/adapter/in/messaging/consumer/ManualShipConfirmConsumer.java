package com.wms.outbound.adapter.in.messaging.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.outbound.application.command.ConfirmShippingCommand;
import com.wms.outbound.application.port.in.ConfirmShippingUseCase;
import com.wms.outbound.application.port.out.EventDedupePort;
import com.wms.outbound.application.port.out.OrderPersistencePort;
import com.wms.outbound.application.port.out.SagaPersistencePort;
import com.wms.outbound.domain.model.Order;
import com.wms.outbound.domain.model.OutboundSaga;
import com.wms.outbound.domain.model.SagaStatus;
import java.util.Optional;
import java.util.Set;
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
 * Consumes {@code ecommerce.shipping.manual-confirm-requested.v1} (cross-project
 * event from ecommerce-platform, ADR-MONO-022 D4 v2(c), TASK-MONO-305) so an
 * ecommerce operator who hand-ships a WMS-routed order can opt in to also
 * deducting wms physical inventory.
 *
 * <p>This closes the reverse-direction gap: without it, a WMS-routed order
 * shipped by hand leaves its wms reservation {@code RESERVED} forever (D4 v2(b)
 * only reconciles the wms→ecommerce direction).
 *
 * <h2>Flow</h2>
 * <ol>
 *   <li>Resolve {@code payload.orderNo} → outbound {@link Order} via
 *       {@link OrderPersistencePort#findByOrderNo}. <b>Miss ⇒ no-op</b> (the
 *       order never routed through wms — legitimate; debug log, no DLT).</li>
 *   <li>Load the saga by {@code order.id}:
 *     <ul>
 *       <li>{@link SagaStatus#PACKING_CONFIRMED} ⇒ call the <b>existing</b>
 *           {@link ConfirmShippingUseCase#confirm} → existing
 *           {@code wms.outbound.shipping.confirmed.v1} → existing inventory
 *           deduction. <b>No new inventory path; inventory data model
 *           untouched.</b></li>
 *       <li>already {@link SagaStatus#SHIPPED} / {@link SagaStatus#COMPLETED}
 *           ⇒ terminal no-op (already deducted; idempotent).</li>
 *       <li>any earlier state ⇒ WARN no-op (not yet physically picked/packed —
 *           a forced deduction would violate the physical invariant; never a
 *           silent forced skip).</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <h2>Envelope convention &amp; idempotency</h2>
 *
 * <p>By ACL design the producer emits this event in the <strong>wms envelope
 * convention</strong> (camelCase {@code eventId} / {@code eventType} /
 * {@code occurredAt} / {@code aggregateId} / {@code aggregateType} /
 * {@code payload}), so {@link EventEnvelopeParser} + the
 * {@code outbound_event_dedupe} (T8) table are reused unchanged — exactly like
 * {@link FulfillmentRequestedConsumer}. Re-delivery of the same envelope
 * {@code eventId} short-circuits in {@link EventDedupePort#process}; the saga
 * terminal-state guard is the second idempotency layer.
 *
 * <h2>System-trigger authorization</h2>
 *
 * <p>{@link ConfirmShippingUseCase} enforces
 * {@code requireAnyRole(ROLE_OUTBOUND_WRITE, ROLE_OUTBOUND_ADMIN)} and a
 * cross-tenant {@code CallerScope} guard. As a system trigger this consumer
 * supplies an explicit {@link #SYSTEM_ROLES} role set so the role guard passes,
 * and runs with no security context — so {@code CallerScopeProvider.current()}
 * resolves to {@code CallerScope.unrestricted()} and the order-access guard is a
 * no-op. The existing REST confirm path and its authorization are therefore
 * <b>untouched</b>.
 *
 * <h2>Failure → DLT</h2>
 *
 * <p>An unparseable envelope / missing {@code orderNo} throws
 * {@link IllegalArgumentException}, which the shared {@code DefaultErrorHandler}
 * treats as non-retryable and routes to {@code <topic>.DLT}. A resolved-but-not
 * -yet-packed saga is NOT an error (WARN no-op) — only structural malformity
 * goes to the DLT.
 */
@Component
@Profile("!standalone")
public class ManualShipConfirmConsumer {

    private static final Logger log = LoggerFactory.getLogger(ManualShipConfirmConsumer.class);

    private static final String SYSTEM_ACTOR = "system:manual-ship-confirm";

    /** Internal carrier code used when the ecommerce event carries no carrier. */
    private static final String DEFAULT_CARRIER_CODE = "WMS-MANUAL";

    /**
     * Role set granted to this system trigger so the existing
     * {@link ConfirmShippingUseCase} role guard passes. Mirrors a native wms
     * operator's write role — it does NOT weaken the REST path, which keeps
     * deriving roles from the signed JWT.
     */
    private static final Set<String> SYSTEM_ROLES = Set.of("ROLE_OUTBOUND_WRITE");

    /**
     * Version-skip sentinel for the optimistic-lock check. A system trigger has
     * no client-supplied {@code If-Match} version, so {@code -1} tells
     * {@code ConfirmShippingService.assertVersionAndStatus} to skip the version
     * comparison while still enforcing the {@code PACKED} status invariant.
     */
    private static final long VERSION_SKIP = -1L;

    private final EventEnvelopeParser parser;
    private final EventDedupePort dedupe;
    private final OrderPersistencePort orderPersistence;
    private final SagaPersistencePort sagaPersistence;
    private final ConfirmShippingUseCase confirmShippingUseCase;

    public ManualShipConfirmConsumer(EventEnvelopeParser parser,
                                     EventDedupePort dedupe,
                                     OrderPersistencePort orderPersistence,
                                     SagaPersistencePort sagaPersistence,
                                     ConfirmShippingUseCase confirmShippingUseCase) {
        this.parser = parser;
        this.dedupe = dedupe;
        this.orderPersistence = orderPersistence;
        this.sagaPersistence = sagaPersistence;
        this.confirmShippingUseCase = confirmShippingUseCase;
    }

    @KafkaListener(
            topics = "${outbound.kafka.topics.manual-ship-confirm:ecommerce.shipping.manual-confirm-requested.v1}",
            groupId = "${spring.kafka.consumer.group-id:outbound-service}"
    )
    @Transactional
    public void onMessage(@Payload String rawJson,
                          @Header(name = "kafka_receivedMessageKey", required = false) String key) {
        EventEnvelope envelope = parser.parse(rawJson);
        MDC.put("eventId", envelope.eventId().toString());
        MDC.put("consumer", "manual-ship-confirm");
        try {
            EventDedupePort.Outcome outcome = dedupe.process(
                    envelope.eventId(),
                    envelope.eventType(),
                    () -> applyEvent(envelope));
            if (outcome == EventDedupePort.Outcome.IGNORED_DUPLICATE) {
                log.debug("manual.ship.confirm eventId={} already applied; skipping",
                        envelope.eventId());
            }
        } finally {
            MDC.remove("eventId");
            MDC.remove("consumer");
        }
    }

    private void applyEvent(EventEnvelope envelope) {
        JsonNode payload = envelope.payload();
        String orderNo = requireText(payload, "orderNo");

        Optional<Order> orderOpt = orderPersistence.findByOrderNo(orderNo);
        if (orderOpt.isEmpty()) {
            // Order never routed through wms — legitimate (operator hand-shipped a
            // non-WMS order). No deduction to do; not a DLT case.
            log.debug("manual_ship_confirm_unknown_order orderNo={} eventId={} -> no-op",
                    orderNo, envelope.eventId());
            return;
        }
        Order order = orderOpt.get();

        Optional<OutboundSaga> sagaOpt = sagaPersistence.findByOrderId(order.getId());
        if (sagaOpt.isEmpty()) {
            // An outbound order with no saga should not occur, but if it does the
            // safe action is no-op (never force a deduction without saga state).
            log.warn("manual_ship_confirm_no_saga orderNo={} orderId={} eventId={} -> no-op",
                    orderNo, order.getId(), envelope.eventId());
            return;
        }
        SagaStatus status = sagaOpt.get().status();

        switch (status) {
            case PACKING_CONFIRMED -> confirmShipping(order, payload, envelope);
            case SHIPPED, COMPLETED ->
                // Already deducted on the original confirm; idempotent terminal no-op.
                log.info("manual_ship_confirm_already_shipped orderNo={} orderId={} saga={} eventId={} -> no-op",
                        orderNo, order.getId(), status, envelope.eventId());
            default ->
                // Earlier than PACKING_CONFIRMED: the order is not yet physically
                // picked/packed. Forcing an inventory deduction here would violate
                // the physical invariant — WARN no-op, never throw.
                log.warn("manual_ship_confirm_not_packed orderNo={} orderId={} saga={} eventId={} -> no-op",
                        orderNo, order.getId(), status, envelope.eventId());
        }
    }

    private void confirmShipping(Order order, JsonNode payload, EventEnvelope envelope) {
        String carrierCode = resolveCarrierCode(payload);
        confirmShippingUseCase.confirm(new ConfirmShippingCommand(
                order.getId(),
                VERSION_SKIP,
                carrierCode,
                SYSTEM_ACTOR,
                SYSTEM_ROLES));
        log.info("manual_ship_confirm_deducted orderNo={} orderId={} carrier={} eventId={}",
                order.getOrderNo(), order.getId(), carrierCode, envelope.eventId());
    }

    /**
     * Resolves the carrier code from the event payload, falling back to the
     * {@link #DEFAULT_CARRIER_CODE internal default} when the operator path
     * omits it (the ecommerce manual ship may not record a carrier).
     */
    private static String resolveCarrierCode(JsonNode payload) {
        JsonNode node = payload.get("carrierCode");
        if (node == null || node.isNull() || !node.isTextual() || node.asText().isBlank()) {
            return DEFAULT_CARRIER_CODE;
        }
        return node.asText();
    }

    private static String requireText(JsonNode node, String field) {
        JsonNode f = node.get(field);
        if (f == null || f.isNull() || !f.isTextual() || f.asText().isBlank()) {
            throw new IllegalArgumentException(
                    "manual ship-confirm event missing required field: " + field);
        }
        return f.asText();
    }
}
