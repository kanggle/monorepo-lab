package com.wms.inventory.adapter.in.messaging.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.common.page.PageResult;
import com.example.messaging.dedupe.EventDedupePort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inventory.application.port.out.InventoryMovementRepository;
import com.wms.inventory.application.port.out.InventoryRepository;
import com.wms.inventory.application.port.out.OutboxWriter;
import com.wms.inventory.application.port.out.ReservationRepository;
import com.wms.inventory.application.query.InventoryListCriteria;
import com.wms.inventory.application.query.MovementListCriteria;
import com.wms.inventory.application.query.ReservationListCriteria;
import com.wms.inventory.application.result.InventoryView;
import com.wms.inventory.application.result.MovementView;
import com.wms.inventory.application.result.ReservationView;
import com.wms.inventory.application.service.ReleaseReservationService;
import com.wms.inventory.domain.event.InventoryDomainEvent;
import com.wms.inventory.domain.event.InventoryReleasedEvent;
import com.wms.inventory.domain.model.Inventory;
import com.wms.inventory.domain.model.InventoryMovement;
import com.wms.inventory.domain.model.ReleasedReason;
import com.wms.inventory.domain.model.Reservation;
import com.wms.inventory.domain.model.ReservationLine;
import com.wms.inventory.domain.model.ReservationStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PickingCancelledConsumer} (TASK-BE-651).
 *
 * <p>The consumer used to read {@code payload.pickingRequestId} — a field the producer
 * ({@code outbound-service EventEnvelopeSerializer.pickingCancelledPayload}, authoritative schema
 * {@code outbound-events.md} §4) has never sent on this event. Every real cancel NPE'd, was
 * retried, and was dead-lettered; the reservation stayed {@code RESERVED} and the saga stuck in
 * {@code CANCELLATION_REQUESTED} (33rd demo window, 2026-10-23 UTC). This is the compensation-leg
 * sibling of the BE-431 (forward) and BE-437 (return) fixes.
 *
 * <p>🔴 The wire shape below is <em>not</em> typed from the consumer-side contract (that is the
 * BE-437 trap: a hand-written fixture of a stale document goes green while the producer sends
 * something else). Its field set is asserted against the producer's own contract block
 * ({@code outbound-events.md} §4) by {@link #fixtureCarriesExactlyTheProducerContractFields()},
 * and the outbound module's {@code EventEnvelopeSerializerTest} asserts the real serializer emits
 * that same field set — so fixture = contract = serializer. The values mirror the live window:
 * {@code reservationId} = {@code sagaId} = the reservation's {@code picking_request_id}, while the
 * reservation's own {@code id} is a different UUID.
 *
 * <p>Everything below the listener is real — the parser, the consumer, and
 * {@link ReleaseReservationService}; only the repositories, the outbox and the dedupe table are
 * in-memory fakes.
 */
class PickingCancelledConsumerTest {

    private static final Instant NOW = Instant.parse("2026-10-23T13:00:00Z");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path OUTBOUND_CONTRACT =
            Paths.get("specs", "contracts", "events", "outbound-events.md");

    private FakeReservationRepo reservationRepo;
    private FakeInventoryRepo inventoryRepo;
    private FakeOutbox outbox;
    private FakeDedupe dedupe;
    private PickingCancelledConsumer consumer;

    @BeforeEach
    void setUp() {
        reservationRepo = new FakeReservationRepo();
        inventoryRepo = new FakeInventoryRepo();
        outbox = new FakeOutbox();
        dedupe = new FakeDedupe();
        ReleaseReservationService release = new ReleaseReservationService(
                reservationRepo, inventoryRepo, new FakeMovementRepo(), outbox,
                Clock.fixed(NOW, ZoneOffset.UTC), new SimpleMeterRegistry());
        consumer = new PickingCancelledConsumer(
                new OutboundEventParser(MAPPER), dedupe, release, reservationRepo);
    }

    // ---- the fixture is the producer's shape, not a hand-written guess ------------------------

    @Test
    @DisplayName("fixture payload carries exactly the producer contract's fields (outbound-events.md §4)")
    void fixtureCarriesExactlyTheProducerContractFields() throws IOException {
        Set<String> contract = producerContractPayloadFields();
        // Non-vacuity: a parse that found nothing would make the equality below meaningless.
        assertThat(contract).contains("reservationId").doesNotContain("pickingRequestId");

        UUID id = UUID.randomUUID();
        JsonNode payload = MAPPER.readTree(liveCancel(UUID.randomUUID(), id, id)).get("payload");
        Set<String> fixture = new HashSet<>();
        payload.fieldNames().forEachRemaining(fixture::add);

        assertThat(fixture).isEqualTo(contract);
    }

    // ---- AC: the live payload releases the reservation ----------------------------------------

    @Test
    @DisplayName("live payload (reservationId = sagaId = picking_request_id) releases the reservation with CANCELLED")
    void livePayloadReleasesReservation() {
        UUID sagaId = UUID.randomUUID();           // 01a168e0-… in the window
        UUID pickingRequestId = sagaId;            // the saga seeds pickingRequestId from its own id
        Seeded seeded = seedReserved(pickingRequestId, 1); // reservation.id (2f397b48-…) differs
        assertThat(seeded.reservation.id()).isNotEqualTo(pickingRequestId);

        consumer.handle(liveCancel(UUID.randomUUID(), sagaId, pickingRequestId), sagaId.toString());

        Reservation after = reservationRepo.byId.get(seeded.reservation.id());
        assertThat(after.status()).isEqualTo(ReservationStatus.RELEASED);
        Inventory inv = inventoryRepo.entries.get(seeded.inventoryId);
        assertThat(inv.reservedQty()).isZero();
        assertThat(inv.availableQty()).isEqualTo(10);

        assertThat(outbox.events).hasSize(1);
        InventoryReleasedEvent released = (InventoryReleasedEvent) outbox.events.get(0);
        assertThat(released.releasedReason()).isEqualTo(ReleasedReason.CANCELLED);
        assertThat(released.reservationId()).isEqualTo(seeded.reservation.id());
        // outbound's SagaIdResolver correlates the reply by this field.
        assertThat(released.pickingRequestId()).isEqualTo(pickingRequestId);
    }

    @Test
    @DisplayName("lookup is by reservationId, never by sagaId (they need not be equal)")
    void lookupUsesReservationIdNotSagaId() {
        UUID pickingRequestId = UUID.randomUUID();
        Seeded seeded = seedReserved(pickingRequestId, 2);

        consumer.handle(liveCancel(UUID.randomUUID(), UUID.randomUUID(), pickingRequestId), "k");

        assertThat(reservationRepo.byId.get(seeded.reservation.id()).status())
                .isEqualTo(ReservationStatus.RELEASED);
        assertThat(outbox.events).hasSize(1);
    }

    // ---- edge cases ---------------------------------------------------------------------------

    @Test
    @DisplayName("already CONFIRMED reservation → WARN no-op (no release, no event)")
    void confirmedReservationIsNoOp() {
        UUID pickingRequestId = UUID.randomUUID();
        Seeded seeded = seed(pickingRequestId, ReservationStatus.CONFIRMED, 1);

        consumer.handle(liveCancel(UUID.randomUUID(), pickingRequestId, pickingRequestId), "k");

        assertThat(reservationRepo.byId.get(seeded.reservation.id()).status())
                .isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(outbox.events).isEmpty();
    }

    @Test
    @DisplayName("already RELEASED (TTL) reservation → WARN no-op (no second event)")
    void releasedReservationIsNoOp() {
        UUID pickingRequestId = UUID.randomUUID();
        seed(pickingRequestId, ReservationStatus.RELEASED, 1);

        consumer.handle(liveCancel(UUID.randomUUID(), pickingRequestId, pickingRequestId), "k");

        assertThat(outbox.events).isEmpty();
    }

    @Test
    @DisplayName("unknown reservationId (cancel before picking.requested was applied) → ignored, no throw")
    void unknownReservationIsIgnored() {
        UUID id = UUID.randomUUID();

        consumer.handle(liveCancel(UUID.randomUUID(), id, id), "k");

        assertThat(outbox.events).isEmpty();
    }

    @Test
    @DisplayName("same eventId delivered twice → released once")
    void duplicateEventIdReleasesOnce() {
        UUID pickingRequestId = UUID.randomUUID();
        seedReserved(pickingRequestId, 1);
        String wire = liveCancel(UUID.randomUUID(), pickingRequestId, pickingRequestId);

        consumer.handle(wire, "k");
        consumer.handle(wire, "k");

        assertThat(outbox.events).hasSize(1);
    }

    @Test
    @DisplayName("saga-sweeper re-emission (fresh eventId, same payload) after the release → no second event")
    void sweeperReEmissionAfterReleaseIsNoOp() {
        UUID pickingRequestId = UUID.randomUUID();
        seedReserved(pickingRequestId, 1);

        consumer.handle(liveCancel(UUID.randomUUID(), pickingRequestId, pickingRequestId), "k");
        consumer.handle(liveCancel(UUID.randomUUID(), pickingRequestId, pickingRequestId), "k");

        assertThat(outbox.events).hasSize(1);
    }

    @Test
    @DisplayName("payload without reservationId is malformed → IllegalArgumentException (non-retryable), not an NPE")
    void missingReservationIdIsNonRetryable() {
        UUID pickingRequestId = UUID.randomUUID();
        seedReserved(pickingRequestId, 1);
        String wire = withoutReservationId(liveCancel(UUID.randomUUID(), pickingRequestId, pickingRequestId));

        assertThatThrownBy(() -> consumer.handle(wire, "k"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reservationId");
        assertThat(outbox.events).isEmpty();
    }

    // ---- builders -----------------------------------------------------------------------------

    /**
     * The envelope {@code EventEnvelopeSerializer} writes for a {@code PickingCancelledEvent}
     * ({@code CancelOrderService.java} publishes it with {@code reservationId =
     * saga.pickingRequestId()}). Payload field set pinned by
     * {@link #fixtureCarriesExactlyTheProducerContractFields()}.
     */
    private static String liveCancel(UUID eventId, UUID sagaId, UUID reservationId) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", "outbound.picking.cancelled");
        envelope.put("eventVersion", 1);
        envelope.put("occurredAt", "2026-10-23T13:16:00Z");
        envelope.put("producer", "outbound-service");
        envelope.put("aggregateType", "outbound_saga");
        envelope.put("aggregateId", sagaId.toString());
        envelope.put("traceId", null);
        envelope.put("actorId", "system:fulfillment-cancel-requested-consumer");
        envelope.put("tenantId", "ecommerce");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sagaId", sagaId.toString());
        payload.put("reservationId", reservationId.toString());
        payload.put("orderId", UUID.randomUUID().toString());
        payload.put("reason", "고객 주문 취소 요청");
        envelope.put("payload", payload);
        try {
            return MAPPER.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String withoutReservationId(String wire) {
        try {
            JsonNode root = MAPPER.readTree(wire);
            ((com.fasterxml.jackson.databind.node.ObjectNode) root.get("payload")).remove("reservationId");
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Field names of the {@code "payload"} JSON block under
     * {@code ### 4. `outbound.picking.cancelled`} in the producer contract, read from the
     * repository (walks up from the module directory). Fails loudly if not found.
     */
    static Set<String> producerContractPayloadFields() throws IOException {
        String doc = Files.readString(locate(OUTBOUND_CONTRACT), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        int heading = doc.indexOf("### 4. `outbound.picking.cancelled`");
        assertThat(heading).as("outbound-events.md § 4 heading").isGreaterThanOrEqualTo(0);
        Matcher block = Pattern.compile("```json\\n(.*?)\\n```", Pattern.DOTALL)
                .matcher(doc.substring(heading));
        assertThat(block.find()).as("outbound-events.md § 4 json block").isTrue();
        JsonNode payload = MAPPER.readTree("{" + block.group(1) + "}").get("payload");
        assertThat(payload).as("outbound-events.md § 4 block has a \"payload\" object").isNotNull();
        Set<String> fields = new HashSet<>();
        Iterator<String> it = payload.fieldNames();
        it.forEachRemaining(fields::add);
        return fields;
    }

    private static Path locate(Path relative) {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new AssertionError(relative + " not found above " + Paths.get("").toAbsolutePath()
                + " — this check must not pass without reading it");
    }

    private record Seeded(Reservation reservation, UUID inventoryId) {}

    private Seeded seedReserved(UUID pickingRequestId, int qty) {
        return seed(pickingRequestId, ReservationStatus.RESERVED, qty);
    }

    /** Inventory row of 10 with {@code qty} of it reserved by a reservation in {@code status}. */
    private Seeded seed(UUID pickingRequestId, ReservationStatus status, int qty) {
        UUID inventoryId = UUID.randomUUID();
        inventoryRepo.entries.put(inventoryId, Inventory.restore(inventoryId, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), null, 10 - qty, qty, 0, NOW, 0L,
                NOW, "seed", NOW, "seed"));
        UUID reservationId = UUID.randomUUID();
        ReservationLine line = new ReservationLine(UUID.randomUUID(), reservationId, inventoryId,
                UUID.randomUUID(), UUID.randomUUID(), null, qty);
        Reservation reservation = Reservation.restore(reservationId, pickingRequestId,
                UUID.randomUUID(), List.of(line), status, NOW.plusSeconds(3600),
                status == ReservationStatus.RELEASED ? ReleasedReason.EXPIRED : null,
                status == ReservationStatus.CONFIRMED ? NOW : null,
                status == ReservationStatus.RELEASED ? NOW : null,
                0L, NOW, "seed", NOW, "seed");
        reservationRepo.insert(reservation);
        return new Seeded(reservation, inventoryId);
    }

    // ---- Fakes --------------------------------------------------------------------------------

    private static class FakeReservationRepo implements ReservationRepository {
        final Map<UUID, Reservation> byId = new HashMap<>();
        final Map<UUID, UUID> idByPickingRequest = new HashMap<>();

        @Override public Optional<Reservation> findById(UUID id) { return Optional.ofNullable(byId.get(id)); }
        @Override public Optional<Reservation> findByPickingRequestId(UUID p) {
            return Optional.ofNullable(idByPickingRequest.get(p)).map(byId::get);
        }
        @Override public Reservation insert(Reservation reservation) {
            byId.put(reservation.id(), reservation);
            idByPickingRequest.put(reservation.pickingRequestId(), reservation.id());
            return reservation;
        }
        @Override public Reservation updateWithVersionCheck(Reservation reservation) {
            byId.put(reservation.id(), reservation);
            return reservation;
        }
        @Override public Optional<ReservationView> findViewById(UUID id) { return findById(id).map(ReservationView::from); }
        @Override public PageResult<ReservationView> listViews(ReservationListCriteria c) { throw new UnsupportedOperationException(); }
        @Override public List<Reservation> findExpired(Instant asOf, int limit) { throw new UnsupportedOperationException(); }
        @Override public long countActive() { return byId.size(); }
    }

    private static class FakeInventoryRepo implements InventoryRepository {
        final Map<UUID, Inventory> entries = new HashMap<>();
        @Override public Optional<Inventory> findById(UUID id) { return Optional.ofNullable(entries.get(id)); }
        @Override public Optional<Inventory> findByKey(UUID a, UUID b, UUID c) { throw new UnsupportedOperationException(); }
        @Override public List<Inventory> findAvailableByWarehouseSkuLot(UUID a, UUID b, UUID c) { throw new UnsupportedOperationException(); }
        @Override public Optional<InventoryView> findViewById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public Optional<InventoryView> findViewByKey(UUID a, UUID b, UUID c) { throw new UnsupportedOperationException(); }
        @Override public PageResult<InventoryView> listViews(InventoryListCriteria c) { throw new UnsupportedOperationException(); }
        @Override public Inventory insert(Inventory inventory) { entries.put(inventory.id(), inventory); return inventory; }
        @Override public Inventory updateWithVersionCheck(Inventory inventory) { entries.put(inventory.id(), inventory); return inventory; }
    }

    private static class FakeMovementRepo implements InventoryMovementRepository {
        final List<InventoryMovement> saved = new ArrayList<>();
        @Override public void save(InventoryMovement movement) { saved.add(movement); }
        @Override public PageResult<MovementView> list(MovementListCriteria c) { throw new UnsupportedOperationException(); }
    }

    private static class FakeOutbox implements OutboxWriter {
        final List<InventoryDomainEvent> events = new ArrayList<>();
        @Override public void write(InventoryDomainEvent event) { events.add(event); }
    }

    /** Passthrough dedupe: runs the work the first time per eventId, ignores duplicates. */
    private static class FakeDedupe implements EventDedupePort {
        private final Set<UUID> seen = new HashSet<>();
        @Override public Outcome process(UUID eventId, String eventType, Runnable work) {
            if (!seen.add(eventId)) {
                return Outcome.IGNORED_DUPLICATE;
            }
            work.run();
            return Outcome.APPLIED;
        }
    }
}
