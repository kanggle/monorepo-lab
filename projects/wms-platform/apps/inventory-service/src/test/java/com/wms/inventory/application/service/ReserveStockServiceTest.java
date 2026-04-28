package com.wms.inventory.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.inventory.application.command.ReserveStockCommand;
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
import com.wms.inventory.domain.event.InventoryReservedEvent;
import com.wms.inventory.domain.exception.InventoryNotFoundException;
import com.wms.inventory.domain.model.Inventory;
import com.wms.inventory.domain.model.InventoryMovement;
import com.wms.inventory.domain.model.ReasonCode;
import com.wms.inventory.domain.model.Reservation;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class ReserveStockServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-25T10:00:00Z");

    private FakeInventoryRepo invRepo;
    private FakeMovementRepo movementRepo;
    private FakeOutbox outbox;
    private FakeReservationRepo reservationRepo;
    private ReserveStockService service;

    @BeforeEach
    void setUp() {
        invRepo = new FakeInventoryRepo();
        movementRepo = new FakeMovementRepo();
        outbox = new FakeOutbox();
        reservationRepo = new FakeReservationRepo();
        TransactionTemplate tt = new TransactionTemplate(new NoopTxManager());
        service = new ReserveStockService(reservationRepo, invRepo, movementRepo, outbox,
                tt, Clock.fixed(NOW, ZoneOffset.UTC), new SimpleMeterRegistry());
    }

    @Test
    void successfulReserveCreatesReservationAndUpdatesInventory() {
        UUID invId = seedInventory(100);
        ReserveStockCommand cmd = new ReserveStockCommand(
                UUID.randomUUID(), UUID.randomUUID(),
                List.of(new ReserveStockCommand.Line(invId, 30)),
                86400, null, "u", null);

        ReservationView view = service.reserve(cmd);

        Inventory inv = invRepo.entries.get(invId);
        assertThat(inv.availableQty()).isEqualTo(70);
        assertThat(inv.reservedQty()).isEqualTo(30);
        assertThat(reservationRepo.byId.get(view.id()).status())
                .isEqualTo(com.wms.inventory.domain.model.ReservationStatus.RESERVED);
        assertThat(movementRepo.saved).hasSize(2); // AVAILABLE -N + RESERVED +N
        assertThat(outbox.events).hasSize(1);
        assertThat(outbox.events.get(0)).isInstanceOf(InventoryReservedEvent.class);
    }

    @Test
    void duplicatePickingRequestReturnsExistingReservation() {
        UUID invId = seedInventory(100);
        UUID pickingRequestId = UUID.randomUUID();
        ReserveStockCommand cmd = new ReserveStockCommand(
                pickingRequestId, UUID.randomUUID(),
                List.of(new ReserveStockCommand.Line(invId, 30)),
                86400, null, "u", null);

        ReservationView first = service.reserve(cmd);
        // Re-issue the same command — should return the existing reservation
        // and NOT increment counters (the pickingRequestId lookup short-circuits).
        ReservationView second = service.reserve(cmd);

        assertThat(second.id()).isEqualTo(first.id());
        // Inventory was reserved exactly once.
        assertThat(invRepo.entries.get(invId).availableQty()).isEqualTo(70);
        assertThat(outbox.events).hasSize(1);
    }

    @Test
    void unknownInventoryThrows() {
        ReserveStockCommand cmd = new ReserveStockCommand(
                UUID.randomUUID(), UUID.randomUUID(),
                List.of(new ReserveStockCommand.Line(UUID.randomUUID(), 5)),
                86400, null, "u", null);
        assertThatThrownBy(() -> service.reserve(cmd))
                .isInstanceOf(InventoryNotFoundException.class);
    }

    @Test
    void optimisticLockRetriesUpToThreeTimes() {
        UUID invId = seedInventory(100);
        invRepo.failuresRemaining = 2; // First two attempts collide; third succeeds.

        ReserveStockCommand cmd = new ReserveStockCommand(
                UUID.randomUUID(), UUID.randomUUID(),
                List.of(new ReserveStockCommand.Line(invId, 30)),
                86400, null, "u", null);
        ReservationView view = service.reserve(cmd);

        assertThat(view.status())
                .isEqualTo(com.wms.inventory.domain.model.ReservationStatus.RESERVED);
        assertThat(invRepo.updateAttempts).isEqualTo(3); // attempted 3 times
    }

    @Test
    void exhaustedRetriesPropagateConflict() {
        UUID invId = seedInventory(100);
        invRepo.failuresRemaining = 5; // All attempts collide.

        ReserveStockCommand cmd = new ReserveStockCommand(
                UUID.randomUUID(), UUID.randomUUID(),
                List.of(new ReserveStockCommand.Line(invId, 30)),
                86400, null, "u", null);
        assertThatThrownBy(() -> service.reserve(cmd))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void linesAreLoadedInDeterministicIdOrder() {
        UUID lowId = new UUID(0, 1);
        UUID highId = new UUID(0, 99);
        seedInventoryWithId(lowId, 100);
        seedInventoryWithId(highId, 100);

        ReserveStockCommand cmd = new ReserveStockCommand(
                UUID.randomUUID(), UUID.randomUUID(),
                // Submit in reverse order; service should sort ascending
                List.of(new ReserveStockCommand.Line(highId, 5),
                        new ReserveStockCommand.Line(lowId, 10)),
                86400, null, "u", null);
        service.reserve(cmd);

        assertThat(invRepo.findByIdOrder).containsExactly(lowId, highId);
    }

    private UUID seedInventory(int qty) {
        UUID id = UUID.randomUUID();
        seedInventoryWithId(id, qty);
        return id;
    }

    private void seedInventoryWithId(UUID id, int qty) {
        Inventory inv = Inventory.restore(id, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), null, qty, 0, 0, NOW, 0L,
                NOW, "seed", NOW, "seed");
        invRepo.entries.put(id, inv);
    }

    // ---- Fakes ---------------------------------------------------------------

    private static class FakeInventoryRepo implements InventoryRepository {
        final Map<UUID, Inventory> entries = new HashMap<>();
        final List<UUID> findByIdOrder = new ArrayList<>();
        int failuresRemaining = 0;
        int updateAttempts = 0;

        @Override public Optional<Inventory> findById(UUID id) {
            findByIdOrder.add(id);
            return Optional.ofNullable(entries.get(id));
        }
        @Override public Optional<Inventory> findByKey(UUID locationId, UUID skuId, UUID lotId) {
            return entries.values().stream()
                    .filter(i -> i.locationId().equals(locationId) && i.skuId().equals(skuId))
                    .findFirst();
        }
        @Override public Optional<InventoryView> findViewById(UUID id) { throw new UnsupportedOperationException(); }
        @Override public Optional<InventoryView> findViewByKey(UUID a, UUID b, UUID c) { throw new UnsupportedOperationException(); }
        @Override public PageView<InventoryView> listViews(InventoryListCriteria c) { throw new UnsupportedOperationException(); }
        @Override public Inventory insert(Inventory inventory) {
            entries.put(inventory.id(), inventory);
            return inventory;
        }
        @Override public Inventory updateWithVersionCheck(Inventory inventory) {
            updateAttempts++;
            if (failuresRemaining > 0) {
                failuresRemaining--;
                // Re-seed fresh state so the next attempt's read doesn't see
                // the in-memory mutation.
                Inventory fresh = Inventory.restore(inventory.id(), inventory.warehouseId(),
                        inventory.locationId(), inventory.skuId(), inventory.lotId(),
                        100, 0, 0, NOW, 0L, NOW, "seed", NOW, "seed");
                entries.put(inventory.id(), fresh);
                throw new OptimisticLockingFailureException("forced");
            }
            entries.put(inventory.id(), inventory);
            return inventory;
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

        @Override public Optional<Reservation> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }
        @Override public Optional<Reservation> findByPickingRequestId(UUID pickingRequestId) {
            return Optional.ofNullable(byPickingRequest.get(pickingRequestId));
        }
        @Override public Reservation insert(Reservation reservation) {
            byId.put(reservation.id(), reservation);
            byPickingRequest.put(reservation.pickingRequestId(), reservation);
            return reservation;
        }
        @Override public Reservation updateWithVersionCheck(Reservation reservation) {
            byId.put(reservation.id(), reservation);
            return reservation;
        }
        @Override public Optional<ReservationView> findViewById(UUID id) {
            return findById(id).map(ReservationView::from);
        }
        @Override public PageView<ReservationView> listViews(ReservationListCriteria c) {
            List<ReservationView> views = byId.values().stream()
                    .sorted(Comparator.comparing(Reservation::updatedAt).reversed())
                    .map(ReservationView::from).toList();
            return PageView.of(views, 0, views.size(), views.size(), "updatedAt,desc");
        }
        @Override public List<Reservation> findExpired(Instant asOf, int limit) { throw new UnsupportedOperationException(); }
        @Override public long countActive() { return byId.size(); }
    }

    /** Lightweight TX manager that just runs the callback without a real transaction. */
    private static class NoopTxManager implements PlatformTransactionManager {
        @Override public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new org.springframework.transaction.support.SimpleTransactionStatus();
        }
        @Override public void commit(TransactionStatus status) { }
        @Override public void rollback(TransactionStatus status) { }
    }
}
