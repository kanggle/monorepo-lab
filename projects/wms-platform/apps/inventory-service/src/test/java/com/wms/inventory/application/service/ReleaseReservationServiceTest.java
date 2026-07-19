package com.wms.inventory.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.inventory.application.command.ReleaseReservationCommand;
import com.wms.inventory.application.port.out.InventoryMovementRepository;
import com.wms.inventory.application.port.out.InventoryRepository;
import com.wms.inventory.application.port.out.OutboxWriter;
import com.wms.inventory.application.port.out.ReservationRepository;
import com.wms.inventory.application.query.InventoryListCriteria;
import com.wms.inventory.application.query.MovementListCriteria;
import com.wms.inventory.application.query.ReservationListCriteria;
import com.wms.inventory.application.result.InventoryView;
import com.wms.inventory.application.result.MovementView;
import com.wms.inventory.application.result.PageView;
import com.wms.inventory.application.result.ReservationView;
import com.wms.inventory.domain.event.InventoryDomainEvent;
import com.wms.inventory.domain.event.InventoryReleasedEvent;
import com.wms.inventory.domain.model.Inventory;
import com.wms.inventory.domain.model.InventoryMovement;
import com.wms.inventory.domain.model.ReleasedReason;
import com.wms.inventory.domain.model.Reservation;
import com.wms.inventory.domain.model.ReservationLine;
import com.wms.inventory.domain.model.ReservationStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReleaseReservationServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-25T11:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(3600);

    private FakeInventoryRepo invRepo;
    private FakeMovementRepo movementRepo;
    private FakeOutbox outbox;
    private FakeReservationRepo reservationRepo;
    private ReleaseReservationService service;

    @BeforeEach
    void setUp() {
        invRepo = new FakeInventoryRepo();
        movementRepo = new FakeMovementRepo();
        outbox = new FakeOutbox();
        reservationRepo = new FakeReservationRepo();
        service = new ReleaseReservationService(reservationRepo, invRepo, movementRepo, outbox,
                Clock.fixed(NOW, ZoneOffset.UTC), new SimpleMeterRegistry());
    }

    @Test
    void firstReleaseMovesReservedBackToAvailableAndEmitsOnce() {
        UUID invId = seedInventory(70, 30); // available=70, reserved=30
        Reservation reservation = seedReservation(invId, 30);

        ReservationView view = service.release(new ReleaseReservationCommand(
                reservation.id(), ReleasedReason.MANUAL, null, null, "admin"));

        assertThat(view.status()).isEqualTo(ReservationStatus.RELEASED);
        Inventory inv = invRepo.entries.get(invId);
        assertThat(inv.availableQty()).isEqualTo(100);
        assertThat(inv.reservedQty()).isZero();
        assertThat(movementRepo.saved).hasSize(2); // RESERVED -N + AVAILABLE +N
        assertThat(outbox.events).hasSize(1);
        assertThat(outbox.events.get(0)).isInstanceOf(InventoryReleasedEvent.class);
    }

    @Test
    void secondReleaseOnAlreadyReleasedReservationIsIdempotentNoOp() {
        UUID invId = seedInventory(70, 30);
        Reservation reservation = seedReservation(invId, 30);

        // First release: RESERVED → RELEASED, stock restored, one event.
        service.release(new ReleaseReservationCommand(
                reservation.id(), ReleasedReason.MANUAL, null, null, "admin"));

        int availableAfterFirst = invRepo.entries.get(invId).availableQty();
        int reservedAfterFirst = invRepo.entries.get(invId).reservedQty();
        int movementsAfterFirst = movementRepo.saved.size();
        int eventsAfterFirst = outbox.events.size();

        // Second release on the now-RELEASED reservation must be a 200 no-op:
        // no re-mutation of stock, no duplicate inventory.released event.
        ReservationView view = service.release(new ReleaseReservationCommand(
                reservation.id(), ReleasedReason.MANUAL, null, null, "admin"));

        assertThat(view.status()).isEqualTo(ReservationStatus.RELEASED);
        // Quantities UNCHANGED after the 2nd call (the load-bearing property).
        assertThat(invRepo.entries.get(invId).availableQty()).isEqualTo(availableAfterFirst);
        assertThat(invRepo.entries.get(invId).reservedQty()).isEqualTo(reservedAfterFirst);
        // No extra movements, no extra outbox event — exactly ONE inventory.released.
        assertThat(movementRepo.saved).hasSize(movementsAfterFirst);
        assertThat(outbox.events).hasSize(eventsAfterFirst);
        assertThat(outbox.events).hasSize(1);
    }

    private UUID seedInventory(int available, int reserved) {
        UUID id = UUID.randomUUID();
        Inventory inv = Inventory.restore(id, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), null, available, reserved, 0, NOW, 0L,
                NOW, "seed", NOW, "seed");
        invRepo.entries.put(id, inv);
        return id;
    }

    private Reservation seedReservation(UUID inventoryId, int qty) {
        UUID resId = UUID.randomUUID();
        ReservationLine line = new ReservationLine(
                UUID.randomUUID(), resId, inventoryId, UUID.randomUUID(), UUID.randomUUID(), null, qty);
        Reservation r = Reservation.create(
                resId, UUID.randomUUID(), UUID.randomUUID(),
                List.of(line), LATER, NOW, "seed");
        reservationRepo.byId.put(resId, r);
        return r;
    }

    // ---- Fakes ---------------------------------------------------------------

    private static class FakeInventoryRepo implements InventoryRepository {
        final Map<UUID, Inventory> entries = new HashMap<>();
        @Override public Optional<Inventory> findById(UUID id) { return Optional.ofNullable(entries.get(id)); }
        @Override public Optional<Inventory> findByKey(UUID a, UUID b, UUID c) { throw new UnsupportedOperationException(); }
        @Override public java.util.List<Inventory> findAvailableByWarehouseSkuLot(UUID a, UUID b, UUID c) { throw new UnsupportedOperationException(); }
        @Override public Optional<InventoryView> findViewById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public Optional<InventoryView> findViewByKey(UUID a, UUID b, UUID c) { throw new UnsupportedOperationException(); }
        @Override public PageView<InventoryView> listViews(InventoryListCriteria c) { throw new UnsupportedOperationException(); }
        @Override public Inventory insert(Inventory inventory) { entries.put(inventory.id(), inventory); return inventory; }
        @Override public Inventory updateWithVersionCheck(Inventory inventory) { entries.put(inventory.id(), inventory); return inventory; }
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
        @Override public Optional<Reservation> findById(UUID id) { return Optional.ofNullable(byId.get(id)); }
        @Override public Optional<Reservation> findByPickingRequestId(UUID p) { throw new UnsupportedOperationException(); }
        @Override public Reservation insert(Reservation reservation) { byId.put(reservation.id(), reservation); return reservation; }
        @Override public Reservation updateWithVersionCheck(Reservation reservation) { byId.put(reservation.id(), reservation); return reservation; }
        @Override public Optional<ReservationView> findViewById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public PageView<ReservationView> listViews(ReservationListCriteria c) { throw new UnsupportedOperationException(); }
        @Override public List<Reservation> findExpired(Instant asOf, int limit) { throw new UnsupportedOperationException(); }
        @Override public long countActive() { return byId.size(); }
    }
}
