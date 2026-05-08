package com.wms.admin.application.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.admin.application.projection.fakes.InMemoryDedupePort;
import com.wms.admin.infra.observability.ProjectionMetrics;
import com.wms.admin.readmodel.alert.AlertLogEntity;
import com.wms.admin.readmodel.alert.AlertLogRepository;
import com.wms.admin.readmodel.inventory.AdjustmentAuditEntity;
import com.wms.admin.readmodel.inventory.AdjustmentAuditRepository;
import com.wms.admin.readmodel.inventory.InventorySnapshotEntity;
import com.wms.admin.readmodel.inventory.InventorySnapshotId;
import com.wms.admin.readmodel.inventory.InventorySnapshotRepository;
import com.wms.admin.readmodel.master.LocationRefRepository;
import com.wms.admin.readmodel.master.LotRefRepository;
import com.wms.admin.readmodel.master.SkuRefRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryProjectionServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");

    @Mock InventorySnapshotRepository snapshotRepo;
    @Mock AdjustmentAuditRepository auditRepo;
    @Mock AlertLogRepository alertRepo;
    @Mock LocationRefRepository locationRepo;
    @Mock SkuRefRepository skuRepo;
    @Mock LotRefRepository lotRepo;

    private InMemoryDedupePort dedupe;
    private InventoryProjectionService service;

    @BeforeEach
    void setUp() {
        dedupe = new InMemoryDedupePort();
        ProjectionMetrics metrics = new ProjectionMetrics(new SimpleMeterRegistry(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new InventoryProjectionService(snapshotRepo, auditRepo, alertRepo,
                locationRepo, skuRepo, lotRepo, dedupe, metrics, clock);
    }

    @Test
    void inventoryReceived_insertsSnapshot() throws Exception {
        UUID location = UUID.randomUUID();
        UUID sku = UUID.randomUUID();
        when(snapshotRepo.findById(any(InventorySnapshotId.class))).thenReturn(Optional.empty());
        when(locationRepo.findById(any())).thenReturn(Optional.empty());
        when(skuRepo.findById(any())).thenReturn(Optional.empty());

        ProjectionEnvelope env = envelope("inventory.received", "wms.inventory.received.v1",
                "{\"warehouseId\":\"" + UUID.randomUUID() + "\",\"lines\":[{\"locationId\":\""
                        + location + "\",\"skuId\":\"" + sku + "\",\"qtyReceived\":50,"
                        + "\"availableQtyAfter\":50}]}");

        assertThat(service.project(env)).isEqualTo(DedupeOutcome.APPLIED);
        verify(snapshotRepo, times(1)).save(any(InventorySnapshotEntity.class));
    }

    @Test
    void inventoryAdjusted_appendsAuditAndUpdatesSnapshot() throws Exception {
        UUID location = UUID.randomUUID();
        UUID sku = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(auditRepo.existsById(any())).thenReturn(false);
        when(snapshotRepo.findById(any(InventorySnapshotId.class))).thenReturn(Optional.empty());
        when(locationRepo.findById(any())).thenReturn(Optional.empty());
        when(skuRepo.findById(any())).thenReturn(Optional.empty());

        ProjectionEnvelope env = envelope("inventory.adjusted", "wms.inventory.adjusted.v1",
                "{\"locationId\":\"" + location + "\",\"skuId\":\"" + sku
                        + "\",\"warehouseId\":\"" + warehouseId
                        + "\",\"bucket\":\"AVAILABLE\",\"delta\":-5,\"reasonCode\":\"LOSS\","
                        + "\"inventory\":{\"availableQty\":75,\"reservedQty\":20,\"damagedQty\":0}}");

        assertThat(service.project(env)).isEqualTo(DedupeOutcome.APPLIED);
        verify(auditRepo, times(1)).save(any(AdjustmentAuditEntity.class));
        verify(snapshotRepo, atLeastOnce()).save(any(InventorySnapshotEntity.class));
    }

    @Test
    void inventoryTransferred_dualWritesSourceAndTarget() throws Exception {
        UUID source = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        UUID sku = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(snapshotRepo.findById(any(InventorySnapshotId.class))).thenReturn(Optional.empty());
        when(locationRepo.findById(any())).thenReturn(Optional.empty());
        when(skuRepo.findById(any())).thenReturn(Optional.empty());

        ProjectionEnvelope env = envelope("inventory.transferred",
                "wms.inventory.transferred.v1",
                "{\"warehouseId\":\"" + warehouseId + "\",\"skuId\":\"" + sku
                        + "\",\"source\":{\"locationId\":\"" + source
                        + "\",\"availableQtyAfter\":70},"
                        + "\"target\":{\"locationId\":\"" + target
                        + "\",\"availableQtyAfter\":10}}");

        assertThat(service.project(env)).isEqualTo(DedupeOutcome.APPLIED);
        verify(snapshotRepo, times(2)).save(any(InventorySnapshotEntity.class));
    }

    @Test
    void inventoryReserved_updatesReservedQty() throws Exception {
        UUID location = UUID.randomUUID();
        UUID sku = UUID.randomUUID();
        when(snapshotRepo.findById(any(InventorySnapshotId.class))).thenReturn(Optional.empty());
        when(locationRepo.findById(any())).thenReturn(Optional.empty());
        when(skuRepo.findById(any())).thenReturn(Optional.empty());

        ProjectionEnvelope env = envelope("inventory.reserved", "wms.inventory.reserved.v1",
                "{\"warehouseId\":\"" + UUID.randomUUID() + "\",\"lines\":[{\"locationId\":\""
                        + location + "\",\"skuId\":\"" + sku
                        + "\",\"availableQtyAfter\":75,\"reservedQtyAfter\":25}]}");

        assertThat(service.project(env)).isEqualTo(DedupeOutcome.APPLIED);
        verify(snapshotRepo, times(1)).save(any(InventorySnapshotEntity.class));
    }

    @Test
    void inventoryReleased_updatesReservedQty() throws Exception {
        UUID location = UUID.randomUUID();
        UUID sku = UUID.randomUUID();
        when(snapshotRepo.findById(any(InventorySnapshotId.class))).thenReturn(Optional.empty());
        when(locationRepo.findById(any())).thenReturn(Optional.empty());
        when(skuRepo.findById(any())).thenReturn(Optional.empty());

        ProjectionEnvelope env = envelope("inventory.released", "wms.inventory.released.v1",
                "{\"warehouseId\":\"" + UUID.randomUUID() + "\",\"lines\":[{\"locationId\":\""
                        + location + "\",\"skuId\":\"" + sku
                        + "\",\"availableQtyAfter\":80,\"reservedQtyAfter\":20}]}");

        assertThat(service.project(env)).isEqualTo(DedupeOutcome.APPLIED);
        verify(snapshotRepo, times(1)).save(any(InventorySnapshotEntity.class));
    }

    @Test
    void inventoryConfirmed_updatesSnapshot() throws Exception {
        UUID location = UUID.randomUUID();
        UUID sku = UUID.randomUUID();
        when(snapshotRepo.findById(any(InventorySnapshotId.class))).thenReturn(Optional.empty());
        when(locationRepo.findById(any())).thenReturn(Optional.empty());
        when(skuRepo.findById(any())).thenReturn(Optional.empty());

        ProjectionEnvelope env = envelope("inventory.confirmed", "wms.inventory.confirmed.v1",
                "{\"warehouseId\":\"" + UUID.randomUUID() + "\",\"lines\":[{\"locationId\":\""
                        + location + "\",\"skuId\":\"" + sku
                        + "\",\"reservedQtyAfter\":20}]}");

        assertThat(service.project(env)).isEqualTo(DedupeOutcome.APPLIED);
        verify(snapshotRepo, times(1)).save(any(InventorySnapshotEntity.class));
    }

    @Test
    void lowStockDetected_appendsAlertLog() throws Exception {
        UUID location = UUID.randomUUID();
        UUID sku = UUID.randomUUID();
        when(alertRepo.existsById(any())).thenReturn(false);

        ProjectionEnvelope env = envelope("inventory.low-stock-detected",
                "wms.inventory.alert.v1",
                "{\"locationId\":\"" + location + "\",\"skuId\":\"" + sku
                        + "\",\"availableQty\":5,\"threshold\":10}");

        assertThat(service.project(env)).isEqualTo(DedupeOutcome.APPLIED);
        verify(alertRepo, times(1)).save(any(AlertLogEntity.class));
    }

    @Test
    void lowStockDetected_duplicateEventIdSkipsAppend() throws Exception {
        UUID location = UUID.randomUUID();
        UUID sku = UUID.randomUUID();
        when(alertRepo.existsById(any())).thenReturn(true);  // already appended

        ProjectionEnvelope env = envelope("inventory.low-stock-detected",
                "wms.inventory.alert.v1",
                "{\"locationId\":\"" + location + "\",\"skuId\":\"" + sku
                        + "\",\"availableQty\":5,\"threshold\":10}");

        assertThat(service.project(env)).isEqualTo(DedupeOutcome.APPLIED);
        verify(alertRepo, times(0)).save(any());
    }

    private ProjectionEnvelope envelope(String eventType, String topic, String payloadJson)
            throws Exception {
        JsonNode payload = MAPPER.readTree(payloadJson);
        return new ProjectionEnvelope(UUID.randomUUID(), eventType, NOW, "agg", topic, payload);
    }
}
