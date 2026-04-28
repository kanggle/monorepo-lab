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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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

    private static Inventory sample(int available) {
        return Inventory.restore(UUID.randomUUID(), WAREHOUSE, UUID.randomUUID(), SKU, null,
                available, 0, 0, NOW, 0L, NOW, "seed", NOW, "seed");
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
