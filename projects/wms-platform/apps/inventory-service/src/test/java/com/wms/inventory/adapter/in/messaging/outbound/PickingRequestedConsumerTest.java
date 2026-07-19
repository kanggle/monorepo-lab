package com.wms.inventory.adapter.in.messaging.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inventory.application.command.ReserveStockCommand;
import com.wms.inventory.application.port.in.ReserveStockUseCase;
import com.wms.inventory.application.port.out.EventDedupePort;
import com.wms.inventory.application.port.out.InventoryMovementRepository;
import com.wms.inventory.application.port.out.InventoryRepository;
import com.wms.inventory.application.port.out.MasterReadModelPort;
import com.wms.inventory.application.port.out.OutboxWriter;
import com.wms.inventory.application.port.out.ReservationRepository;
import com.wms.inventory.application.query.InventoryListCriteria;
import com.wms.inventory.application.query.MovementListCriteria;
import com.wms.inventory.application.query.ReservationListCriteria;
import com.wms.inventory.application.result.InventoryView;
import com.wms.inventory.application.result.MovementView;
import com.wms.inventory.application.result.PageView;
import com.wms.inventory.application.result.ReservationView;
import com.wms.inventory.application.service.MasterRefValidator;
import com.wms.inventory.application.service.ReserveStockService;
import com.wms.inventory.domain.event.InventoryDomainEvent;
import com.wms.inventory.domain.event.InventoryReserveFailedEvent;
import com.wms.inventory.domain.event.InventoryReservedEvent;
import com.wms.inventory.domain.model.Inventory;
import com.wms.inventory.domain.model.InventoryMovement;
import com.wms.inventory.domain.model.Reservation;
import com.wms.inventory.domain.model.masterref.LocationSnapshot;
import com.wms.inventory.domain.model.masterref.LotSnapshot;
import com.wms.inventory.domain.model.masterref.SkuSnapshot;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Unit/slice tests for {@link PickingRequestedConsumer} resolution + allocation
 * (TASK-BE-431). Drives the consumer with the <em>real wire shape</em> produced
 * by outbound-service's {@code EventEnvelopeSerializer.pickingRequestedPayload}
 * (top {@code reservationId}; per-line {@code skuId}/{@code lotId}/
 * {@code locationId}/{@code qtyToReserve}) through the real
 * {@link OutboundEventParser}, a real {@link ReserveStockService} wired to
 * in-memory fakes, and a passthrough {@link EventDedupePort}.
 */
class PickingRequestedConsumerTest {

    private static final Instant NOW = Instant.parse("2026-04-25T10:00:00Z");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FakeInventoryRepo invRepo;
    private FakeMovementRepo movementRepo;
    private FakeOutbox outbox;
    private FakeReservationRepo reservationRepo;
    private FakeDedupe dedupe;
    private PickingRequestedConsumer consumer;

    @BeforeEach
    void setUp() {
        invRepo = new FakeInventoryRepo();
        movementRepo = new FakeMovementRepo();
        outbox = new FakeOutbox();
        reservationRepo = new FakeReservationRepo();
        dedupe = new FakeDedupe();
        TransactionTemplate tt = new TransactionTemplate(new NoopTxManager());
        ReserveStockService service = new ReserveStockService(
                reservationRepo, invRepo, movementRepo, outbox,
                new MasterRefValidator(new FakeMasterReadModel()), tt,
                Clock.fixed(NOW, ZoneOffset.UTC), new SimpleMeterRegistry());
        consumer = new PickingRequestedConsumer(
                new OutboundEventParser(MAPPER), dedupe, service, invRepo);
    }

    // ---- AC-1: real serializer wire shape → Reservation + inventory.reserved --

    @Test
    @DisplayName("AC-1: real producer wire (reservationId + skuId/locationId:null/qtyToReserve) reserves")
    void realWireShapeCreatesReservationAndEmitsReserved() {
        UUID warehouseId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        UUID inventoryId = seedRow(warehouseId, randomLoc(), skuId, null, 100);
        UUID reservationId = UUID.randomUUID();

        consumer.handle(realPickingRequested(
                reservationId, warehouseId,
                line(skuId, null, null, 30)), "key");

        // A Reservation was created keyed by reservationId (== pickingRequestId).
        assertThat(reservationRepo.findByPickingRequestId(reservationId)).isPresent();
        // The resolved inventory row was reserved.
        assertThat(invRepo.entries.get(inventoryId).availableQty()).isEqualTo(70);
        assertThat(invRepo.entries.get(inventoryId).reservedQty()).isEqualTo(30);
        // inventory.reserved emitted.
        assertThat(outbox.events).hasSize(1);
        assertThat(outbox.events.get(0)).isInstanceOf(InventoryReservedEvent.class);
        InventoryReservedEvent e = (InventoryReservedEvent) outbox.events.get(0);
        assertThat(e.pickingRequestId()).isEqualTo(reservationId);
    }

    // ---- AC-2: locationId present resolves that specific row -------------------

    @Test
    @DisplayName("AC-2: locationId present resolves that specific row")
    void locationIdPresentResolvesSpecificRow() {
        UUID warehouseId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        UUID locA = randomLoc();
        UUID locB = randomLoc();
        UUID rowA = seedRow(warehouseId, locA, skuId, null, 100);
        UUID rowB = seedRow(warehouseId, locB, skuId, null, 100);

        consumer.handle(realPickingRequested(
                UUID.randomUUID(), warehouseId,
                line(skuId, null, locB, 40)), "key");

        // Only row B (the addressed location) was reserved.
        assertThat(invRepo.entries.get(rowB).reservedQty()).isEqualTo(40);
        assertThat(invRepo.entries.get(rowA).reservedQty()).isZero();
    }

    // ---- AC-3: locationId null, single row + multi-row greatest-first ---------

    @Test
    @DisplayName("AC-3: locationId null single stock row reserves it")
    void locationIdNullSingleRow() {
        UUID warehouseId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        UUID row = seedRow(warehouseId, randomLoc(), skuId, null, 50);

        consumer.handle(realPickingRequested(
                UUID.randomUUID(), warehouseId,
                line(skuId, null, null, 20)), "key");

        assertThat(invRepo.entries.get(row).reservedQty()).isEqualTo(20);
        assertThat(invRepo.entries.get(row).availableQty()).isEqualTo(30);
    }

    @Test
    @DisplayName("AC-3: locationId null multi-row allocates greatest-available first, spanning only when insufficient")
    void locationIdNullMultiRowGreatestFirst() {
        UUID warehouseId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        // Three rows: 30, 80, 10 available. Greatest-first: 80 then 30 (covers 100).
        UUID small = seedRow(warehouseId, randomLoc(), skuId, null, 30);
        UUID big = seedRow(warehouseId, randomLoc(), skuId, null, 80);
        UUID tiny = seedRow(warehouseId, randomLoc(), skuId, null, 10);

        consumer.handle(realPickingRequested(
                UUID.randomUUID(), warehouseId,
                line(skuId, null, null, 100)), "key");

        // big fully drawn (80), small supplies remaining 20, tiny untouched.
        assertThat(invRepo.entries.get(big).reservedQty()).isEqualTo(80);
        assertThat(invRepo.entries.get(big).availableQty()).isZero();
        assertThat(invRepo.entries.get(small).reservedQty()).isEqualTo(20);
        assertThat(invRepo.entries.get(small).availableQty()).isEqualTo(10);
        assertThat(invRepo.entries.get(tiny).reservedQty()).isZero();

        assertThat(outbox.events).hasSize(1);
        assertThat(outbox.events.get(0)).isInstanceOf(InventoryReservedEvent.class);
    }

    // ---- AC-4: total available < requested → reserve.failed, no mutation ------

    @Test
    @DisplayName("AC-4: insufficient total across rows → reserve.failed, no mutation")
    void insufficientStockEmitsReserveFailedNoMutation() {
        UUID warehouseId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        UUID rowA = seedRow(warehouseId, randomLoc(), skuId, null, 30);
        UUID rowB = seedRow(warehouseId, randomLoc(), skuId, null, 20);
        UUID reservationId = UUID.randomUUID();

        consumer.handle(realPickingRequested(
                reservationId, warehouseId,
                line(skuId, null, null, 100)), "key"); // 100 > 50 available

        // No reservation, no mutation.
        assertThat(reservationRepo.byId).isEmpty();
        assertThat(invRepo.entries.get(rowA).reservedQty()).isZero();
        assertThat(invRepo.entries.get(rowB).reservedQty()).isZero();
        assertThat(invRepo.entries.get(rowA).availableQty()).isEqualTo(30);
        assertThat(invRepo.entries.get(rowB).availableQty()).isEqualTo(20);
        // reserve.failed emitted with the picking request id.
        assertThat(outbox.events).hasSize(1);
        assertThat(outbox.events.get(0)).isInstanceOf(InventoryReserveFailedEvent.class);
        InventoryReserveFailedEvent e = (InventoryReserveFailedEvent) outbox.events.get(0);
        assertThat(e.pickingRequestId()).isEqualTo(reservationId);
        assertThat(e.reason()).isEqualTo("INSUFFICIENT_STOCK");
    }

    @Test
    @DisplayName("AC-4 (zero stock): no inventory row at all → reserve.failed, no NPE")
    void zeroStockNoRowEmitsReserveFailed() {
        UUID warehouseId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        // No seeded row for this sku.

        consumer.handle(realPickingRequested(
                reservationId, warehouseId,
                line(skuId, null, null, 5)), "key");

        assertThat(reservationRepo.byId).isEmpty();
        assertThat(outbox.events).hasSize(1);
        assertThat(outbox.events.get(0)).isInstanceOf(InventoryReserveFailedEvent.class);
        InventoryReserveFailedEvent e = (InventoryReserveFailedEvent) outbox.events.get(0);
        assertThat(e.pickingRequestId()).isEqualTo(reservationId);
        assertThat(e.insufficientLines()).hasSize(1);
        // Zero-row line carries a null inventoryId hint and the natural-key identity.
        assertThat(e.insufficientLines().get(0).inventoryId()).isNull();
        assertThat(e.insufficientLines().get(0).skuId()).isEqualTo(skuId);
        assertThat(e.insufficientLines().get(0).qtyRequested()).isEqualTo(5);
        assertThat(e.insufficientLines().get(0).qtyAvailable()).isZero();
    }

    // ---- AC-5: idempotent replay (same eventId) → no duplicate ----------------

    @Test
    @DisplayName("AC-5: duplicate eventId is ignored (dedupe) — no duplicate reservation")
    void duplicateEventIdIsIgnored() {
        UUID warehouseId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        UUID row = seedRow(warehouseId, randomLoc(), skuId, null, 100);
        UUID reservationId = UUID.randomUUID();
        String wire = realPickingRequestedFixedEventId(
                UUID.randomUUID(), reservationId, warehouseId, line(skuId, null, null, 30));

        consumer.handle(wire, "key");
        consumer.handle(wire, "key"); // same eventId → dedupe IGNORED_DUPLICATE

        assertThat(invRepo.entries.get(row).reservedQty()).isEqualTo(30); // reserved once
        assertThat(outbox.events).hasSize(1);
    }

    // ---- wire builders (mirror EventEnvelopeSerializer.pickingRequestedPayload) -

    private static Map<String, Object> line(UUID skuId, UUID lotId, UUID locationId, int qtyToReserve) {
        Map<String, Object> lm = new LinkedHashMap<>();
        lm.put("orderLineId", UUID.randomUUID().toString());
        lm.put("skuId", skuId.toString());
        lm.put("lotId", lotId != null ? lotId.toString() : null);
        lm.put("locationId", locationId != null ? locationId.toString() : null);
        lm.put("qtyToReserve", qtyToReserve);
        return lm;
    }

    @SafeVarargs
    private static String realPickingRequested(UUID reservationId, UUID warehouseId,
                                               Map<String, Object>... lines) {
        return realPickingRequestedFixedEventId(UUID.randomUUID(), reservationId, warehouseId, lines);
    }

    @SafeVarargs
    private static String realPickingRequestedFixedEventId(UUID eventId, UUID reservationId,
                                                           UUID warehouseId,
                                                           Map<String, Object>... lines) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", "outbound.picking.requested");
        envelope.put("eventVersion", 1);
        envelope.put("occurredAt", "2026-04-25T10:00:00.000Z");
        envelope.put("producer", "outbound-service");
        envelope.put("aggregateType", "outbound_saga");
        envelope.put("aggregateId", UUID.randomUUID().toString());
        envelope.put("traceId", null);
        envelope.put("actorId", "system:erp-webhook");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sagaId", UUID.randomUUID().toString());
        payload.put("reservationId", reservationId.toString());
        payload.put("orderId", UUID.randomUUID().toString());
        payload.put("warehouseId", warehouseId.toString());
        payload.put("lines", List.of(lines));
        envelope.put("payload", payload);
        try {
            return MAPPER.writeValueAsString(envelope);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static UUID randomLoc() {
        return UUID.randomUUID();
    }

    private UUID seedRow(UUID warehouseId, UUID locationId, UUID skuId, UUID lotId, int qty) {
        UUID id = UUID.randomUUID();
        Inventory inv = Inventory.restore(id, warehouseId, locationId, skuId, lotId,
                qty, 0, 0, NOW, 0L, NOW, "seed", NOW, "seed");
        invRepo.entries.put(id, inv);
        return id;
    }

    // ---- Fakes ---------------------------------------------------------------

    private static class FakeInventoryRepo implements InventoryRepository {
        final Map<UUID, Inventory> entries = new HashMap<>();

        @Override public Optional<Inventory> findById(UUID id) {
            return Optional.ofNullable(entries.get(id));
        }
        @Override public Optional<Inventory> findByKey(UUID locationId, UUID skuId, UUID lotId) {
            return entries.values().stream()
                    .filter(i -> i.locationId().equals(locationId) && i.skuId().equals(skuId)
                            && Objects.equals(i.lotId(), lotId))
                    .findFirst();
        }
        @Override public List<Inventory> findAvailableByWarehouseSkuLot(UUID warehouseId, UUID skuId, UUID lotId) {
            return entries.values().stream()
                    .filter(i -> i.warehouseId().equals(warehouseId) && i.skuId().equals(skuId)
                            && Objects.equals(i.lotId(), lotId) && i.availableQty() > 0)
                    .sorted(Comparator.comparingInt(Inventory::availableQty).reversed()
                            .thenComparing(Inventory::id))
                    .toList();
        }
        @Override public Optional<InventoryView> findViewById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public Optional<InventoryView> findViewByKey(UUID a, UUID b, UUID c) { throw new UnsupportedOperationException(); }
        @Override public PageView<InventoryView> listViews(InventoryListCriteria c) { throw new UnsupportedOperationException(); }
        @Override public Inventory insert(Inventory inventory) {
            entries.put(inventory.id(), inventory); return inventory;
        }
        @Override public Inventory updateWithVersionCheck(Inventory inventory) {
            entries.put(inventory.id(), inventory); return inventory;
        }
    }

    private static class FakeMovementRepo implements InventoryMovementRepository {
        final List<InventoryMovement> saved = new ArrayList<>();
        @Override public void save(InventoryMovement movement) { saved.add(movement); }
        @Override public PageView<MovementView> list(MovementListCriteria c) { throw new UnsupportedOperationException(); }
    }

    private static class FakeOutbox implements OutboxWriter {
        final List<InventoryDomainEvent> events = new ArrayList<>();
        @Override public void write(InventoryDomainEvent event) { events.add(event); }
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

    /** Empty master read model — snapshot absence is treated as "no opinion" (allow). */
    private static class FakeMasterReadModel implements MasterReadModelPort {
        @Override public Optional<LocationSnapshot> findLocation(UUID id) { return Optional.empty(); }
        @Override public Optional<SkuSnapshot> findSku(UUID id) { return Optional.empty(); }
        @Override public Optional<LotSnapshot> findLot(UUID id) { return Optional.empty(); }
        @Override public Optional<com.wms.inventory.domain.model.masterref.WarehouseSnapshot> findWarehouse(UUID id) { return Optional.empty(); }
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

    private static class NoopTxManager implements PlatformTransactionManager {
        @Override public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new org.springframework.transaction.support.SimpleTransactionStatus();
        }
        @Override public void commit(TransactionStatus status) { }
        @Override public void rollback(TransactionStatus status) { }
    }
}
