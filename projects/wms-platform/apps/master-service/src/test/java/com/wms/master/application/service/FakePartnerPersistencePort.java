package com.wms.master.application.service;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.application.port.out.PartnerPersistencePort;
import com.wms.master.application.query.ListPartnersCriteria;
import com.wms.master.domain.exception.PartnerCodeDuplicateException;
import com.wms.master.domain.exception.PartnerNotFoundException;
import com.wms.master.domain.model.Partner;
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
 * In-memory fake for {@link PartnerPersistencePort}. Models the partner_code
 * UNIQUE constraint plus version-based optimistic locking so service-level
 * tests exercise every error path without Docker / Testcontainers.
 */
class FakePartnerPersistencePort implements PartnerPersistencePort {

    private final Map<UUID, Partner> byId = new HashMap<>();
    private boolean failNextUpdateAsOptimisticLock = false;

    @Override
    public Partner insert(Partner newPartner) {
        boolean codeTaken = byId.values().stream()
                .anyMatch(p -> p.getPartnerCode().equals(newPartner.getPartnerCode()));
        if (codeTaken) {
            throw new PartnerCodeDuplicateException(newPartner.getPartnerCode());
        }
        Partner snapshot = snapshot(newPartner);
        byId.put(snapshot.getId(), snapshot);
        return snapshot(snapshot);
    }

    @Override
    public Partner update(Partner modified) {
        Partner stored = byId.get(modified.getId());
        if (stored == null) {
            throw new PartnerNotFoundException(modified.getId().toString());
        }
        if (failNextUpdateAsOptimisticLock) {
            failNextUpdateAsOptimisticLock = false;
            throw new ObjectOptimisticLockingFailureException(Partner.class, modified.getId());
        }
        if (stored.getVersion() != modified.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(Partner.class, modified.getId());
        }
        Partner bumped = withVersion(snapshot(modified), modified.getVersion() + 1);
        byId.put(bumped.getId(), bumped);
        return snapshot(bumped);
    }

    @Override
    public Optional<Partner> findById(UUID id) {
        return Optional.ofNullable(byId.get(id)).map(FakePartnerPersistencePort::snapshot);
    }

    @Override
    public Optional<Partner> findByCode(String partnerCode) {
        return byId.values().stream()
                .filter(p -> p.getPartnerCode().equals(partnerCode))
                .findFirst()
                .map(FakePartnerPersistencePort::snapshot);
    }

    @Override
    public boolean existsByCode(String partnerCode) {
        return byId.values().stream()
                .anyMatch(p -> p.getPartnerCode().equals(partnerCode));
    }

    @Override
    public PageResult<Partner> findPage(ListPartnersCriteria criteria, PageQuery pageQuery) {
        Stream<Partner> filtered = byId.values().stream();
        if (criteria.status() != null) {
            filtered = filtered.filter(p -> p.getStatus() == criteria.status());
        }
        if (criteria.partnerType() != null) {
            filtered = filtered.filter(p -> p.getPartnerType() == criteria.partnerType());
        }
        if (criteria.hasQueryText()) {
            String q = criteria.q().toLowerCase();
            filtered = filtered.filter(p ->
                    p.getPartnerCode().toLowerCase().contains(q)
                            || p.getName().toLowerCase().contains(q));
        }

        List<Partner> all = filtered
                .sorted(Comparator.comparing(Partner::getUpdatedAt).reversed())
                .toList();

        int from = Math.min(pageQuery.page() * pageQuery.size(), all.size());
        int to = Math.min(from + pageQuery.size(), all.size());
        List<Partner> slice = all.subList(from, to).stream()
                .map(FakePartnerPersistencePort::snapshot)
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

    Partner stored(UUID id) {
        return byId.get(id);
    }

    int size() {
        return byId.size();
    }

    private static Partner snapshot(Partner source) {
        return Partner.reconstitute(
                source.getId(),
                source.getPartnerCode(),
                source.getName(),
                source.getPartnerType(),
                source.getBusinessNumber(),
                source.getContactName(),
                source.getContactEmail(),
                source.getContactPhone(),
                source.getAddress(),
                source.getStatus(),
                source.getVersion(),
                source.getCreatedAt(),
                source.getCreatedBy(),
                source.getUpdatedAt(),
                source.getUpdatedBy());
    }

    private static Partner withVersion(Partner source, long newVersion) {
        return Partner.reconstitute(
                source.getId(),
                source.getPartnerCode(),
                source.getName(),
                source.getPartnerType(),
                source.getBusinessNumber(),
                source.getContactName(),
                source.getContactEmail(),
                source.getContactPhone(),
                source.getAddress(),
                source.getStatus(),
                newVersion,
                source.getCreatedAt(),
                source.getCreatedBy(),
                Instant.now(),
                source.getUpdatedBy());
    }
}
