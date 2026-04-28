package com.wms.inventory.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.inventory.application.command.ReceiveStockCommand;
import com.wms.inventory.application.command.ReceiveStockLineCommand;
import com.wms.inventory.application.port.out.InventoryMovementRepository;
import com.wms.inventory.application.port.out.InventoryRepository;
import com.wms.inventory.application.port.out.MasterReadModelPort;
import com.wms.inventory.application.port.out.OutboxWriter;
import com.wms.inventory.application.query.InventoryListCriteria;
import com.wms.inventory.application.result.InventoryView;
import com.wms.inventory.application.result.PageView;
import com.wms.inventory.domain.event.InventoryDomainEvent;
import com.wms.inventory.domain.event.InventoryReceivedEvent;
import com.wms.inventory.domain.exception.MasterRefInactiveException;
import com.wms.inventory.domain.model.Inventory;
import com.wms.inventory.domain.model.InventoryMovement;
import com.wms.inventory.domain.model.MovementType;
import com.wms.inventory.domain.model.masterref.LocationSnapshot;
import com.wms.inventory.domain.model.masterref.LotSnapshot;
import com.wms.inventory.domain.model.masterref.SkuSnapshot;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReceiveStockServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-25T10:00:00Z");
    private static final UUID WAREHOUSE_ID = UUID.randomUUID();
    private static final UUID LOCATION_ID = UUID.randomUUID();
    private static final UUID SKU_ID = UUID.randomUUID();
    private static final UUID LOT_ID = UUID.randomUUID();

    private FakeInventoryRepo invRepo;
    private FakeMovementRepo movementRepo;
    private FakeOutbox outbox;
    private FakeMasterReadModel masterRefs;
    private ReceiveStockService service;

    @BeforeEach
    void setUp() {
        invRepo = new FakeInventoryRepo();
        movementRepo = new FakeMovementRepo();
        outbox = new FakeOutbox();
        masterRefs = new FakeMasterReadModel();
        service = new ReceiveStockService(
                invRepo, movementRepo, outbox, masterRefs,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SimpleMeterRegistry());
    }

    @Test
    void firstReceiveCreatesInventoryRowAndWritesMovementAndOutbox() {
        ReceiveStockLineCommand line = new ReceiveStockLineCommand(LOCATION_ID, SKU_ID, null, 50);
        ReceiveStockCommand cmd = new ReceiveStockCommand(
                UUID.randomUUID(), WAREHOUSE_ID, UUID.randomUUID(), List.of(line), "system:test");

        service.receive(cmd);

        assertThat(invRepo.entries).hasSize(1);
        Inventory inv = invRepo.entries.values().iterator().next();
        assertThat(inv.availableQty()).isEqualTo(50);

        assertThat(movementRepo.saved).hasSize(1);
        InventoryMovement movement = movementRepo.saved.get(0);
        assertThat(movement.movementType()).isEqualTo(MovementType.RECEIVE);
        assertThat(movement.delta()).isEqualTo(50);

        assertThat(outbox.events).hasSize(1);
        InventoryReceivedEvent event = (InventoryReceivedEvent) outbox.events.get(0);
        assertThat(event.lines()).hasSize(1);
        assertThat(event.lines().get(0).availableQtyAfter()).isEqualTo(50);
    }

    @Test
    void existingInventoryRowGetsIncremented() {
        // Seed a persisted-looking row directly via restore (version > 0, qty
        // already at 20). This avoids chaining a domain mutation on the same
        // in-memory object the service will mutate later, which would couple
        // seed-vs-service paths and hide bugs. With version=1 a buggy
        // "always-insert" implementation would also be caught structurally.
        UUID existingId = UUID.randomUUID();
        Inventory existing = Inventory.restore(existingId, WAREHOUSE_ID,
                LOCATION_ID, SKU_ID, null,
                20, 0, 0, NOW, 1L,
                NOW, "actor", NOW, "actor");
        invRepo.entries.put(existing.id(), existing);

        ReceiveStockLineCommand line = new ReceiveStockLineCommand(LOCATION_ID, SKU_ID, null, 30);
        service.receive(new ReceiveStockCommand(
                UUID.randomUUID(), WAREHOUSE_ID, UUID.randomUUID(),
                List.of(line), "system:test"));

        // The service must take the UPDATE branch, not INSERT — this is the
        // load-bearing assertion for AC-3 of TASK-BE-022 ("if a row exists,
        // available_qty += qty"). A buggy implementation that always called
        // insert(...) would now fail deterministically here.
        assertThat(invRepo.updateCalls()).isEqualTo(1);
        assertThat(invRepo.insertCalls()).isEqualTo(0);

        Inventory persisted = invRepo.entries.get(existing.id());
        assertThat(persisted.availableQty()).isEqualTo(50);
    }

    @Test
    void inactiveLocationSnapshotRejectsReceive() {
        masterRefs.locations.put(LOCATION_ID, new LocationSnapshot(
                LOCATION_ID, "WH01-A-01-01-01", WAREHOUSE_ID, UUID.randomUUID(),
                LocationSnapshot.LocationType.STORAGE, LocationSnapshot.Status.INACTIVE,
                NOW, 1L));

        ReceiveStockLineCommand line = new ReceiveStockLineCommand(LOCATION_ID, SKU_ID, null, 50);
        ReceiveStockCommand cmd = new ReceiveStockCommand(
                UUID.randomUUID(), WAREHOUSE_ID, UUID.randomUUID(), List.of(line), "system:test");

        assertThatThrownBy(() -> service.receive(cmd))
                .isInstanceOf(MasterRefInactiveException.class)
                .hasMessageContaining("INACTIVE");
        assertThat(invRepo.entries).isEmpty();
        assertThat(outbox.events).isEmpty();
    }

    @Test
    void expiredLotRejectsReceive() {
        masterRefs.lots.put(LOT_ID, new LotSnapshot(
                LOT_ID, SKU_ID, "L-X", LocalDate.now(),
                LotSnapshot.Status.EXPIRED, NOW, 1L));

        ReceiveStockLineCommand line = new ReceiveStockLineCommand(LOCATION_ID, SKU_ID, LOT_ID, 50);
        assertThatThrownBy(() -> service.receive(new ReceiveStockCommand(
                UUID.randomUUID(), WAREHOUSE_ID, UUID.randomUUID(),
                List.of(line), "system:test")))
                .isInstanceOf(MasterRefInactiveException.class)
                .hasMessageContaining("EXPIRED");
    }

    @Test
    void multiLineEventEmitsOneOutboxEventCoveringAllLines() {
        UUID location2 = UUID.randomUUID();
        ReceiveStockLineCommand l1 = new ReceiveStockLineCommand(LOCATION_ID, SKU_ID, null, 10);
        ReceiveStockLineCommand l2 = new ReceiveStockLineCommand(location2, SKU_ID, null, 20);
        service.receive(new ReceiveStockCommand(
                UUID.randomUUID(), WAREHOUSE_ID, UUID.randomUUID(),
                List.of(l1, l2), "system:test"));

        assertThat(invRepo.entries).hasSize(2);
        assertThat(movementRepo.saved).hasSize(2);
        assertThat(outbox.events).hasSize(1);
        InventoryReceivedEvent event = (InventoryReceivedEvent) outbox.events.get(0);
        assertThat(event.lines()).hasSize(2);
    }

    // ---- Fakes ---------------------------------------------------------------

    private static class FakeInventoryRepo implements InventoryRepository {
        final Map<UUID, Inventory> entries = new HashMap<>();
        private int insertCalls;
        private int updateCalls;

        int insertCalls() { return insertCalls; }
        int updateCalls() { return updateCalls; }

        @Override public Optional<Inventory> findById(UUID id) {
            return Optional.ofNullable(entries.get(id));
        }
        @Override public Optional<Inventory> findByKey(UUID locationId, UUID skuId, UUID lotId) {
            return entries.values().stream()
                    .filter(i -> i.locationId().equals(locationId) && i.skuId().equals(skuId)
                            && java.util.Objects.equals(i.lotId(), lotId))
                    .findFirst();
        }
        @Override public Optional<InventoryView> findViewById(UUID id) {
            throw new UnsupportedOperationException();
        }
        @Override public Optional<InventoryView> findViewByKey(UUID locationId, UUID skuId, UUID lotId) {
            throw new UnsupportedOperationException();
        }
        @Override public PageView<InventoryView> listViews(InventoryListCriteria criteria) {
            throw new UnsupportedOperationException();
        }
        @Override public Inventory insert(Inventory inventory) {
            // Mirror a real adapter: a duplicate id on insert would surface
            // as a primary-key violation. Without this guard the fake would
            // silently accept "always-insert" bugs and pass tests that should
            // distinguish the two paths.
            if (entries.containsKey(inventory.id())) {
                throw new IllegalStateException(
                        "Fake repo: insert called for already-existing id " + inventory.id());
            }
            insertCalls++;
            entries.put(inventory.id(), inventory);
            return inventory;
        }
        @Override public Inventory updateWithVersionCheck(Inventory inventory) {
            updateCalls++;
            entries.put(inventory.id(), inventory);
            return inventory;
        }
    }

    private static class FakeMovementRepo implements InventoryMovementRepository {
        final List<InventoryMovement> saved = new ArrayList<>();

        @Override public void save(InventoryMovement movement) { saved.add(movement); }
        @Override public PageView<com.wms.inventory.application.result.MovementView> list(
                com.wms.inventory.application.query.MovementListCriteria criteria) {
            throw new UnsupportedOperationException();
        }
    }

    private static class FakeOutbox implements OutboxWriter {
        final List<InventoryDomainEvent> events = new ArrayList<>();
        @Override public void write(InventoryDomainEvent event) { events.add(event); }
    }

    private static class FakeMasterReadModel implements MasterReadModelPort {
        final Map<UUID, LocationSnapshot> locations = new HashMap<>();
        final Map<UUID, SkuSnapshot> skus = new HashMap<>();
        final Map<UUID, LotSnapshot> lots = new HashMap<>();

        @Override public Optional<LocationSnapshot> findLocation(UUID id) {
            return Optional.ofNullable(locations.get(id));
        }
        @Override public Optional<SkuSnapshot> findSku(UUID id) {
            return Optional.ofNullable(skus.get(id));
        }
        @Override public Optional<LotSnapshot> findLot(UUID id) {
            return Optional.ofNullable(lots.get(id));
        }
    }
}
