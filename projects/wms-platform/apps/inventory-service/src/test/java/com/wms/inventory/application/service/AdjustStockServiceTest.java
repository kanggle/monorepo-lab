package com.wms.inventory.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.inventory.adapter.out.alert.InMemoryLowStockAlertDebounceAdapter;
import com.wms.inventory.adapter.out.alert.InMemoryLowStockThresholdAdapter;
import com.wms.inventory.application.command.AdjustStockCommand;
import com.wms.inventory.application.command.AdjustStockCommand.AdjustOperation;
import com.wms.inventory.application.port.out.InventoryMovementRepository;
import com.wms.inventory.application.port.out.InventoryRepository;
import com.wms.inventory.application.port.out.MasterReadModelPort;
import com.wms.inventory.application.port.out.OutboxWriter;
import com.wms.inventory.application.port.out.StockAdjustmentRepository;
import com.wms.inventory.application.query.AdjustmentListCriteria;
import com.wms.inventory.application.query.InventoryListCriteria;
import com.wms.inventory.application.query.MovementListCriteria;
import com.wms.inventory.application.result.AdjustmentResult;
import com.wms.inventory.application.result.AdjustmentView;
import com.wms.inventory.application.result.InventoryView;
import com.wms.inventory.application.result.MovementView;
import com.wms.inventory.application.result.PageView;
import com.wms.inventory.domain.event.InventoryAdjustedEvent;
import com.wms.inventory.domain.event.InventoryDomainEvent;
import com.wms.inventory.domain.event.InventoryLowStockDetectedEvent;
import com.wms.inventory.domain.exception.InsufficientStockException;
import com.wms.inventory.domain.exception.InventoryNotFoundException;
import com.wms.inventory.domain.exception.InventoryValidationException;
import com.wms.inventory.domain.model.Bucket;
import com.wms.inventory.domain.model.Inventory;
import com.wms.inventory.domain.model.InventoryMovement;
import com.wms.inventory.domain.model.MovementType;
import com.wms.inventory.domain.model.ReasonCode;
import com.wms.inventory.domain.model.StockAdjustment;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class AdjustStockServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-25T11:00:00Z");
    private static final UUID WAREHOUSE = UUID.randomUUID();
    private static final UUID LOCATION = UUID.randomUUID();
    private static final UUID SKU = UUID.randomUUID();

    private FakeInventoryRepo invRepo;
    private FakeMovementRepo movementRepo;
    private FakeAdjustmentRepo adjustmentRepo;
    private FakeOutbox outbox;
    private FakeMasterReadModel masterReadModel;
    private InMemoryLowStockThresholdAdapter thresholdAdapter;
    private InMemoryLowStockAlertDebounceAdapter debounceAdapter;
    private LowStockDetectionService lowStockDetection;
    private AdjustStockService service;

    @BeforeEach
    void setUp() {
        invRepo = new FakeInventoryRepo();
        movementRepo = new FakeMovementRepo();
        adjustmentRepo = new FakeAdjustmentRepo();
        outbox = new FakeOutbox();
        masterReadModel = new FakeMasterReadModel();
        thresholdAdapter = new InMemoryLowStockThresholdAdapter();
        debounceAdapter = new InMemoryLowStockAlertDebounceAdapter(
                Clock.fixed(NOW, ZoneOffset.UTC));
        lowStockDetection = new LowStockDetectionService(
                thresholdAdapter, debounceAdapter, masterReadModel, outbox);
        service = new AdjustStockService(
                invRepo, movementRepo, adjustmentRepo, outbox, masterReadModel,
                lowStockDetection, Clock.fixed(NOW, ZoneOffset.UTC),
                new SimpleMeterRegistry());
    }

    @Test
    void regularAdjustWritesAdjustmentMovementAndOutbox() {
        UUID invId = seed(100, 0, 0);
        AdjustStockCommand cmd = new AdjustStockCommand(
                AdjustOperation.REGULAR, invId, Bucket.AVAILABLE, -10,
                ReasonCode.ADJUSTMENT_LOSS, "lost in transit", "actor", "idem-1");

        AdjustmentResult result = service.adjust(cmd);

        assertThat(invRepo.entries.get(invId).availableQty()).isEqualTo(90);
        assertThat(adjustmentRepo.saved).hasSize(1);
        assertThat(movementRepo.saved).hasSize(1);
        assertThat(movementRepo.saved.get(0).movementType()).isEqualTo(MovementType.ADJUSTMENT);
        assertThat(outbox.events).anyMatch(e -> e instanceof InventoryAdjustedEvent);
        assertThat(result.adjustment().delta()).isEqualTo(-10);
    }

    @Test
    void regularAdjustNegativeUnderflowThrowsAndLeavesNoSideEffects() {
        UUID invId = seed(5, 0, 0);
        AdjustStockCommand cmd = new AdjustStockCommand(
                AdjustOperation.REGULAR, invId, Bucket.AVAILABLE, -10,
                ReasonCode.ADJUSTMENT_LOSS, "too much", "actor", "idem-2");

        assertThatThrownBy(() -> service.adjust(cmd))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(adjustmentRepo.saved).isEmpty();
        assertThat(movementRepo.saved).isEmpty();
        assertThat(outbox.events).isEmpty();
    }

    @Test
    void regularAdjustReservedBucketAdminAllowed() {
        UUID invId = seed(50, 30, 0);
        AdjustStockCommand cmd = new AdjustStockCommand(
                AdjustOperation.REGULAR, invId, Bucket.RESERVED, -5,
                ReasonCode.ADJUSTMENT_LOSS, "manual reservation correction",
                "admin", "idem-res-admin",
                Set.of("ROLE_INVENTORY_ADMIN"));

        AdjustmentResult result = service.adjust(cmd);

        assertThat(invRepo.entries.get(invId).reservedQty()).isEqualTo(25);
        assertThat(result.adjustment().delta()).isEqualTo(-5);
        assertThat(result.adjustment().bucket()).isEqualTo(Bucket.RESERVED);
    }

    @Test
    void regularAdjustReservedBucketNonAdminThrowsAccessDenied() {
        UUID invId = seed(50, 30, 0);
        AdjustStockCommand cmd = new AdjustStockCommand(
                AdjustOperation.REGULAR, invId, Bucket.RESERVED, -5,
                ReasonCode.ADJUSTMENT_LOSS, "non-admin attempt",
                "writer", "idem-res-write",
                Set.of("ROLE_INVENTORY_WRITE"));

        assertThatThrownBy(() -> service.adjust(cmd))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("INVENTORY_ADMIN");
        assertThat(adjustmentRepo.saved).isEmpty();
        assertThat(movementRepo.saved).isEmpty();
        assertThat(outbox.events).isEmpty();
    }

    @Test
    void regularAdjustAvailableBucketAdminAllowed() {
        UUID invId = seed(100, 0, 0);
        AdjustStockCommand cmd = new AdjustStockCommand(
                AdjustOperation.REGULAR, invId, Bucket.AVAILABLE, -10,
                ReasonCode.ADJUSTMENT_LOSS, "admin doing routine adjust",
                "admin", "idem-avail-admin",
                Set.of("ROLE_INVENTORY_ADMIN"));

        AdjustmentResult result = service.adjust(cmd);

        assertThat(invRepo.entries.get(invId).availableQty()).isEqualTo(90);
        assertThat(result.adjustment().delta()).isEqualTo(-10);
    }

    @Test
    void regularAdjustAvailableBucketNonAdminAllowed() {
        UUID invId = seed(100, 0, 0);
        AdjustStockCommand cmd = new AdjustStockCommand(
                AdjustOperation.REGULAR, invId, Bucket.AVAILABLE, -10,
                ReasonCode.ADJUSTMENT_LOSS, "writer doing routine adjust",
                "writer", "idem-avail-write",
                Set.of("ROLE_INVENTORY_WRITE"));

        AdjustmentResult result = service.adjust(cmd);

        assertThat(invRepo.entries.get(invId).availableQty()).isEqualTo(90);
        assertThat(result.adjustment().delta()).isEqualTo(-10);
    }

    @Test
    void regularAdjustZeroDeltaRejected() {
        UUID invId = seed(100, 0, 0);
        AdjustStockCommand cmd = new AdjustStockCommand(
                AdjustOperation.REGULAR, invId, Bucket.AVAILABLE, 0,
                ReasonCode.ADJUSTMENT_FOUND, "no-op", "actor", "idem-3");
        assertThatThrownBy(() -> service.adjust(cmd))
                .isInstanceOf(InventoryValidationException.class);
    }

    @Test
    void unknownInventoryThrows() {
        AdjustStockCommand cmd = new AdjustStockCommand(
                AdjustOperation.REGULAR, UUID.randomUUID(), Bucket.AVAILABLE, 5,
                ReasonCode.ADJUSTMENT_FOUND, "found", "actor", "idem-4");
        assertThatThrownBy(() -> service.adjust(cmd))
                .isInstanceOf(InventoryNotFoundException.class);
    }

    @Test
    void markDamagedWritesTwoMovementsAndOneAdjustment() {
        UUID invId = seed(50, 0, 0);
        AdjustStockCommand cmd = new AdjustStockCommand(
                AdjustOperation.MARK_DAMAGED, invId, Bucket.AVAILABLE, 8,
                ReasonCode.ADJUSTMENT_DAMAGE, "package torn", "actor", "idem-5");

        service.adjust(cmd);

        Inventory after = invRepo.entries.get(invId);
        assertThat(after.availableQty()).isEqualTo(42);
        assertThat(after.damagedQty()).isEqualTo(8);
        assertThat(adjustmentRepo.saved).hasSize(1);
        assertThat(movementRepo.saved).hasSize(2);
        assertThat(movementRepo.saved.get(0).movementType()).isEqualTo(MovementType.DAMAGE_MARK);
    }

    @Test
    void writeOffDamagedDecrementsDamaged() {
        UUID invId = seed(50, 0, 20);
        AdjustStockCommand cmd = new AdjustStockCommand(
                AdjustOperation.WRITE_OFF_DAMAGED, invId, Bucket.DAMAGED, 5,
                ReasonCode.DAMAGE_WRITE_OFF, "complete write-off", "admin", "idem-6");

        service.adjust(cmd);

        Inventory after = invRepo.entries.get(invId);
        assertThat(after.damagedQty()).isEqualTo(15);
        assertThat(after.availableQty()).isEqualTo(50);
        assertThat(movementRepo.saved.get(0).movementType()).isEqualTo(MovementType.DAMAGE_WRITE_OFF);
    }

    @Test
    void availableMutationBelowThresholdFiresLowStockAlert() {
        thresholdAdapter.setDefaultThreshold(20);
        UUID invId = seed(25, 0, 0);
        AdjustStockCommand cmd = new AdjustStockCommand(
                AdjustOperation.REGULAR, invId, Bucket.AVAILABLE, -10,
                ReasonCode.ADJUSTMENT_LOSS, "loss", "actor", "idem-7");

        service.adjust(cmd);

        assertThat(outbox.events.stream().anyMatch(e -> e instanceof InventoryLowStockDetectedEvent))
                .as("low-stock event emitted").isTrue();
    }

    @Test
    void availableMutationAtOrAboveThresholdNoAlert() {
        thresholdAdapter.setDefaultThreshold(10);
        UUID invId = seed(50, 0, 0);
        AdjustStockCommand cmd = new AdjustStockCommand(
                AdjustOperation.REGULAR, invId, Bucket.AVAILABLE, -5,
                ReasonCode.ADJUSTMENT_LOSS, "loss", "actor", "idem-8");

        service.adjust(cmd);

        assertThat(outbox.events.stream().anyMatch(e -> e instanceof InventoryLowStockDetectedEvent))
                .as("no low-stock event").isFalse();
    }

    @Test
    void debouncedRepeatNoSecondAlert() {
        thresholdAdapter.setDefaultThreshold(20);
        UUID invId = seed(25, 0, 0);
        service.adjust(new AdjustStockCommand(
                AdjustOperation.REGULAR, invId, Bucket.AVAILABLE, -10,
                ReasonCode.ADJUSTMENT_LOSS, "first dip", "actor", "idem-9"));
        long firstAlertCount = outbox.events.stream()
                .filter(e -> e instanceof InventoryLowStockDetectedEvent).count();
        service.adjust(new AdjustStockCommand(
                AdjustOperation.REGULAR, invId, Bucket.AVAILABLE, -1,
                ReasonCode.ADJUSTMENT_LOSS, "second dip", "actor", "idem-10"));
        long secondAlertCount = outbox.events.stream()
                .filter(e -> e instanceof InventoryLowStockDetectedEvent).count();
        assertThat(firstAlertCount).isEqualTo(1);
        assertThat(secondAlertCount).isEqualTo(1);  // unchanged
    }

    private UUID seed(int available, int reserved, int damaged) {
        UUID id = UUID.randomUUID();
        Inventory inv = Inventory.restore(id, WAREHOUSE, LOCATION, SKU, null,
                available, reserved, damaged, NOW, 0L,
                NOW, "seed", NOW, "seed");
        invRepo.entries.put(id, inv);
        return id;
    }

    // ---- Fakes ---------------------------------------------------------------

    private static class FakeInventoryRepo implements InventoryRepository {
        final Map<UUID, Inventory> entries = new HashMap<>();
        @Override public Optional<Inventory> findById(UUID id) { return Optional.ofNullable(entries.get(id)); }
        @Override public Optional<Inventory> findByKey(UUID a, UUID b, UUID c) { throw new UnsupportedOperationException(); }
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

    private static class FakeAdjustmentRepo implements StockAdjustmentRepository {
        final List<StockAdjustment> saved = new ArrayList<>();
        @Override public StockAdjustment insert(StockAdjustment a) { saved.add(a); return a; }
        @Override public Optional<StockAdjustment> findById(UUID id) {
            return saved.stream().filter(a -> a.id().equals(id)).findFirst();
        }
        @Override public PageView<AdjustmentView> list(AdjustmentListCriteria c) { throw new UnsupportedOperationException(); }
    }

    private static class FakeOutbox implements OutboxWriter {
        final List<InventoryDomainEvent> events = new ArrayList<>();
        @Override public void write(InventoryDomainEvent event) { events.add(event); }
    }

    private static class FakeMasterReadModel implements MasterReadModelPort {
        @Override public Optional<LocationSnapshot> findLocation(UUID id) { return Optional.empty(); }
        @Override public Optional<SkuSnapshot> findSku(UUID id) { return Optional.empty(); }
        @Override public Optional<LotSnapshot> findLot(UUID id) { return Optional.empty(); }
    }
}
