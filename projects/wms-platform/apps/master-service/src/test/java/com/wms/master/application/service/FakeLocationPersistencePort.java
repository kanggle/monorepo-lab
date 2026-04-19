package com.wms.master.application.service;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.application.port.out.LocationPersistencePort;
import com.wms.master.application.query.ListLocationsCriteria;
import com.wms.master.domain.exception.LocationCodeDuplicateException;
import com.wms.master.domain.exception.LocationNotFoundException;
import com.wms.master.domain.model.Location;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * In-memory fake for {@link LocationPersistencePort}. Models the global unique
 * {@code locationCode} constraint and version-based optimistic locking with
 * the same semantics as the JPA adapter.
 */
class FakeLocationPersistencePort implements LocationPersistencePort {

    private final Map<UUID, Location> byId = new HashMap<>();
    private boolean failNextUpdateAsOptimisticLock = false;

    @Override
    public Location insert(Location newLocation) {
        boolean codeTaken = byId.values().stream()
                .anyMatch(l -> l.getLocationCode().equals(newLocation.getLocationCode()));
        if (codeTaken) {
            throw new LocationCodeDuplicateException(newLocation.getLocationCode());
        }
        Location snapshot = snapshot(newLocation);
        byId.put(snapshot.getId(), snapshot);
        return snapshot(snapshot);
    }

    @Override
    public Location update(Location modified) {
        Location stored = byId.get(modified.getId());
        if (stored == null) {
            throw new LocationNotFoundException(modified.getId().toString());
        }
        if (failNextUpdateAsOptimisticLock) {
            failNextUpdateAsOptimisticLock = false;
            throw new ObjectOptimisticLockingFailureException(Location.class, modified.getId());
        }
        if (stored.getVersion() != modified.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(Location.class, modified.getId());
        }
        Location bumped = withVersion(snapshot(modified), modified.getVersion() + 1);
        byId.put(bumped.getId(), bumped);
        return snapshot(bumped);
    }

    @Override
    public Optional<Location> findById(UUID id) {
        return Optional.ofNullable(byId.get(id)).map(FakeLocationPersistencePort::snapshot);
    }

    @Override
    public Optional<Location> findByLocationCode(String locationCode) {
        return byId.values().stream()
                .filter(l -> l.getLocationCode().equals(locationCode))
                .findFirst()
                .map(FakeLocationPersistencePort::snapshot);
    }

    @Override
    public PageResult<Location> findPage(ListLocationsCriteria criteria, PageQuery pageQuery) {
        Stream<Location> filtered = byId.values().stream();
        if (criteria.warehouseId() != null) {
            filtered = filtered.filter(l -> l.getWarehouseId().equals(criteria.warehouseId()));
        }
        if (criteria.zoneId() != null) {
            filtered = filtered.filter(l -> l.getZoneId().equals(criteria.zoneId()));
        }
        if (criteria.locationType() != null) {
            filtered = filtered.filter(l -> l.getLocationType() == criteria.locationType());
        }
        if (criteria.locationCode() != null) {
            filtered = filtered.filter(l -> l.getLocationCode().equals(criteria.locationCode()));
        }
        if (criteria.status() != null) {
            filtered = filtered.filter(l -> l.getStatus() == criteria.status());
        }

        List<Location> all = filtered
                .sorted(Comparator.comparing(Location::getUpdatedAt).reversed())
                .toList();

        int from = Math.min(pageQuery.page() * pageQuery.size(), all.size());
        int to = Math.min(from + pageQuery.size(), all.size());
        List<Location> slice = all.subList(from, to).stream()
                .map(FakeLocationPersistencePort::snapshot)
                .toList();

        int totalPages = pageQuery.size() == 0
                ? 0
                : (int) Math.ceil((double) all.size() / pageQuery.size());

        return new PageResult<>(slice, pageQuery.page(), pageQuery.size(), all.size(), totalPages);
    }

    // test hooks
    void triggerOptimisticLockOnNextUpdate() {
        this.failNextUpdateAsOptimisticLock = true;
    }

    int size() {
        return byId.size();
    }

    private static Location snapshot(Location source) {
        return Location.reconstitute(
                source.getId(),
                source.getWarehouseId(),
                source.getZoneId(),
                source.getLocationCode(),
                source.getAisle(),
                source.getRack(),
                source.getLevel(),
                source.getBin(),
                source.getLocationType(),
                source.getCapacityUnits(),
                source.getStatus(),
                source.getVersion(),
                source.getCreatedAt(),
                source.getCreatedBy(),
                source.getUpdatedAt(),
                source.getUpdatedBy());
    }

    private static Location withVersion(Location source, long newVersion) {
        return Location.reconstitute(
                source.getId(),
                source.getWarehouseId(),
                source.getZoneId(),
                source.getLocationCode(),
                source.getAisle(),
                source.getRack(),
                source.getLevel(),
                source.getBin(),
                source.getLocationType(),
                source.getCapacityUnits(),
                source.getStatus(),
                newVersion,
                source.getCreatedAt(),
                source.getCreatedBy(),
                Instant.now(),
                source.getUpdatedBy());
    }
}
