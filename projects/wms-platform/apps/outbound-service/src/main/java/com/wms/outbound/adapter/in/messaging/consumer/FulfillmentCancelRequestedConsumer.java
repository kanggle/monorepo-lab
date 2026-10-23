package com.wms.outbound.adapter.in.messaging.consumer;

import com.example.messaging.dedupe.EventDedupePort;
import com.fasterxml.jackson.databind.JsonNode;
import com.wms.outbound.application.command.CancelOrderCommand;
import com.wms.outbound.application.port.in.CancelOrderUseCase;
import com.wms.outbound.application.port.out.OrderPersistencePort;
import com.wms.outbound.domain.model.Order;
import com.wms.outbound.domain.model.OrderStatus;
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
 * Consumes {@code ecommerce.fulfillment.cancel-requested.v1} (cross-project event
 * from ecommerce-platform, ADR-MONO-022 D4 v2(d) X-A, TASK-BE-650): an ecommerce
 * order cancelled after {@code CONFIRMED}, while its Shipping row was still
 * {@code PREPARING} and WMS-routed, asks wms to cancel the outbound order.
 *
 * <p>Until this consumer, cancellation ran only wms → ecommerce (D4 v2(a)), so a
 * cancelled and refunded ecommerce order could still be picked, packed and shipped.
 *
 * <h2>Flow — decided by the outbound <b>order</b> status (X-A)</h2>
 * <ol>
 *   <li>Resolve {@code payload.orderNo} → outbound {@link Order} via
 *       {@link OrderPersistencePort#findByOrderNo}. <b>Miss ⇒ no-op</b> (never
 *       routed through wms; debug log, no DLT).</li>
 *   <li>{@code RECEIVED} / {@code PICKING} ⇒ call the <b>existing</b>
 *       {@link CancelOrderUseCase#cancel}. Its existing compensation
 *       ({@code outbound.picking.cancelled} → reservation release) and its existing
 *       {@code wms.outbound.order.cancelled.v1} emission run unchanged.</li>
 *   <li>{@code PICKED} / {@code PACKING} / {@code PACKED} ⇒ <b>not cancelled</b>.
 *       {@code ALERT} at ERROR level naming {@code orderNo} + status; a wms admin
 *       cancels through the existing admin path once goods are put back.</li>
 *   <li>{@code SHIPPED} ⇒ logged no-op (return flow).</li>
 *   <li>{@code CANCELLED} / {@code BACKORDERED} ⇒ no-op (already terminal).</li>
 * </ol>
 *
 * <h2>System-trigger authorization — WRITE only, never ADMIN</h2>
 *
 * <p>{@code CancelOrderService} has two independent guards. Its <b>role</b> guard
 * reads only {@link CancelOrderCommand#callerRoles()}: post-pick cancel requires
 * {@code ROLE_OUTBOUND_ADMIN}. Its <b>tenant-visibility</b> guard (D9
 * {@code CallerScope}) reads the security context, which a Kafka consumer does not
 * have, so it resolves to <em>unrestricted</em> — that widens which tenants' orders
 * the caller may see, never which states it may cancel.
 *
 * <p>This consumer supplies exactly {@link #SYSTEM_ROLES}
 * ({@code ROLE_OUTBOUND_WRITE}). So even when the order moves to {@code PICKED}
 * between this consumer's status read and the service's own read, the service
 * itself refuses ({@code AccessDeniedException}). That refusal is <b>not</b>
 * caught here: it is retried by the shared error handler (it is not an
 * {@link IllegalArgumentException}), the dedupe row rolls back with the
 * transaction, and the retry reads the new status and ends with the {@code ALERT}.
 * The REST cancel path, which derives roles from the signed JWT, is untouched.
 *
 * <h2>Envelope convention &amp; idempotency</h2>
 *
 * <p>The producer emits this event in the wms envelope convention (camelCase), so
 * {@link EventEnvelopeParser} + the {@code outbound_event_dedupe} (T8) table are
 * reused unchanged — exactly like {@link ManualShipConfirmConsumer}. Re-delivery of
 * the same envelope {@code eventId} short-circuits in {@link EventDedupePort#process};
 * the order terminal-state guard is the second idempotency layer. The envelope and
 * payload {@code tenantId} are opaque correlation (ADR-MONO-022 facet d) and are
 * never used to decide anything here.
 *
 * <h2>Failure → DLT</h2>
 *
 * <p>An unparseable envelope / missing {@code orderNo} throws
 * {@link IllegalArgumentException}, which the shared {@code DefaultErrorHandler}
 * treats as non-retryable and routes to {@code <topic>.DLT}. A post-pick refusal or a
 * shipped order is NOT an error — only structural malformity goes to the DLT.
 */
@Component
@Profile("!standalone")
public class FulfillmentCancelRequestedConsumer {

    private static final Logger log = LoggerFactory.getLogger(FulfillmentCancelRequestedConsumer.class);

    private static final String SYSTEM_ACTOR = "system:fulfillment-cancel-request";

    /** Reason used when the producer omits one (contract: constant in v1). */
    private static final String DEFAULT_REASON = "ORDER_CANCELLED";

    /**
     * Role set granted to this system trigger. WRITE only, by decision (ADR-MONO-022
     * D4 v2(d) X-A): it lets the existing role guard pass for a pre-pick cancel and
     * makes the same guard refuse a post-pick one. Adding {@code ROLE_OUTBOUND_ADMIN}
     * here would remove the human gate over physically picked goods (X-B, rejected).
     */
    private static final Set<String> SYSTEM_ROLES = Set.of("ROLE_OUTBOUND_WRITE");

    /**
     * Version-skip sentinel. A system trigger has no client-supplied {@code If-Match}
     * version, so {@code -1} tells {@code CancelOrderService} to skip the early
     * optimistic-lock comparison (JPA's own version check still applies at flush).
     */
    private static final long VERSION_SKIP = -1L;

    private final EventEnvelopeParser parser;
    private final EventDedupePort dedupe;
    private final OrderPersistencePort orderPersistence;
    private final CancelOrderUseCase cancelOrderUseCase;

    public FulfillmentCancelRequestedConsumer(EventEnvelopeParser parser,
                                              EventDedupePort dedupe,
                                              OrderPersistencePort orderPersistence,
                                              CancelOrderUseCase cancelOrderUseCase) {
        this.parser = parser;
        this.dedupe = dedupe;
        this.orderPersistence = orderPersistence;
        this.cancelOrderUseCase = cancelOrderUseCase;
    }

    @KafkaListener(
            topics = "${outbound.kafka.topics.fulfillment-cancel-requested:ecommerce.fulfillment.cancel-requested.v1}",
            groupId = "${spring.kafka.consumer.group-id:outbound-service}"
    )
    @Transactional
    public void onMessage(@Payload String rawJson,
                          @Header(name = "kafka_receivedMessageKey", required = false) String key) {
        EventEnvelope envelope = parser.parse(rawJson);
        MDC.put("eventId", envelope.eventId().toString());
        MDC.put("consumer", "fulfillment-cancel-requested");
        try {
            EventDedupePort.Outcome outcome = dedupe.process(
                    envelope.eventId(),
                    envelope.eventType(),
                    () -> applyEvent(envelope));
            if (outcome == EventDedupePort.Outcome.IGNORED_DUPLICATE) {
                log.debug("fulfillment.cancel.requested eventId={} already applied; skipping",
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
            // Never routed through wms (or not yet — see the contract's cross-topic
            // ordering note). Nothing to cancel; not a DLT case.
            log.debug("fulfillment_cancel_unknown_order orderNo={} eventId={} -> no-op",
                    orderNo, envelope.eventId());
            return;
        }
        Order order = orderOpt.get();
        OrderStatus status = order.getStatus();

        switch (status) {
            case RECEIVED, PICKING -> cancel(order, status, payload, envelope);
            case PICKED, PACKING, PACKED ->
                // X-A: goods are physically picked. The system request has no ADMIN
                // scope, so it does not cancel; a human admin must, after put-back.
                log.error("ALERT ecommerce cancel request refused after pick — wms admin must cancel "
                                + "after put-back. orderNo={} orderId={} status={} eventId={}",
                        orderNo, order.getId(), status, envelope.eventId());
            case SHIPPED ->
                // Already left the warehouse: the return flow, not a cancel.
                log.warn("fulfillment_cancel_already_shipped orderNo={} orderId={} status=SHIPPED eventId={} -> no-op",
                        orderNo, order.getId(), envelope.eventId());
            default ->
                // CANCELLED / BACKORDERED — already terminal; idempotent no-op.
                log.info("fulfillment_cancel_already_terminal orderNo={} orderId={} status={} eventId={} -> no-op",
                        orderNo, order.getId(), status, envelope.eventId());
        }
    }

    private void cancel(Order order, OrderStatus previousStatus, JsonNode payload, EventEnvelope envelope) {
        String reason = resolveReason(payload);
        cancelOrderUseCase.cancel(new CancelOrderCommand(
                order.getId(),
                reason,
                VERSION_SKIP,
                SYSTEM_ACTOR,
                SYSTEM_ROLES));
        log.info("fulfillment_cancel_applied orderNo={} orderId={} previousStatus={} reason={} eventId={}",
                order.getOrderNo(), order.getId(), previousStatus, reason, envelope.eventId());
    }

    private static String resolveReason(JsonNode payload) {
        JsonNode node = payload.get("reason");
        if (node == null || node.isNull() || !node.isTextual() || node.asText().isBlank()) {
            return DEFAULT_REASON;
        }
        return node.asText();
    }

    private static String requireText(JsonNode node, String field) {
        JsonNode f = node.get(field);
        if (f == null || f.isNull() || !f.isTextual() || f.asText().isBlank()) {
            throw new IllegalArgumentException(
                    "fulfillment cancel-request event missing required field: " + field);
        }
        return f.asText();
    }
}
