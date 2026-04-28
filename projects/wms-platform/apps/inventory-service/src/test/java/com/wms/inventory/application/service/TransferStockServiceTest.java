package com.wms.inventory.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.inventory.adapter.out.alert.InMemoryLowStockAlertDebounceAdapter;
import com.wms.inventory.adapter.out.alert.InMemoryLowStockThresholdAdapter;
import com.wms.inventory.application.command.TransferStockCommand;
import com.wms.inventory.application.port.out.InventoryMovementRepository;
import com.wms.inventory.application.port.out.InventoryRepository;
import com.wms.inventory.application.port.out.MasterReadModelPort;
import com.wms.inventory.application.port.out.OutboxWriter;
import com.wms.inventory.application.port.out.StockTransferRepository;
import com.wms.inventory.application.query.InventoryListCriteria;
import com.wms.inventory.application.query.MovementListCriteria;
import com.wms.inventory.application.query.TransferListCriteria;
import com.wms.inventory.application.result.InventoryView;
import com.wms.inventory.application.result.MovementView;
import com.wms.inventory.application.result.PageView;
import com.wms.inventory.application.result.TransferResult;
import com.wms.inventory.application.result.TransferView;
import com.wms.inventory.domain.event.InventoryDomainEvent;
import com.wms.inventory.domain.event.InventoryTransferredEvent;
import com.wms.inventory.domain.exception.InsufficientStockException;
import com.wms.inventory.domain.exception.InventoryValidationException;
import com.wms.inventory.domain.exception.MasterRefInactiveException;
import com.wms.inventory.domain.exception.TransferSameLocationException;
import com.wms.inventory.domain.model.Inventory;
import com.wms.inventory.domain.model.InventoryMovement;
import com.wms.inventory.domain.model.MovementType;
import com.wms.inventory.domain.model.StockTransfer;
import com.wms.inventory.domain.model.TransferReasonCode;
import com.wms.inventory.domain.model.masterref.LocationSnapshot;
import com.wms.inventory.domain.model.masterref.LotSnapshot;
import com.wms.inventory.domain.model.masterref.SkuSnapshot;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransferStockServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-25T11:00:00Z");
    private static final UUID WAREHOUSE = UUID.randomUUID();
    private static final UUID OTHER_WAREHOUSE = UUID.randomUUID();
    private static final UUID SKU = UUID.randomUUID();

    private FakeInventoryRepo invRepo;
    private FakeMovementRepo movementRepo;
    private FakeTransferRepo transferRepo;
    private FakeOutbox outbox;
    private FakeMasterReadModel masterReadModel;
    private LowStockDetectionService lowStockDetection;
    private TransferStockService service;

    @BeforeEach
    void setUp() {
        invRepo = new FakeInventoryRepo();
        movementRepo = new FakeMovementRepo();
        transferRepo = new FakeTransferRepo();
        outbox = new FakeOutbox();
        masterReadModel = new FakeMasterReadModel();
        lowStockDetection = new LowStockDetectionService(
                new InMemoryLowStockThresholdAdapter(),
                new InMemoryLowStockAlertDebounceAdapter(Clock.fixed(NOW, ZoneOffset.UTC)),
                masterReadModel, outbox);
        service = new TransferStockService(
                invRepo, movementRepo, transferRepo, outbox, masterReadModel,
                lowStockDetection, Clock.fixed(NOW, ZoneOffset.UTC),
                new SimpleMeterRegistry());
    }

    @Test
    void transferToExistingTargetUpdatesBothRowsAtomically() {
        UUID source = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        masterReadModel.locations.put(source, locationSnapshot(source, WAREHOUSE));
        masterReadModel.locations.put(target, locationSnapshot(target, WAREHOUSE));
        seed(source, 100, 0, 0);
        seed(target, 5, 0, 0);

        TransferStockCommand cmd = new TransferStockCommand(
                source, target, SKU, null, 30,
                TransferReasonCode.TRANSFER_INTERNAL, "rebalance",
                "actor", "idem-1");
        TransferResult result = service.transfer(cmd);

        assertThat(invRepo.byKey(source).availableQty()).isEqualTo(70);
        assertThat(invRepo.byKey(target).availableQty()).isEqualTo(35);
        assertThat(movementRepo.saved).hasSize(2);
        assertThat(movementRepo.saved.stream().map(InventoryMovement::movementType))
                .contains(MovementType.TRANSFER_OUT, MovementType.TRANSFER_IN);
        assertThat(transferRepo.saved).hasSize(1);
        assertThat(outbox.events).anyMatch(e -> e instanceof InventoryTransferredEvent);
        assertThat(result.target().wasCreated()).isFalse();
    }

    @Test
    void transferToBrandNewTargetUpsertsAndMarksWasCreated() {
        UUID source = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        masterReadModel.locations.put(source, locationSnapshot(source, WAREHOUSE));
        masterReadModel.locations.put(target, locationSnapshot(target, WAREHOUSE));
        seed(source, 100, 0, 0);

        TransferStockCommand cmd = new TransferStockCommand(
                source, target, SKU, null, 40,
                TransferReasonCode.TRANSFER_INTERNAL, null, "actor", "idem-2");
        TransferResult result = service.transfer(cmd);

        assertThat(invRepo.byKey(source).availableQty()).isEqualTo(60);
        assertThat(invRepo.byKey(target).availableQty()).isEqualTo(40);
        assertThat(result.target().wasCreated()).isTrue();
    }

    @Test
    void sameLocationThrows() {
        UUID location = UUID.randomUUID();
        TransferStockCommand cmd = new TransferStockCommand(
                location, location, SKU, null, 5,
                TransferReasonCode.TRANSFER_INTERNAL, null, "actor", "idem-3");
        assertThatThrownBy(() -> service.transfer(cmd))
                .isInstanceOf(TransferSameLocationException.class);
    }

    @Test
    void crossWarehouseRejected() {
        UUID source = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        masterReadModel.locations.put(source, locationSnapshot(source, WAREHOUSE));
        masterReadModel.locations.put(target, locationSnapshot(target, OTHER_WAREHOUSE));
        seed(source, 100, 0, 0);

        TransferStockCommand cmd = new TransferStockCommand(
                source, target, SKU, null, 10,
                TransferReasonCode.TRANSFER_INTERNAL, null, "actor", "idem-4");
        assertThatThrownBy(() -> service.transfer(cmd))
                .isInstanceOf(InventoryValidationException.class);
    }

    @Test
    void insufficientSourceStockThrows() {
        UUID source = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        masterReadModel.locations.put(source, locationSnapshot(source, WAREHOUSE));
        masterReadModel.locations.put(target, locationSnapshot(target, WAREHOUSE));
        seed(source, 5, 0, 0);

        TransferStockCommand cmd = new TransferStockCommand(
                source, target, SKU, null, 10,
                TransferReasonCode.TRANSFER_INTERNAL, null, "actor", "idem-5");
        assertThatThrownBy(() -> service.transfer(cmd))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void inactiveSourceLocationRejected() {
        UUID source = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        masterReadModel.locations.put(source, new LocationSnapshot(
                source, "L-A", WAREHOUSE, UUID.randomUUID(),
                LocationSnapshot.LocationType.STORAGE, LocationSnapshot.Status.INACTIVE,
                NOW, 1L));
        masterReadModel.locations.put(target, locationSnapshot(target, WAREHOUSE));
        seed(source, 100, 0, 0);

        TransferStockCommand cmd = new TransferStockCommand(
                source, target, SKU, null, 10,
                TransferReasonCode.TRANSFER_INTERNAL, null, "actor", "idem-6");
        assertThatThrownBy(() -> service.transfer(cmd))
                .isInstanceOf(MasterRefInactiveException.class);
    }

    private void seed(UUID location, int available, int reserved, int damaged) {
        Inventory inv = Inventory.restore(UUID.randomUUID(), WAREHOUSE, location, SKU, null,
                available, reserved, damaged, NOW, 0L,
                NOW, "seed", NOW, "seed");
        invRepo.entries.put(inv.id(), inv);
    }

    private static LocationSnapshot locationSnapshot(UUID id, UUID warehouseId) {
        return new LocationSnapshot(id, "L-" + id.toString().substring(0, 4),
                warehouseId, UUID.randomUUID(),
                LocationSnapshot.LocationType.STORAGE, LocationSnapshot.Status.ACTIVE,
                NOW, 1L);
    }

    // ---- Fakes ---------------------------------------------------------------

    private static class FakeInventoryRepo implements InventoryRepository {
        final Map<UUID, Inventory> entries = new HashMap<>();

        Inventory byKey(UUID locationId) {
            return entries.values().stream()
                    .filter(i -> i.locationId().equals(locationId))
                    .findFirst().orElseThrow();
        }

        @Override public Optional<Inventory> findById(UUID id) { return Optional.ofNullable(entries.get(id)); }
        @Override public Optional<Inventory> findByKey(UUID locationId, UUID skuId, UUID lotId) {
            return entries.values().stream()
                    .filter(i -> i.locationId().equals(locationId)
                            && i.skuId().equals(skuId)
                            && Objects.equals(i.lotId(), lotId))
                    .findFirst();
        }
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

    private static class FakeTransferRepo implements StockTransferRepository {
        final List<StockTransfer> saved = new ArrayList<>();
        @Override public StockTransfer insert(StockTransfer t) { saved.add(t); return t; }
        @Override public Optional<StockTransfer> findById(UUID id) {
            return saved.stream().filter(t -> t.id().equals(id)).findFirst();
        }
        @Override public PageView<TransferView> list(TransferListCriteria c) { throw new UnsupportedOperationException(); }
    }

    private static class FakeOutbox implements OutboxWriter {
        final List<InventoryDomainEvent> events = new ArrayList<>();
        @Override public void write(InventoryDomainEvent event) { events.add(event); }
    }

    private static class FakeMasterReadModel implements MasterReadModelPort {
        final Map<UUID, LocationSnapshot> locations = new HashMap<>();
        @Override public Optional<LocationSnapshot> findLocation(UUID id) { return Optional.ofNullable(locations.get(id)); }
        @Override public Optional<SkuSnapshot> findSku(UUID id) { return Optional.empty(); }
        @Override public Optional<LotSnapshot> findLot(UUID id) { return Optional.empty(); }
    }
}
