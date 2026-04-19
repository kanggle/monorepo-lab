package com.wms.master.application.service;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.application.port.out.ZonePersistencePort;
import com.wms.master.application.query.ListZonesCriteria;
import com.wms.master.domain.exception.ZoneCodeDuplicateException;
import com.wms.master.domain.exception.ZoneNotFoundException;
import com.wms.master.domain.model.Zone;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * In-memory fake for {@link ZonePersistencePort}. Models version-based
 * optimistic locking and the compound {@code (warehouseId, zoneCode)} unique
 * constraint with the same semantics as the JPA adapter so service-level tests
 * can exercise every error path without Docker / Testcontainers.
 */
class FakeZonePersistencePort implements ZonePersistencePort {

    private final Map<UUID, Zone> byId = new HashMap<>();
    private final Set<UUID> zonesWithActiveLocations = new HashSet<>();
    private boolean failNextUpdateAsOptimisticLock = false;

    @Override
    public Zone insert(Zone newZone) {
        boolean codeTakenInWarehouse = byId.values().stream()
                .anyMatch(z -> z.getWarehouseId().equals(newZone.getWarehouseId())
                        && z.getZoneCode().equals(newZone.getZoneCode()));
        if (codeTakenInWarehouse) {
            throw new ZoneCodeDuplicateException(newZone.getWarehouseId(), newZone.getZoneCode());
        }
        Zone snapshot = snapshot(newZone);
        byId.put(snapshot.getId(), snapshot);
        return snapshot(snapshot);
    }

    @Override
    public Zone update(Zone modified) {
        Zone stored = byId.get(modified.getId());
        if (stored == null) {
            throw new ZoneNotFoundException(modified.getId().toString());
        }
        if (failNextUpdateAsOptimisticLock) {
            failNextUpdateAsOptimisticLock = false;
            throw new ObjectOptimisticLockingFailureException(Zone.class, modified.getId());
        }
        if (stored.getVersion() != modified.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(Zone.class, modified.getId());
        }
        Zone bumped = withVersion(snapshot(modified), modified.getVersion() + 1);
        byId.put(bumped.getId(), bumped);
        return snapshot(bumped);
    }

    @Override
    public Optional<Zone> findById(UUID id) {
        return Optional.ofNullable(byId.get(id)).map(FakeZonePersistencePort::snapshot);
    }

    @Override
    public Optional<Zone> findByWarehouseIdAndZoneCode(UUID warehouseId, String zoneCode) {
        return byId.values().stream()
                .filter(z -> z.getWarehouseId().equals(warehouseId)
                        && z.getZoneCode().equals(zoneCode))
                .findFirst()
                .map(FakeZonePersistencePort::snapshot);
    }

    @Override
    public PageResult<Zone> findPage(ListZonesCriteria criteria, PageQuery pageQuery) {
        Stream<Zone> filtered = byId.values().stream()
                .filter(z -> z.getWarehouseId().equals(criteria.warehouseId()));
        if (criteria.status() != null) {
            filtered = filtered.filter(z -> z.getStatus() == criteria.status());
        }
        if (criteria.zoneType() != null) {
            filtered = filtered.filter(z -> z.getZoneType() == criteria.zoneType());
        }

        List<Zone> all = filtered
                .sorted(Comparator.comparing(Zone::getUpdatedAt).reversed())
                .toList();

        int from = Math.min(pageQuery.page() * pageQuery.size(), all.size());
        int to = Math.min(from + pageQuery.size(), all.size());
        List<Zone> slice = all.subList(from, to).stream()
                .map(FakeZonePersistencePort::snapshot)
                .toList();

        int totalPages = pageQuery.size() == 0
                ? 0
                : (int) Math.ceil((double) all.size() / pageQuery.size());

        return new PageResult<>(slice, pageQuery.page(), pageQuery.size(), all.size(), totalPages);
    }

    @Override
    public boolean hasActiveLocationsFor(UUID zoneId) {
        return zonesWithActiveLocations.contains(zoneId);
    }

    // test hooks
    void triggerOptimisticLockOnNextUpdate() {
        this.failNextUpdateAsOptimisticLock = true;
    }

    void markZoneAsHavingActiveLocations(UUID zoneId) {
        zonesWithActiveLocations.add(zoneId);
    }

    Zone stored(UUID id) {
        return byId.get(id);
    }

    int size() {
        return byId.size();
    }

    private static Zone snapshot(Zone source) {
        return Zone.reconstitute(
                source.getId(),
                source.getWarehouseId(),
                source.getZoneCode(),
                source.getName(),
                source.getZoneType(),
                source.getStatus(),
                source.getVersion(),
                source.getCreatedAt(),
                source.getCreatedBy(),
                source.getUpdatedAt(),
                source.getUpdatedBy());
    }

    private static Zone withVersion(Zone source, long newVersion) {
        return Zone.reconstitute(
                source.getId(),
                source.getWarehouseId(),
                source.getZoneCode(),
                source.getName(),
                source.getZoneType(),
                source.getStatus(),
                newVersion,
                source.getCreatedAt(),
                source.getCreatedBy(),
                Instant.now(),
                source.getUpdatedBy());
    }
}
