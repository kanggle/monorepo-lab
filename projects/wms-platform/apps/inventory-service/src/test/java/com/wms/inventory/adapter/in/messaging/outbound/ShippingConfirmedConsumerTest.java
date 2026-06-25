package com.wms.inventory.adapter.in.messaging.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inventory.application.command.ConfirmReservationCommand;
import com.wms.inventory.application.port.in.ConfirmReservationUseCase;
import com.wms.inventory.application.port.out.EventDedupePort;
import com.wms.inventory.application.port.out.ReservationRepository;
import com.wms.inventory.application.query.ReservationListCriteria;
import com.wms.inventory.application.result.PageView;
import com.wms.inventory.application.result.ReservationView;
import com.wms.inventory.domain.model.Reservation;
import com.wms.inventory.domain.model.ReservationLine;
import com.wms.inventory.domain.model.ReservationStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ShippingConfirmedConsumer} (TASK-BE-437). Drives the consumer with the
 * <em>real wire shape</em> produced by outbound-service's
 * {@code EventEnvelopeSerializer.shippingConfirmedPayload} (top {@code reservationId}; per-line
 * {@code skuId}/{@code lotId}/{@code locationId}/{@code qtyConfirmed}) through the real
 * {@link OutboundEventParser}, a capturing {@link ConfirmReservationUseCase}, and a passthrough
 * {@link EventDedupePort}.
 *
 * <p>The previous consumer read {@code pickingRequestId} / per-line {@code reservationLineId} +
 * {@code shippedQuantity} — none of which the producer emits — and NPE'd on every real event
 * (the live deduction silently never happened; ecommerce SHIPPED on the same topic via
 * {@code orderNo}). These tests lock the corrected reservationId/qtyConfirmed mapping, the
 * return-leg sibling of the BE-431 forward-leg fix.
 */
class ShippingConfirmedConsumerTest {

    private static final Instant NOW = Instant.parse("2026-04-25T10:00:00Z");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FakeReservationRepo reservationRepo;
    private CapturingConfirm confirm;
    private FakeDedupe dedupe;
    private ShippingConfirmedConsumer consumer;

    @BeforeEach
    void setUp() {
        reservationRepo = new FakeReservationRepo();
        confirm = new CapturingConfirm();
        dedupe = new FakeDedupe();
        consumer = new ShippingConfirmedConsumer(
                new OutboundEventParser(MAPPER), dedupe, confirm, reservationRepo);
    }

    // ---- AC-1: real producer wire → confirm with mapped reservationLineId + qtyConfirmed -----

    @Test
    @DisplayName("AC-1: real producer wire (reservationId + skuId/qtyConfirmed) confirms the reservation")
    void realWireShapeConfirmsReservation() {
        UUID reservationId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        ReservationLine resLine = reservationLine(reservationId, skuId, null, 5);
        seedReserved(reservationId, resLine);

        consumer.handle(realShippingConfirmed(
                reservationId, line(skuId, null, 5)), "key");

        assertThat(confirm.captured).isNotNull();
        assertThat(confirm.captured.reservationId()).isEqualTo(reservationId);
        assertThat(confirm.captured.lines()).hasSize(1);
        // The consumer mapped the shipped line (by skuId) to the owning ReservationLine PK,
        // and carried qtyConfirmed (NOT a non-existent shippedQuantity) through.
        assertThat(confirm.captured.lines().get(0).reservationLineId()).isEqualTo(resLine.id());
        assertThat(confirm.captured.lines().get(0).shippedQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("AC-1b: lotId-qualified line maps to the matching reservation line")
    void lotQualifiedLineMaps() {
        UUID reservationId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        ReservationLine resLine = reservationLine(reservationId, skuId, lotId, 3);
        seedReserved(reservationId, resLine);

        consumer.handle(realShippingConfirmed(
                reservationId, line(skuId, lotId, 3)), "key");

        assertThat(confirm.captured.lines().get(0).reservationLineId()).isEqualTo(resLine.id());
        assertThat(confirm.captured.lines().get(0).shippedQuantity()).isEqualTo(3);
    }

    // ---- AC-2: terminal-state reservation → no-op (no confirm) --------------------------------

    @Test
    @DisplayName("AC-2: already-CONFIRMED reservation → no-op (terminal guard, no confirm call)")
    void terminalStateIsNoOp() {
        UUID reservationId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        seed(reservationId, ReservationStatus.CONFIRMED, reservationLine(reservationId, skuId, null, 5));

        consumer.handle(realShippingConfirmed(reservationId, line(skuId, null, 5)), "key");

        assertThat(confirm.captured).isNull();
    }

    // ---- AC-3: unknown reservationId → ignored ------------------------------------------------

    @Test
    @DisplayName("AC-3: unknown reservationId → ignored (no confirm, no throw)")
    void unknownReservationIsIgnored() {
        consumer.handle(realShippingConfirmed(
                UUID.randomUUID(), line(UUID.randomUUID(), null, 5)), "key");

        assertThat(confirm.captured).isNull();
    }

    // ---- AC-4: shipped line with no matching reservation line → hard error --------------------

    @Test
    @DisplayName("AC-4: shipped line whose sku has no matching reservation line → IllegalArgumentException")
    void unmatchedLineThrows() {
        UUID reservationId = UUID.randomUUID();
        seedReserved(reservationId, reservationLine(reservationId, UUID.randomUUID(), null, 5));

        UUID otherSku = UUID.randomUUID();
        assertThatThrownBy(() -> consumer.handle(
                realShippingConfirmed(reservationId, line(otherSku, null, 5)), "key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no matching reservation line");
        assertThat(confirm.captured).isNull();
    }

    // ---- AC-5: duplicate eventId → dedupe ignores (confirm once) ------------------------------

    @Test
    @DisplayName("AC-5: duplicate eventId is ignored (dedupe) — confirm runs once")
    void duplicateEventIdIsIgnored() {
        UUID reservationId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        seedReserved(reservationId, reservationLine(reservationId, skuId, null, 5));
        String wire = realShippingConfirmedFixedEventId(
                UUID.randomUUID(), reservationId, line(skuId, null, 5));

        consumer.handle(wire, "key");
        consumer.handle(wire, "key"); // same eventId → IGNORED_DUPLICATE

        assertThat(confirm.callCount).isEqualTo(1);
    }

    // ---- builders (mirror EventEnvelopeSerializer.shippingConfirmedPayload) -------------------

    private static Map<String, Object> line(UUID skuId, UUID lotId, int qtyConfirmed) {
        Map<String, Object> lm = new LinkedHashMap<>();
        lm.put("orderLineId", UUID.randomUUID().toString());
        lm.put("skuId", skuId.toString());
        lm.put("lotId", lotId != null ? lotId.toString() : null);
        lm.put("locationId", UUID.randomUUID().toString());
        lm.put("qtyConfirmed", qtyConfirmed);
        return lm;
    }

    @SafeVarargs
    private static String realShippingConfirmed(UUID reservationId, Map<String, Object>... lines) {
        return realShippingConfirmedFixedEventId(UUID.randomUUID(), reservationId, lines);
    }

    @SafeVarargs
    private static String realShippingConfirmedFixedEventId(UUID eventId, UUID reservationId,
                                                            Map<String, Object>... lines) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", "outbound.shipping.confirmed");
        envelope.put("eventVersion", 1);
        envelope.put("occurredAt", "2026-04-25T10:00:00.000Z");
        envelope.put("producer", "outbound-service");
        envelope.put("aggregateType", "shipment");
        envelope.put("aggregateId", UUID.randomUUID().toString());
        envelope.put("traceId", null);
        envelope.put("tenantId", "ecommerce");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sagaId", UUID.randomUUID().toString());
        payload.put("reservationId", reservationId.toString());
        payload.put("orderId", UUID.randomUUID().toString());
        payload.put("orderNo", "ORD-20260425-0001");
        payload.put("shipmentId", UUID.randomUUID().toString());
        payload.put("shipmentNo", "SHP-20260425-0001");
        payload.put("warehouseId", UUID.randomUUID().toString());
        payload.put("shippedAt", "2026-04-25T10:00:00.000Z");
        payload.put("carrierCode", "CJ-LOGISTICS");
        payload.put("lines", List.of(lines));
        envelope.put("payload", payload);
        try {
            return MAPPER.writeValueAsString(envelope);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static ReservationLine reservationLine(UUID reservationId, UUID skuId, UUID lotId, int qty) {
        return new ReservationLine(UUID.randomUUID(), reservationId, UUID.randomUUID(),
                UUID.randomUUID(), skuId, lotId, qty);
    }

    private void seedReserved(UUID reservationId, ReservationLine... lines) {
        seed(reservationId, ReservationStatus.RESERVED, lines);
    }

    private void seed(UUID reservationId, ReservationStatus status, ReservationLine... lines) {
        Reservation reservation = Reservation.restore(
                reservationId, reservationId, UUID.randomUUID(), List.of(lines), status,
                NOW.plusSeconds(3600), null, null, null, 0L, NOW, "seed", NOW, "seed");
        reservationRepo.insert(reservation);
    }

    // ---- Fakes -------------------------------------------------------------------------------

    private static class CapturingConfirm implements ConfirmReservationUseCase {
        ConfirmReservationCommand captured;
        int callCount;

        @Override public ReservationView confirm(ConfirmReservationCommand command) {
            captured = command;
            callCount++;
            return null; // consumer ignores the return value
        }
    }

    private static class FakeReservationRepo implements ReservationRepository {
        final Map<UUID, Reservation> byId = new HashMap<>();
        final Map<UUID, Reservation> byPickingRequest = new HashMap<>();

        @Override public Optional<Reservation> findById(UUID id) { return Optional.ofNullable(byId.get(id)); }
        @Override public Optional<Reservation> findByPickingRequestId(UUID p) {
            return Optional.ofNullable(byPickingRequest.get(p));
        }
        @Override public Reservation insert(Reservation reservation) {
            byId.put(reservation.id(), reservation);
            byPickingRequest.put(reservation.pickingRequestId(), reservation);
            return reservation;
        }
        @Override public Reservation updateWithVersionCheck(Reservation reservation) {
            byId.put(reservation.id(), reservation); return reservation;
        }
        @Override public Optional<ReservationView> findViewById(UUID id) {
            return findById(id).map(ReservationView::from);
        }
        @Override public PageView<ReservationView> listViews(ReservationListCriteria c) { throw new UnsupportedOperationException(); }
        @Override public List<Reservation> findExpired(Instant asOf, int limit) { throw new UnsupportedOperationException(); }
        @Override public long countActive() { return byId.size(); }
    }

    /** Passthrough dedupe: runs the work the first time per eventId, ignores duplicates. */
    private static class FakeDedupe implements EventDedupePort {
        private final java.util.Set<UUID> seen = new java.util.HashSet<>();
        @Override public Outcome process(UUID eventId, String eventType, Runnable work) {
            if (!seen.add(eventId)) {
                return Outcome.IGNORED_DUPLICATE;
            }
            work.run();
            return Outcome.APPLIED;
        }
    }
}
