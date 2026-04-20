package com.wms.master.application.service;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.application.port.out.LotPersistencePort;
import com.wms.master.application.query.ListLotsCriteria;
import com.wms.master.domain.exception.LotNoDuplicateException;
import com.wms.master.domain.exception.LotNotFoundException;
import com.wms.master.domain.model.Lot;
import com.wms.master.domain.model.LotStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * In-memory fake for {@link LotPersistencePort}. Models per-SKU unique
 * {@code lot_no} and version-based optimistic locking so service-level tests
 * exercise every error path without Docker.
 */
class FakeLotPersistencePort implements LotPersistencePort {

    private final Map<UUID, Lot> byId = new HashMap<>();
    private boolean failNextUpdateAsOptimisticLock = false;
    /** when != null, the next update() for this lot id throws once. */
    private UUID failSpecificUpdateAsOptimisticLock = null;

    @Override
    public Lot insert(Lot newLot) {
        boolean lotNoTaken = byId.values().stream().anyMatch(l ->
                l.getSkuId().equals(newLot.getSkuId())
                        && l.getLotNo().equals(newLot.getLotNo()));
        if (lotNoTaken) {
            throw new LotNoDuplicateException(newLot.getSkuId(), newLot.getLotNo());
        }
        Lot snapshot = snapshot(newLot);
        byId.put(snapshot.getId(), snapshot);
        return snapshot(snapshot);
    }

    @Override
    public Lot update(Lot modified) {
        Lot stored = byId.get(modified.getId());
        if (stored == null) {
            throw new LotNotFoundException(modified.getId().toString());
        }
        if (failNextUpdateAsOptimisticLock) {
            failNextUpdateAsOptimisticLock = false;
            throw new ObjectOptimisticLockingFailureException(Lot.class, modified.getId());
        }
        if (failSpecificUpdateAsOptimisticLock != null
                && failSpecificUpdateAsOptimisticLock.equals(modified.getId())) {
            failSpecificUpdateAsOptimisticLock = null;
            throw new ObjectOptimisticLockingFailureException(Lot.class, modified.getId());
        }
        if (stored.getVersion() != modified.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(Lot.class, modified.getId());
        }
        Lot bumped = withVersion(snapshot(modified), modified.getVersion() + 1);
        byId.put(bumped.getId(), bumped);
        return snapshot(bumped);
    }

    @Override
    public Optional<Lot> findById(UUID id) {
        return Optional.ofNullable(byId.get(id)).map(FakeLotPersistencePort::snapshot);
    }

    @Override
    public PageResult<Lot> findPage(ListLotsCriteria criteria, PageQuery pageQuery) {
        Stream<Lot> filtered = byId.values().stream();
        if (criteria.skuId() != null) {
            filtered = filtered.filter(l -> Objects.equals(criteria.skuId(), l.getSkuId()));
        }
        if (criteria.status() != null) {
            filtered = filtered.filter(l -> l.getStatus() == criteria.status());
        }
        if (criteria.expiryBefore() != null) {
            filtered = filtered.filter(l -> l.getExpiryDate() != null
                    && l.getExpiryDate().isBefore(criteria.expiryBefore()));
        }
        if (criteria.expiryAfter() != null) {
            filtered = filtered.filter(l -> l.getExpiryDate() != null
                    && l.getExpiryDate().isAfter(criteria.expiryAfter()));
        }

        List<Lot> all = filtered
                .sorted(Comparator.comparing(Lot::getUpdatedAt).reversed())
                .toList();

        int from = Math.min(pageQuery.page() * pageQuery.size(), all.size());
        int to = Math.min(from + pageQuery.size(), all.size());
        List<Lot> slice = all.subList(from, to).stream()
                .map(FakeLotPersistencePort::snapshot)
                .toList();
        int totalPages = pageQuery.size() == 0
                ? 0
                : (int) Math.ceil((double) all.size() / pageQuery.size());

        return new PageResult<>(slice, pageQuery.page(), pageQuery.size(), all.size(), totalPages);
    }

    @Override
    public List<Lot> findAllByStatusAndExpiryDateBefore(LotStatus status, LocalDate cutoff) {
        return byId.values().stream()
                .filter(l -> l.getStatus() == status)
                .filter(l -> l.getExpiryDate() != null && l.getExpiryDate().isBefore(cutoff))
                .map(FakeLotPersistencePort::snapshot)
                .toList();
    }

    // ---------- test hooks ----------

    void triggerOptimisticLockOnNextUpdate() {
        this.failNextUpdateAsOptimisticLock = true;
    }

    void triggerOptimisticLockFor(UUID id) {
        this.failSpecificUpdateAsOptimisticLock = id;
    }

    Lot stored(UUID id) {
        return byId.get(id);
    }

    int size() {
        return byId.size();
    }

    private static Lot snapshot(Lot source) {
        return Lot.reconstitute(
                source.getId(),
                source.getSkuId(),
                source.getLotNo(),
                source.getManufacturedDate(),
                source.getExpiryDate(),
                source.getSupplierPartnerId(),
                source.getStatus(),
                source.getVersion(),
                source.getCreatedAt(),
                source.getCreatedBy(),
                source.getUpdatedAt(),
                source.getUpdatedBy());
    }

    private static Lot withVersion(Lot source, long newVersion) {
        return Lot.reconstitute(
                source.getId(),
                source.getSkuId(),
                source.getLotNo(),
                source.getManufacturedDate(),
                source.getExpiryDate(),
                source.getSupplierPartnerId(),
                source.getStatus(),
                newVersion,
                source.getCreatedAt(),
                source.getCreatedBy(),
                Instant.now(),
                source.getUpdatedBy());
    }
}
