package com.wms.inbound.adapter.out.persistence.masterref;

import com.wms.inbound.application.port.out.MasterReadModelPort;
import com.wms.inbound.application.port.out.MasterReadModelWriterPort;
import com.wms.inbound.domain.model.masterref.LocationSnapshot;
import com.wms.inbound.domain.model.masterref.LotSnapshot;
import com.wms.inbound.domain.model.masterref.PartnerSnapshot;
import com.wms.inbound.domain.model.masterref.SkuSnapshot;
import com.wms.inbound.domain.model.masterref.WarehouseSnapshot;
import com.wms.inbound.domain.model.masterref.ZoneSnapshot;
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

    private final WarehouseSnapshotJpaRepository warehouseRepo;
    private final ZoneSnapshotJpaRepository zoneRepo;
    private final LocationSnapshotJpaRepository locationRepo;
    private final SkuSnapshotJpaRepository skuRepo;
    private final LotSnapshotJpaRepository lotRepo;
    private final PartnerSnapshotJpaRepository partnerRepo;

    @PersistenceContext
    private EntityManager entityManager;

    public MasterReadModelPersistenceAdapter(WarehouseSnapshotJpaRepository warehouseRepo,
                                             ZoneSnapshotJpaRepository zoneRepo,
                                             LocationSnapshotJpaRepository locationRepo,
                                             SkuSnapshotJpaRepository skuRepo,
                                             LotSnapshotJpaRepository lotRepo,
                                             PartnerSnapshotJpaRepository partnerRepo) {
        this.warehouseRepo = warehouseRepo;
        this.zoneRepo = zoneRepo;
        this.locationRepo = locationRepo;
        this.skuRepo = skuRepo;
        this.lotRepo = lotRepo;
        this.partnerRepo = partnerRepo;
    }

    // ---- Read side -----------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Optional<WarehouseSnapshot> findWarehouse(UUID id) {
        return warehouseRepo.findById(id).map(MasterReadModelPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ZoneSnapshot> findZone(UUID id) {
        return zoneRepo.findById(id).map(MasterReadModelPersistenceAdapter::toDomain);
    }

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

    @Override
    @Transactional(readOnly = true)
    public Optional<PartnerSnapshot> findPartner(UUID id) {
        return partnerRepo.findById(id).map(MasterReadModelPersistenceAdapter::toDomain);
    }

    // ---- Write side ----------------------------------------------------------

    @Override
    public boolean upsertWarehouse(WarehouseSnapshot s) {
        int rows = entityManager.createNativeQuery("""
                INSERT INTO warehouse_snapshot
                    (id, warehouse_code, status, cached_at, master_version)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    warehouse_code = EXCLUDED.warehouse_code,
                    status = EXCLUDED.status,
                    cached_at = EXCLUDED.cached_at,
                    master_version = EXCLUDED.master_version
                WHERE warehouse_snapshot.master_version < EXCLUDED.master_version
                """)
                .setParameter(1, s.id())
                .setParameter(2, s.warehouseCode())
                .setParameter(3, s.status().name())
                .setParameter(4, s.cachedAt())
                .setParameter(5, s.masterVersion())
                .executeUpdate();
        return rows > 0;
    }

    @Override
    public boolean upsertZone(ZoneSnapshot s) {
        int rows = entityManager.createNativeQuery("""
                INSERT INTO zone_snapshot
                    (id, warehouse_id, zone_code, zone_type, status,
                     cached_at, master_version)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    warehouse_id = EXCLUDED.warehouse_id,
                    zone_code = EXCLUDED.zone_code,
                    zone_type = EXCLUDED.zone_type,
                    status = EXCLUDED.status,
                    cached_at = EXCLUDED.cached_at,
                    master_version = EXCLUDED.master_version
                WHERE zone_snapshot.master_version < EXCLUDED.master_version
                """)
                .setParameter(1, s.id())
                .setParameter(2, s.warehouseId())
                .setParameter(3, s.zoneCode())
                .setParameter(4, s.zoneType())
                .setParameter(5, s.status().name())
                .setParameter(6, s.cachedAt())
                .setParameter(7, s.masterVersion())
                .executeUpdate();
        return rows > 0;
    }

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
                    (id, sku_code, tracking_type, status,
                     cached_at, master_version)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    sku_code = EXCLUDED.sku_code,
                    tracking_type = EXCLUDED.tracking_type,
                    status = EXCLUDED.status,
                    cached_at = EXCLUDED.cached_at,
                    master_version = EXCLUDED.master_version
                WHERE sku_snapshot.master_version < EXCLUDED.master_version
                """)
                .setParameter(1, s.id())
                .setParameter(2, s.skuCode())
                .setParameter(3, s.trackingType().name())
                .setParameter(4, s.status().name())
                .setParameter(5, s.cachedAt())
                .setParameter(6, s.masterVersion())
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

    @Override
    public boolean upsertPartner(PartnerSnapshot s) {
        int rows = entityManager.createNativeQuery("""
                INSERT INTO partner_snapshot
                    (id, partner_code, partner_type, status,
                     cached_at, master_version)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    partner_code = EXCLUDED.partner_code,
                    partner_type = EXCLUDED.partner_type,
                    status = EXCLUDED.status,
                    cached_at = EXCLUDED.cached_at,
                    master_version = EXCLUDED.master_version
                WHERE partner_snapshot.master_version < EXCLUDED.master_version
                """)
                .setParameter(1, s.id())
                .setParameter(2, s.partnerCode())
                .setParameter(3, s.partnerType().name())
                .setParameter(4, s.status().name())
                .setParameter(5, s.cachedAt())
                .setParameter(6, s.masterVersion())
                .executeUpdate();
        return rows > 0;
    }

    // ---- Mappers -------------------------------------------------------------

    private static WarehouseSnapshot toDomain(WarehouseSnapshotJpaEntity e) {
        return new WarehouseSnapshot(
                e.getId(),
                e.getWarehouseCode(),
                WarehouseSnapshot.Status.valueOf(e.getStatus()),
                e.getCachedAt(),
                e.getMasterVersion());
    }

    private static ZoneSnapshot toDomain(ZoneSnapshotJpaEntity e) {
        return new ZoneSnapshot(
                e.getId(),
                e.getWarehouseId(),
                e.getZoneCode(),
                e.getZoneType(),
                ZoneSnapshot.Status.valueOf(e.getStatus()),
                e.getCachedAt(),
                e.getMasterVersion());
    }

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

    private static PartnerSnapshot toDomain(PartnerSnapshotJpaEntity e) {
        return new PartnerSnapshot(
                e.getId(),
                e.getPartnerCode(),
                PartnerSnapshot.PartnerType.valueOf(e.getPartnerType()),
                PartnerSnapshot.Status.valueOf(e.getStatus()),
                e.getCachedAt(),
                e.getMasterVersion());
    }
}
