package com.wms.master.application.service;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.application.port.out.SkuPersistencePort;
import com.wms.master.application.query.ListSkusCriteria;
import com.wms.master.domain.exception.BarcodeDuplicateException;
import com.wms.master.domain.exception.SkuCodeDuplicateException;
import com.wms.master.domain.exception.SkuNotFoundException;
import com.wms.master.domain.model.Sku;
import java.time.Instant;
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
 * In-memory fake for {@link SkuPersistencePort}. Models the two unique
 * constraints (sku_code — case-insensitive because storage is UPPERCASE; and
 * barcode — partial: only non-null must be unique) plus version-based
 * optimistic locking, so service-level tests exercise every error path
 * without Docker / Testcontainers.
 */
class FakeSkuPersistencePort implements SkuPersistencePort {

    private final Map<UUID, Sku> byId = new HashMap<>();
    private boolean failNextUpdateAsOptimisticLock = false;
    private boolean hasActiveLots = false;

    @Override
    public Sku insert(Sku newSku) {
        boolean skuCodeTaken = byId.values().stream()
                .anyMatch(s -> s.getSkuCode().equals(newSku.getSkuCode()));
        if (skuCodeTaken) {
            throw new SkuCodeDuplicateException(newSku.getSkuCode());
        }
        if (newSku.getBarcode() != null) {
            boolean barcodeTaken = byId.values().stream()
                    .anyMatch(s -> Objects.equals(s.getBarcode(), newSku.getBarcode()));
            if (barcodeTaken) {
                throw new BarcodeDuplicateException(newSku.getBarcode());
            }
        }
        Sku snapshot = snapshot(newSku);
        byId.put(snapshot.getId(), snapshot);
        return snapshot(snapshot);
    }

    @Override
    public Sku update(Sku modified) {
        Sku stored = byId.get(modified.getId());
        if (stored == null) {
            throw new SkuNotFoundException(modified.getId().toString());
        }
        if (failNextUpdateAsOptimisticLock) {
            failNextUpdateAsOptimisticLock = false;
            throw new ObjectOptimisticLockingFailureException(Sku.class, modified.getId());
        }
        if (stored.getVersion() != modified.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(Sku.class, modified.getId());
        }
        if (modified.getBarcode() != null) {
            boolean barcodeTakenByOther = byId.values().stream()
                    .anyMatch(s -> !s.getId().equals(modified.getId())
                            && Objects.equals(s.getBarcode(), modified.getBarcode()));
            if (barcodeTakenByOther) {
                throw new BarcodeDuplicateException(modified.getBarcode());
            }
        }
        Sku bumped = withVersion(snapshot(modified), modified.getVersion() + 1);
        byId.put(bumped.getId(), bumped);
        return snapshot(bumped);
    }

    @Override
    public Optional<Sku> findById(UUID id) {
        return Optional.ofNullable(byId.get(id)).map(FakeSkuPersistencePort::snapshot);
    }

    @Override
    public Optional<Sku> findBySkuCode(String skuCodeUpper) {
        return byId.values().stream()
                .filter(s -> s.getSkuCode().equals(skuCodeUpper))
                .findFirst()
                .map(FakeSkuPersistencePort::snapshot);
    }

    @Override
    public Optional<Sku> findByBarcode(String barcode) {
        return byId.values().stream()
                .filter(s -> Objects.equals(s.getBarcode(), barcode))
                .findFirst()
                .map(FakeSkuPersistencePort::snapshot);
    }

    @Override
    public PageResult<Sku> findPage(ListSkusCriteria criteria, PageQuery pageQuery) {
        Stream<Sku> filtered = byId.values().stream();
        if (criteria.status() != null) {
            filtered = filtered.filter(s -> s.getStatus() == criteria.status());
        }
        if (criteria.trackingType() != null) {
            filtered = filtered.filter(s -> s.getTrackingType() == criteria.trackingType());
        }
        if (criteria.baseUom() != null) {
            filtered = filtered.filter(s -> s.getBaseUom() == criteria.baseUom());
        }
        if (criteria.barcode() != null) {
            filtered = filtered.filter(s -> Objects.equals(s.getBarcode(), criteria.barcode()));
        }
        if (criteria.hasQueryText()) {
            String q = criteria.q().toLowerCase();
            filtered = filtered.filter(s ->
                    s.getSkuCode().toLowerCase().contains(q)
                            || s.getName().toLowerCase().contains(q));
        }

        List<Sku> all = filtered
                .sorted(Comparator.comparing(Sku::getUpdatedAt).reversed())
                .toList();

        int from = Math.min(pageQuery.page() * pageQuery.size(), all.size());
        int to = Math.min(from + pageQuery.size(), all.size());
        List<Sku> slice = all.subList(from, to).stream()
                .map(FakeSkuPersistencePort::snapshot)
                .toList();

        int totalPages = pageQuery.size() == 0
                ? 0
                : (int) Math.ceil((double) all.size() / pageQuery.size());

        return new PageResult<>(slice, pageQuery.page(), pageQuery.size(), all.size(), totalPages);
    }

    @Override
    public boolean hasActiveLotsFor(UUID skuId) {
        return hasActiveLots;
    }

    // test hooks
    void triggerOptimisticLockOnNextUpdate() {
        this.failNextUpdateAsOptimisticLock = true;
    }

    void setHasActiveLots(boolean value) {
        this.hasActiveLots = value;
    }

    Sku stored(UUID id) {
        return byId.get(id);
    }

    int size() {
        return byId.size();
    }

    private static Sku snapshot(Sku source) {
        return Sku.reconstitute(
                source.getId(),
                source.getSkuCode(),
                source.getName(),
                source.getDescription(),
                source.getBarcode(),
                source.getBaseUom(),
                source.getTrackingType(),
                source.getWeightGrams(),
                source.getVolumeMl(),
                source.getHazardClass(),
                source.getShelfLifeDays(),
                source.getStatus(),
                source.getVersion(),
                source.getCreatedAt(),
                source.getCreatedBy(),
                source.getUpdatedAt(),
                source.getUpdatedBy());
    }

    private static Sku withVersion(Sku source, long newVersion) {
        return Sku.reconstitute(
                source.getId(),
                source.getSkuCode(),
                source.getName(),
                source.getDescription(),
                source.getBarcode(),
                source.getBaseUom(),
                source.getTrackingType(),
                source.getWeightGrams(),
                source.getVolumeMl(),
                source.getHazardClass(),
                source.getShelfLifeDays(),
                source.getStatus(),
                newVersion,
                source.getCreatedAt(),
                source.getCreatedBy(),
                Instant.now(),
                source.getUpdatedBy());
    }
}
