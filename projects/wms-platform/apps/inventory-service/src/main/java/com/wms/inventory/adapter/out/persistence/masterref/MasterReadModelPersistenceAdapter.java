package com.wms.inventory.adapter.out.persistence.masterref;

import com.wms.inventory.application.port.out.MasterReadModelPort;
import com.wms.inventory.application.port.out.MasterReadModelWriterPort;
import com.wms.inventory.domain.model.masterref.LocationSnapshot;
import com.wms.inventory.domain.model.masterref.LotSnapshot;
import com.wms.inventory.domain.model.masterref.SkuSnapshot;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Combined adapter that implements the read-side {@link MasterReadModelPort}
 * and the write-side {@link MasterReadModelWriterPort}.
 *
 * <p>Upserts are version-guarded via a single native SQL statement: an
 * {@code INSERT … ON CONFLICT … DO UPDATE … WHERE existing.master_version &lt;
 * EXCLUDED.master_version}. The DB enforces the version check atomically so
 * we never race with a concurrent consumer thread, and we avoid load-then-write
 * round trips.
 *
 * <p>The {@code WHERE} clause makes the {@code DO UPDATE} a conditional one —
 * out-of-order older events are reported back to the caller as
 * {@code applied = false}.
 */
@Component
public class MasterReadModelPersistenceAdapter
        implements MasterReadModelPort, MasterReadModelWriterPort {

    private final LocationSnapshotJpaRepository locationRepo;
    private final SkuSnapshotJpaRepository skuRepo;
    private final LotSnapshotJpaRepository lotRepo;

    @PersistenceContext
    private EntityManager entityManager;

    public MasterReadModelPersistenceAdapter(LocationSnapshotJpaRepository locationRepo,
                                             SkuSnapshotJpaRepository skuRepo,
                                             LotSnapshotJpaRepository lotRepo) {
        this.locationRepo = locationRepo;
        this.skuRepo = skuRepo;
        this.lotRepo = lotRepo;
    }

    // ---- Read side -----------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Optional<LocationSnapshot> findLocation(UUID id) {
        return locationRepo.findById(id).map(MasterReadModelPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SkuSnapshot> findSku(UUID id) {
        return skuRepo.findById(id).map(MasterReadModelPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LotSnapshot> findLot(UUID id) {
        return lotRepo.findById(id).map(MasterReadModelPersistenceAdapter::toDomain);
    }

    // ---- Write side ----------------------------------------------------------

    @Override
    public boolean upsertLocation(LocationSnapshot s) {
        int rows = entityManager.createNativeQuery("""
                INSERT INTO location_snapshot
                    (id, location_code, warehouse_id, zone_id, location_type,
                     status, cached_at, master_version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    location_code = EXCLUDED.location_code,
                    warehouse_id = EXCLUDED.warehouse_id,
                    zone_id = EXCLUDED.zone_id,
                    location_type = EXCLUDED.location_type,
                    status = EXCLUDED.status,
                    cached_at = EXCLUDED.cached_at,
                    master_version = EXCLUDED.master_version
                WHERE location_snapshot.master_version < EXCLUDED.master_version
                """)
                .setParameter(1, s.id())
                .setParameter(2, s.locationCode())
                .setParameter(3, s.warehouseId())
                .setParameter(4, s.zoneId())
                .setParameter(5, s.locationType().name())
                .setParameter(6, s.status().name())
                .setParameter(7, s.cachedAt())
                .setParameter(8, s.masterVersion())
                .executeUpdate();
        return rows > 0;
    }

    @Override
    public boolean upsertSku(SkuSnapshot s) {
        int rows = entityManager.createNativeQuery("""
                INSERT INTO sku_snapshot
                    (id, sku_code, tracking_type, base_uom, status,
                     cached_at, master_version)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    sku_code = EXCLUDED.sku_code,
                    tracking_type = EXCLUDED.tracking_type,
                    base_uom = EXCLUDED.base_uom,
                    status = EXCLUDED.status,
                    cached_at = EXCLUDED.cached_at,
                    master_version = EXCLUDED.master_version
                WHERE sku_snapshot.master_version < EXCLUDED.master_version
                """)
                .setParameter(1, s.id())
                .setParameter(2, s.skuCode())
                .setParameter(3, s.trackingType().name())
                .setParameter(4, s.baseUom())
                .setParameter(5, s.status().name())
                .setParameter(6, s.cachedAt())
                .setParameter(7, s.masterVersion())
                .executeUpdate();
        return rows > 0;
    }

    @Override
    public boolean upsertLot(LotSnapshot s) {
        int rows = entityManager.createNativeQuery("""
                INSERT INTO lot_snapshot
                    (id, sku_id, lot_no, expiry_date, status,
                     cached_at, master_version)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    sku_id = EXCLUDED.sku_id,
                    lot_no = EXCLUDED.lot_no,
                    expiry_date = EXCLUDED.expiry_date,
                    status = EXCLUDED.status,
                    cached_at = EXCLUDED.cached_at,
                    master_version = EXCLUDED.master_version
                WHERE lot_snapshot.master_version < EXCLUDED.master_version
                """)
                .setParameter(1, s.id())
                .setParameter(2, s.skuId())
                .setParameter(3, s.lotNo())
                .setParameter(4, s.expiryDate())
                .setParameter(5, s.status().name())
                .setParameter(6, s.cachedAt())
                .setParameter(7, s.masterVersion())
                .executeUpdate();
        return rows > 0;
    }

    // ---- Mappers -------------------------------------------------------------

    private static LocationSnapshot toDomain(LocationSnapshotJpaEntity e) {
        return new LocationSnapshot(
                e.getId(),
                e.getLocationCode(),
                e.getWarehouseId(),
                e.getZoneId(),
                LocationSnapshot.LocationType.valueOf(e.getLocationType()),
                LocationSnapshot.Status.valueOf(e.getStatus()),
                e.getCachedAt(),
                e.getMasterVersion());
    }

    private static SkuSnapshot toDomain(SkuSnapshotJpaEntity e) {
        return new SkuSnapshot(
                e.getId(),
                e.getSkuCode(),
                SkuSnapshot.TrackingType.valueOf(e.getTrackingType()),
                e.getBaseUom(),
                SkuSnapshot.Status.valueOf(e.getStatus()),
                e.getCachedAt(),
                e.getMasterVersion());
    }

    private static LotSnapshot toDomain(LotSnapshotJpaEntity e) {
        return new LotSnapshot(
                e.getId(),
                e.getSkuId(),
                e.getLotNo(),
                e.getExpiryDate(),
                LotSnapshot.Status.valueOf(e.getStatus()),
                e.getCachedAt(),
                e.getMasterVersion());
    }
}
