package com.wms.master.application.service;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.application.port.out.WarehousePersistencePort;
import com.wms.master.application.query.WarehouseListCriteria;
import com.wms.master.domain.exception.WarehouseCodeDuplicateException;
import com.wms.master.domain.exception.WarehouseNotFoundException;
import com.wms.master.domain.model.Warehouse;
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
 * In-memory fake for {@link WarehousePersistencePort}. Models version-based
 * optimistic locking and the unique-code constraint with the same semantics as
 * the JPA adapter so service-level tests can exercise every error path without
 * Docker / Testcontainers.
 */
class FakeWarehousePersistencePort implements WarehousePersistencePort {

    private final Map<UUID, Warehouse> byId = new HashMap<>();
    private final Set<UUID> warehousesWithActiveZones = new HashSet<>();
    private boolean failNextUpdateAsOptimisticLock = false;

    @Override
    public Warehouse insert(Warehouse newWarehouse) {
        boolean codeTaken = byId.values().stream()
                .anyMatch(w -> w.getWarehouseCode().equals(newWarehouse.getWarehouseCode()));
        if (codeTaken) {
            throw new WarehouseCodeDuplicateException(newWarehouse.getWarehouseCode());
        }
        Warehouse snapshot = snapshot(newWarehouse);
        byId.put(snapshot.getId(), snapshot);
        return snapshot(snapshot);
    }

    @Override
    public Warehouse update(Warehouse modified) {
        Warehouse stored = byId.get(modified.getId());
        if (stored == null) {
            throw new WarehouseNotFoundException(modified.getId().toString());
        }
        if (failNextUpdateAsOptimisticLock) {
            failNextUpdateAsOptimisticLock = false;
            throw new ObjectOptimisticLockingFailureException(
                    Warehouse.class, modified.getId());
        }
        if (stored.getVersion() != modified.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(
                    Warehouse.class, modified.getId());
        }
        Warehouse bumped = withVersion(snapshot(modified), modified.getVersion() + 1);
        byId.put(bumped.getId(), bumped);
        return snapshot(bumped);
    }

    @Override
    public Optional<Warehouse> findById(UUID id) {
        return Optional.ofNullable(byId.get(id)).map(FakeWarehousePersistencePort::snapshot);
    }

    @Override
    public Optional<Warehouse> findByCode(String warehouseCode) {
        return byId.values().stream()
                .filter(w -> w.getWarehouseCode().equals(warehouseCode))
                .findFirst()
                .map(FakeWarehousePersistencePort::snapshot);
    }

    @Override
    public PageResult<Warehouse> findPage(WarehouseListCriteria criteria, PageQuery pageQuery) {
        Stream<Warehouse> filtered = byId.values().stream();
        if (criteria.status() != null) {
            filtered = filtered.filter(w -> w.getStatus() == criteria.status());
        }
        if (criteria.hasQueryText()) {
            String q = criteria.q().toLowerCase();
            filtered = filtered.filter(w ->
                    w.getWarehouseCode().toLowerCase().contains(q)
                            || w.getName().toLowerCase().contains(q));
        }

        List<Warehouse> all = filtered
                .sorted(Comparator.comparing(Warehouse::getUpdatedAt).reversed())
                .toList();

        int from = Math.min(pageQuery.page() * pageQuery.size(), all.size());
        int to = Math.min(from + pageQuery.size(), all.size());
        List<Warehouse> slice = all.subList(from, to).stream()
                .map(FakeWarehousePersistencePort::snapshot)
                .toList();

        int totalPages = pageQuery.size() == 0
                ? 0
                : (int) Math.ceil((double) all.size() / pageQuery.size());

        return new PageResult<>(slice, pageQuery.page(), pageQuery.size(), all.size(), totalPages);
    }

    @Override
    public boolean hasActiveZonesFor(UUID warehouseId) {
        return warehousesWithActiveZones.contains(warehouseId);
    }

    // test hooks
    void triggerOptimisticLockOnNextUpdate() {
        this.failNextUpdateAsOptimisticLock = true;
    }

    void markWarehouseAsHavingActiveZones(UUID warehouseId) {
        warehousesWithActiveZones.add(warehouseId);
    }

    Warehouse stored(UUID id) {
        return byId.get(id);
    }

    int size() {
        return byId.size();
    }

    // copy via reflection so tests can assert the stored state is isolated from
    // later mutations on the returned instance (mirrors a real DB load returning
    // a fresh aggregate per call).
    private static Warehouse snapshot(Warehouse source) {
        return Warehouse.reconstitute(
                source.getId(),
                source.getWarehouseCode(),
                source.getName(),
                source.getAddress(),
                source.getTimezone(),
                source.getStatus(),
                source.getVersion(),
                source.getCreatedAt(),
                source.getCreatedBy(),
                source.getUpdatedAt(),
                source.getUpdatedBy());
    }

    private static Warehouse withVersion(Warehouse source, long newVersion) {
        return Warehouse.reconstitute(
                source.getId(),
                source.getWarehouseCode(),
                source.getName(),
                source.getAddress(),
                source.getTimezone(),
                source.getStatus(),
                newVersion,
                source.getCreatedAt(),
                source.getCreatedBy(),
                Instant.now(),
                source.getUpdatedBy());
    }
}
