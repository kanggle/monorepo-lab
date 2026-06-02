package com.wms.admin.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.admin.readmodel.alert.AlertLogEntity;
import com.wms.admin.readmodel.alert.AlertLogRepository;
import com.wms.admin.readmodel.inbound.AsnSummaryEntity;
import com.wms.admin.readmodel.inbound.AsnSummaryRepository;
import com.wms.admin.readmodel.inventory.AdjustmentAuditEntity;
import com.wms.admin.readmodel.inventory.AdjustmentAuditRepository;
import com.wms.admin.readmodel.inventory.InventorySnapshotEntity;
import com.wms.admin.readmodel.inventory.InventorySnapshotId;
import com.wms.admin.readmodel.inventory.InventorySnapshotRepository;
import com.wms.admin.readmodel.master.WarehouseRefEntity;
import com.wms.admin.readmodel.master.WarehouseRefRepository;
import com.wms.admin.readmodel.outbound.OrderSummaryEntity;
import com.wms.admin.readmodel.outbound.OrderSummaryRepository;
import com.wms.admin.readmodel.outbound.ShipmentSummaryEntity;
import com.wms.admin.readmodel.outbound.ShipmentSummaryRepository;
import com.wms.admin.readmodel.throughput.ThroughputDailyId;
import com.wms.admin.readmodel.throughput.ThroughputInboundDailyEntity;
import com.wms.admin.readmodel.throughput.ThroughputInboundDailyRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence-level integration test that round-trips a representative entity
 * for each of the 15 V2 read-model tables. Verifies (1) Flyway V2 schema is
 * compatible with the JPA mappings and (2) the composite-PK + sentinel-UUID
 * pattern works end-to-end (admin_inventory_snapshot lot_id slot,
 * admin_throughput_*).
 */
@Tag("integration")
@SpringBootTest(classes = com.wms.admin.AdminServiceApplication.class,
        properties = {
                "spring.flyway.locations=classpath:db/migration",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        })
@ActiveProfiles({"test", "standalone"})
@ContextConfiguration(initializers = AdminServiceIntegrationBase.Initializer.class)
@Transactional
class ReadModelPersistenceIntegrationTest extends AdminServiceIntegrationBase {

    private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");

    @Autowired WarehouseRefRepository warehouseRepo;
    @Autowired AsnSummaryRepository asnRepo;
    @Autowired OrderSummaryRepository orderRepo;
    @Autowired InventorySnapshotRepository snapshotRepo;
    @Autowired AdjustmentAuditRepository auditRepo;
    @Autowired AlertLogRepository alertRepo;
    @Autowired ThroughputInboundDailyRepository throughputInboundRepo;
    @Autowired ShipmentSummaryRepository shipmentRepo;

    @Test
    void warehouseRef_roundTrip() {
        UUID id = UUID.randomUUID();
        warehouseRepo.save(new WarehouseRefEntity(id, "WH01", "Seoul", "Asia/Seoul",
                "ACTIVE", NOW));

        var loaded = warehouseRepo.findById(id).orElseThrow();
        assertThat(loaded.getWarehouseCode()).isEqualTo("WH01");
        assertThat(loaded.getName()).isEqualTo("Seoul");
        assertThat(loaded.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void asnSummary_roundTrip() {
        UUID asnId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        asnRepo.save(new AsnSummaryEntity(asnId, "ASN-001", warehouseId, null, null,
                "CREATED", "MANUAL", LocalDate.of(2026, 5, 12), 3, NOW, null, NOW));

        var loaded = asnRepo.findById(asnId).orElseThrow();
        assertThat(loaded.getAsnNo()).isEqualTo("ASN-001");
        assertThat(loaded.getLineCount()).isEqualTo(3);
    }

    @Test
    void orderSummary_roundTrip() {
        UUID orderId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        orderRepo.save(new OrderSummaryEntity(orderId, "ORD-1", warehouseId, null, null,
                "RECEIVED", "WEBHOOK_ERP", LocalDate.of(2026, 5, 15), 2, null, NOW, null, NOW));

        var loaded = orderRepo.findById(orderId).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo("RECEIVED");
        assertThat(loaded.getRequiredShipDate()).isEqualTo(LocalDate.of(2026, 5, 15));
    }

    @Test
    void inventorySnapshot_compositePk_roundTrip_withNullLotId() {
        UUID location = UUID.randomUUID();
        UUID sku = UUID.randomUUID();
        UUID warehouse = UUID.randomUUID();
        snapshotRepo.save(new InventorySnapshotEntity(location, sku, null, warehouse,
                "WH01-A-01", "SKU-1", null, 100, 0, 0, false, NOW, NOW));

        var loaded = snapshotRepo.findById(new InventorySnapshotId(location, sku, null))
                .orElseThrow();
        assertThat(loaded.getOnHandQty()).isEqualTo(100);
        assertThat(loaded.getLotIdOrNull()).isNull();
    }

    @Test
    void inventorySnapshot_compositePk_roundTrip_withLotId() {
        UUID location = UUID.randomUUID();
        UUID sku = UUID.randomUUID();
        UUID lot = UUID.randomUUID();
        UUID warehouse = UUID.randomUUID();
        snapshotRepo.save(new InventorySnapshotEntity(location, sku, lot, warehouse,
                null, null, "LOT-1", 50, 10, 0, false, NOW, NOW));

        var loaded = snapshotRepo.findById(new InventorySnapshotId(location, sku, lot))
                .orElseThrow();
        assertThat(loaded.getLotIdOrNull()).isEqualTo(lot);
        assertThat(loaded.getReservedQty()).isEqualTo(10);
    }

    @Test
    void adjustmentAudit_appendOnly_uniquePk() {
        UUID id = UUID.randomUUID();
        AdjustmentAuditEntity row = new AdjustmentAuditEntity(id,
                UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(),
                "AVAILABLE", -5, "LOSS", "lost", "actor", NOW, NOW);
        auditRepo.save(row);

        assertThat(auditRepo.existsById(id)).isTrue();
    }

    @Test
    void alertLog_appendOnly_acknowledgePathMutates() {
        UUID id = UUID.randomUUID();
        AlertLogEntity row = new AlertLogEntity(id, "LOW_STOCK",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                10, 5, NOW, NOW);
        alertRepo.save(row);

        var loaded = alertRepo.findById(id).orElseThrow();
        loaded.acknowledge("ops-1", NOW.plusSeconds(60));
        alertRepo.save(loaded);

        var reloaded = alertRepo.findById(id).orElseThrow();
        assertThat(reloaded.getAcknowledgedBy()).isEqualTo("ops-1");
        assertThat(reloaded.getAcknowledgedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    /**
     * Regression (TASK-BE-331): the alerts dashboard query with ALL filters
     * null — exactly what the platform-console WMS 운영 page sends — must run
     * against real PostgreSQL without `42P18 could not determine data type of
     * parameter` (a 500). Mirrors the controller's default sort
     * ({@code detectedAt,desc}). This path is invisible to the WebMvc slice
     * test (mocked repository) and the integration suite is excluded from CI
     * `check`, so the bug shipped — the fix CASTs the nullable temporal bounds.
     */
    @Test
    void alertLog_search_allNullFilters_doesNotFailPgTypeInference() {
        UUID id = UUID.randomUUID();
        alertRepo.save(new AlertLogEntity(id, "LOW_STOCK",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                10, 5, NOW, NOW));

        Page<AlertLogEntity> result = alertRepo.search(
                null, null, null, null, null,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "detectedAt")));

        assertThat(result).isNotNull();
        assertThat(result.getContent()).anyMatch(a -> a.getId().equals(id));
    }

    /**
     * Regression (TASK-BE-331): the adjustments dashboard query carries the
     * identical nullable-temporal pattern (occurredAtFrom/To) and the same
     * latent 42P18 — pinned here against real PostgreSQL with all-null filters.
     */
    @Test
    void adjustmentAudit_search_allNullFilters_doesNotFailPgTypeInference() {
        UUID id = UUID.randomUUID();
        auditRepo.save(new AdjustmentAuditEntity(id,
                UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(),
                "AVAILABLE", -5, "LOSS", "lost", "actor", NOW, NOW));

        Page<AdjustmentAuditEntity> result = auditRepo.search(
                null, null, null, null, null, null, null,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "occurredAt")));

        assertThat(result).isNotNull();
        assertThat(result.getContent()).anyMatch(a -> a.getId().equals(id));
    }

    /**
     * Regression (TASK-BE-332): the order-summary dashboard query with ALL
     * filters null must run against real PostgreSQL without `42P18` (the nullable
     * temporal `requiredShipDate` bound). Same class as BE-331 AlertLog.
     */
    @Test
    void orderSummary_search_allNullFilters_doesNotFailPgTypeInference() {
        UUID orderId = UUID.randomUUID();
        orderRepo.save(new OrderSummaryEntity(orderId, "ORD-1", UUID.randomUUID(), null, null,
                "RECEIVED", "WEBHOOK_ERP", LocalDate.of(2026, 5, 15), 2, null, NOW, null, NOW));

        Page<OrderSummaryEntity> result =
                orderRepo.search(null, null, null, null, null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).anyMatch(o -> o.getOrderId().equals(orderId));
    }

    /**
     * Regression (TASK-BE-332): the asn-summary dashboard query with ALL filters
     * null (nullable temporal `expectedArriveDate`). Same class as BE-331.
     */
    @Test
    void asnSummary_search_allNullFilters_doesNotFailPgTypeInference() {
        UUID asnId = UUID.randomUUID();
        asnRepo.save(new AsnSummaryEntity(asnId, "ASN-001", UUID.randomUUID(), null, null,
                "CREATED", "MANUAL", LocalDate.of(2026, 5, 12), 3, NOW, null, NOW));

        Page<AsnSummaryEntity> result =
                asnRepo.search(null, null, null, null, null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).anyMatch(a -> a.getAsnId().equals(asnId));
    }

    /**
     * Regression (TASK-BE-332): the shipment-summary dashboard query with ALL
     * filters null (nullable temporal `shippedAt`). Same class as BE-331.
     */
    @Test
    void shipmentSummary_search_allNullFilters_doesNotFailPgTypeInference() {
        UUID shipmentId = UUID.randomUUID();
        shipmentRepo.save(new ShipmentSummaryEntity(shipmentId, UUID.randomUUID(), "ORD-1",
                UUID.randomUUID(), "SH-1", "CARRIER-A", "TRK-1", NOW, 5, NOW));

        Page<ShipmentSummaryEntity> result =
                shipmentRepo.search(null, null, null, null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).anyMatch(s -> s.getShipmentId().equals(shipmentId));
    }

    @Test
    void throughputInboundDaily_compositePk_increment() {
        UUID warehouseId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 5, 9);
        throughputInboundRepo.save(new ThroughputInboundDailyEntity(date, warehouseId,
                1, 50, NOW));

        var loaded = throughputInboundRepo.findById(new ThroughputDailyId(date, warehouseId))
                .orElseThrow();
        loaded.increment(20, NOW.plusSeconds(60));
        throughputInboundRepo.save(loaded);

        var reloaded = throughputInboundRepo.findById(new ThroughputDailyId(date, warehouseId))
                .orElseThrow();
        assertThat(reloaded.getPutawayCount()).isEqualTo(2);
        assertThat(reloaded.getQtyReceived()).isEqualTo(70);
    }

    @Test
    void throughputInboundDaily_nativeUpsertIncrement_isAtomicAndLwwGuarded() {
        UUID warehouseId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 5, 10);

        // First call: INSERT path (no row exists)
        int firstAffected = throughputInboundRepo.upsertIncrement(date, warehouseId, 50, NOW);
        assertThat(firstAffected).isEqualTo(1);

        var afterInsert = throughputInboundRepo.findById(new ThroughputDailyId(date, warehouseId))
                .orElseThrow();
        assertThat(afterInsert.getPutawayCount()).isEqualTo(1);
        assertThat(afterInsert.getQtyReceived()).isEqualTo(50);

        // Second call: ON CONFLICT DO UPDATE path (newer event)
        int secondAffected = throughputInboundRepo.upsertIncrement(
                date, warehouseId, 25, NOW.plusSeconds(60));
        assertThat(secondAffected).isEqualTo(1);

        var afterUpdate = throughputInboundRepo.findById(new ThroughputDailyId(date, warehouseId))
                .orElseThrow();
        assertThat(afterUpdate.getPutawayCount()).isEqualTo(2);
        assertThat(afterUpdate.getQtyReceived()).isEqualTo(75);

        // Third call: stale event (occurredAt < existing last_event_at) — WHERE
        // guard skips the UPDATE; affected = 0; row unchanged.
        int staleAffected = throughputInboundRepo.upsertIncrement(
                date, warehouseId, 999, NOW.minusSeconds(60));
        assertThat(staleAffected).isEqualTo(0);

        var afterStale = throughputInboundRepo.findById(new ThroughputDailyId(date, warehouseId))
                .orElseThrow();
        assertThat(afterStale.getPutawayCount()).isEqualTo(2);
        assertThat(afterStale.getQtyReceived()).isEqualTo(75);
    }
}
