package com.wms.inventory.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.inventory.adapter.out.alert.InMemoryLowStockAlertDebounceAdapter;
import com.wms.inventory.adapter.out.alert.InMemoryLowStockThresholdAdapter;
import com.wms.inventory.application.port.out.MasterReadModelPort;
import com.wms.inventory.application.port.out.OutboxWriter;
import com.wms.inventory.domain.event.InventoryDomainEvent;
import com.wms.inventory.domain.event.InventoryLowStockDetectedEvent;
import com.wms.inventory.domain.model.Inventory;
import com.wms.inventory.domain.model.masterref.LocationSnapshot;
import com.wms.inventory.domain.model.masterref.LotSnapshot;
import com.wms.inventory.domain.model.masterref.SkuSnapshot;
import com.wms.inventory.domain.model.masterref.WarehouseSnapshot;
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

class LowStockDetectionServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-25T11:00:00Z");
    private static final UUID WAREHOUSE = UUID.randomUUID();
    private static final UUID SKU = UUID.randomUUID();

    private InMemoryLowStockThresholdAdapter thresholdAdapter;
    private InMemoryLowStockAlertDebounceAdapter debounceAdapter;
    private FakeOutbox outbox;
    private FakeMasterReadModel masterReadModel;
    private LowStockDetectionService service;

    @BeforeEach
    void setUp() {
        thresholdAdapter = new InMemoryLowStockThresholdAdapter();
        debounceAdapter = new InMemoryLowStockAlertDebounceAdapter(
                Clock.fixed(NOW, ZoneOffset.UTC));
        outbox = new FakeOutbox();
        masterReadModel = new FakeMasterReadModel();
        service = new LowStockDetectionService(
                thresholdAdapter, debounceAdapter, masterReadModel, outbox);
    }

    @Test
    void thresholdAbsentNoAlert() {
        Inventory inv = sample(5);
        service.evaluate(inv, "inventory.adjusted", null, NOW, "actor");
        assertThat(outbox.events).isEmpty();
    }

    @Test
    void aboveThresholdNoAlert() {
        thresholdAdapter.setDefaultThreshold(10);
        Inventory inv = sample(50);
        service.evaluate(inv, "inventory.adjusted", null, NOW, "actor");
        assertThat(outbox.events).isEmpty();
    }

    @Test
    void belowThresholdFiresOnceThenDebounced() {
        thresholdAdapter.setDefaultThreshold(10);
        Inventory inv = sample(5);

        service.evaluate(inv, "inventory.adjusted", null, NOW, "actor");
        service.evaluate(inv, "inventory.adjusted", null, NOW, "actor");

        long alertCount = outbox.events.stream()
                .filter(e -> e instanceof InventoryLowStockDetectedEvent).count();
        assertThat(alertCount).isEqualTo(1);
    }

    @Test
    void payloadCarriesThresholdAndAvailable() {
        thresholdAdapter.setDefaultThreshold(20);
        Inventory inv = sample(5);
        service.evaluate(inv, "inventory.transferred", UUID.randomUUID(), NOW, "actor");
        InventoryLowStockDetectedEvent fired = outbox.events.stream()
                .filter(e -> e instanceof InventoryLowStockDetectedEvent)
                .map(e -> (InventoryLowStockDetectedEvent) e)
                .findFirst().orElseThrow();
        assertThat(fired.threshold()).isEqualTo(20);
        assertThat(fired.availableQty()).isEqualTo(5);
        assertThat(fired.triggeringEventType()).isEqualTo("inventory.transferred");
    }

    @Test
    void payloadCarriesWarehouseCode() {
        // ADR-MONO-050 D9: the alert must carry the warehouse CODE (resolved from the
        // warehouse master read-model), not just the uuid, so scm can address the PO.
        thresholdAdapter.setDefaultThreshold(20);
        masterReadModel.putWarehouse(WAREHOUSE, "WH-SEOUL-01");
        Inventory inv = sample(5);
        service.evaluate(inv, "inventory.adjusted", null, NOW, "actor");
        InventoryLowStockDetectedEvent fired = outbox.events.stream()
                .filter(e -> e instanceof InventoryLowStockDetectedEvent)
                .map(e -> (InventoryLowStockDetectedEvent) e)
                .findFirst().orElseThrow();
        assertThat(fired.warehouseCode()).isEqualTo("WH-SEOUL-01");
    }

    @Test
    void warehouseCodeNullWhenSnapshotAbsent() {
        // Best-effort enrichment: a missing warehouse snapshot must not suppress the alert.
        thresholdAdapter.setDefaultThreshold(20);
        Inventory inv = sample(5);
        service.evaluate(inv, "inventory.adjusted", null, NOW, "actor");
        InventoryLowStockDetectedEvent fired = outbox.events.stream()
                .filter(e -> e instanceof InventoryLowStockDetectedEvent)
                .map(e -> (InventoryLowStockDetectedEvent) e)
                .findFirst().orElseThrow();
        assertThat(fired.warehouseCode()).isNull();
    }

    private static Inventory sample(int available) {
        return Inventory.restore(UUID.randomUUID(), WAREHOUSE, UUID.randomUUID(), SKU, null,
                available, 0, 0, NOW, 0L, NOW, "seed", NOW, "seed");
    }

    private static class FakeOutbox implements OutboxWriter {
        final List<InventoryDomainEvent> events = new ArrayList<>();
        @Override public void write(InventoryDomainEvent event) { events.add(event); }
    }

    private static class FakeMasterReadModel implements MasterReadModelPort {
        private final Map<UUID, String> warehouseCodes = new HashMap<>();

        void putWarehouse(UUID id, String warehouseCode) {
            warehouseCodes.put(id, warehouseCode);
        }

        @Override public Optional<LocationSnapshot> findLocation(UUID id) { return Optional.empty(); }
        @Override public Optional<SkuSnapshot> findSku(UUID id) { return Optional.empty(); }
        @Override public Optional<LotSnapshot> findLot(UUID id) { return Optional.empty(); }
        @Override public Optional<WarehouseSnapshot> findWarehouse(UUID id) {
            return Optional.ofNullable(warehouseCodes.get(id))
                    .map(code -> new WarehouseSnapshot(
                            id, code, WarehouseSnapshot.Status.ACTIVE, NOW, 0L));
        }
    }
}
