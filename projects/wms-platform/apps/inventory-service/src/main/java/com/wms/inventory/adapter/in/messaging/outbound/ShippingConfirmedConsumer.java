package com.wms.inventory.adapter.in.messaging.outbound;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.inventory.application.command.ConfirmReservationCommand;
import com.wms.inventory.application.port.in.ConfirmReservationUseCase;
import com.wms.inventory.application.port.out.EventDedupePort;
import com.wms.inventory.application.port.out.ReservationRepository;
import com.wms.inventory.domain.exception.StateTransitionInvalidException;
import com.wms.inventory.domain.model.Reservation;
import com.wms.inventory.domain.model.ReservationStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
 * Consumes {@code wms.outbound.shipping.confirmed.v1} and confirms the
 * reservation (W5: shipped → consumed).
 *
 * <p><strong>Layered idempotency.</strong> Two independent guards run in
 * series, both required by the architecture spec:
 * <ol>
 *   <li><strong>Outer (eventId dedupe, trait T8).</strong>
 *       {@link EventDedupePort#process(UUID, String, Runnable)} inserts a row
 *       into {@code inventory_event_dedupe} keyed by the envelope's
 *       {@code eventId}. A duplicate {@code eventId} (typical Kafka
 *       at-least-once redelivery) is short-circuited at the table's PK
 *       constraint and the confirm work is not re-executed.</li>
 *   <li><strong>Inner (terminal-state guard).</strong> If the reservation
 *       is already in a terminal state ({@code CONFIRMED} via prior
 *       confirmation, {@code RELEASED} via TTL or cancel), the consumer
 *       logs WARN and treats the message as a no-op rather than routing to
 *       DLT — terminal-state collisions are resolved by the existing state,
 *       not by retry.</li>
 * </ol>
 *
 * <p>Both layers are required: the eventId dedupe table is the
 * spec-mandated observable surface for "did this Kafka message already get
 * processed", while the terminal-state guard handles cross-consumer races
 * where a different {@code eventId} mutated the aggregate first (e.g., a
 * cancel that arrived before the confirmation).
 *
 * <p>The consumer is {@code @Transactional} so the dedupe row, the
 * reservation/inventory updates, and the outbox row commit (or roll back)
 * atomically. {@code EventDedupePersistenceAdapter} declares
 * {@code Propagation.MANDATORY}, ensuring the dedupe write joins this TX.
 *
 * <p>Authoritative consumed shape: {@code inventory-events.md} §C4.
 */
@Component
@Profile("!standalone")
public class ShippingConfirmedConsumer {

    private static final Logger log = LoggerFactory.getLogger(ShippingConfirmedConsumer.class);
    private static final String SYSTEM_ACTOR = "system:shipping-confirmed-consumer";

    private final OutboundEventParser parser;
    private final EventDedupePort eventDedupePort;
    private final ConfirmReservationUseCase confirmReservation;
    private final ReservationRepository reservationRepository;

    public ShippingConfirmedConsumer(OutboundEventParser parser,
                                     EventDedupePort eventDedupePort,
                                     ConfirmReservationUseCase confirmReservation,
                                     ReservationRepository reservationRepository) {
        this.parser = parser;
        this.eventDedupePort = eventDedupePort;
        this.confirmReservation = confirmReservation;
        this.reservationRepository = reservationRepository;
    }

    @KafkaListener(
            topics = "${inventory.kafka.topics.outbound-shipping-confirmed:wms.outbound.shipping.confirmed.v1}",
            groupId = "${spring.kafka.consumer.group-id:inventory-service}"
    )
    @Transactional
    public void handle(@Payload String rawJson,
                       @Header(name = "kafka_receivedMessageKey", required = false) String key) {
        OutboundEventParser.Parsed envelope = parser.parse(rawJson);
        MDC.put("eventId", envelope.eventId().toString());
        MDC.put("consumer", "shipping-confirmed");
        try {
            EventDedupePort.Outcome outcome = eventDedupePort.process(
                    envelope.eventId(), envelope.eventType(),
                    () -> applyConfirm(envelope));
            if (outcome == EventDedupePort.Outcome.IGNORED_DUPLICATE) {
                log.debug("outbound.shipping.confirmed eventId={} already applied; skipping",
                        envelope.eventId());
            }
        } finally {
            MDC.remove("eventId");
            MDC.remove("consumer");
        }
    }

    /**
     * Inner-guard body: terminal-state short-circuits remain in place to
     * absorb cross-consumer races. Runs inside the outer TX started by
     * {@link #handle(String, String)} and joined by the dedupe write.
     */
    private void applyConfirm(OutboundEventParser.Parsed envelope) {
        JsonNode payload = envelope.payload();
        UUID pickingRequestId = UUID.fromString(payload.get("pickingRequestId").asText());
        Optional<Reservation> existing = reservationRepository.findByPickingRequestId(pickingRequestId);
        if (existing.isEmpty()) {
            log.warn("Shipping confirm for unknown pickingRequestId {} — ignored", pickingRequestId);
            return;
        }
        Reservation reservation = existing.get();
        if (reservation.status() != ReservationStatus.RESERVED) {
            log.warn("Shipping confirm for reservation {} in terminal state {} — no-op",
                    reservation.id(), reservation.status());
            return;
        }

        JsonNode lines = payload.get("lines");
        if (lines == null || !lines.isArray() || lines.isEmpty()) {
            throw new IllegalArgumentException("shipping.confirmed has no lines");
        }
        List<ConfirmReservationCommand.Line> commandLines = new ArrayList<>(lines.size());
        for (JsonNode line : lines) {
            UUID reservationLineId = UUID.fromString(line.get("reservationLineId").asText());
            int shipped = line.get("shippedQuantity").asInt();
            commandLines.add(new ConfirmReservationCommand.Line(reservationLineId, shipped));
        }
        try {
            confirmReservation.confirm(new ConfirmReservationCommand(
                    reservation.id(), reservation.version(), commandLines,
                    envelope.eventId(), SYSTEM_ACTOR));
        } catch (StateTransitionInvalidException e) {
            log.warn("Shipping confirm race on reservation {}: {}",
                    reservation.id(), e.getMessage());
        }
    }
}
