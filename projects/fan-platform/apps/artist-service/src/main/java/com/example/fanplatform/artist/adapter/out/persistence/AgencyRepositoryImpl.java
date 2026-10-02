package com.example.fanplatform.artist.adapter.out.persistence;

import com.example.common.page.PageResult;
import com.example.fanplatform.artist.application.exception.AgencyNameConflictException;
import com.example.fanplatform.artist.application.port.out.AgencyRepository;
import com.example.fanplatform.artist.domain.agency.Agency;
import com.example.fanplatform.artist.domain.agency.AgencyId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Persistence adapter for {@link AgencyRepository} (TASK-MONO-748). */
@Repository
class AgencyRepositoryImpl implements AgencyRepository {

    private static final String NAME_CONSTRAINT = "uq_agencies_tenant_name";

    private final AgencyJpaRepository jpa;

    AgencyRepositoryImpl(AgencyJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Agency insert(Agency a) {
        AgencyJpaEntity entity = new AgencyJpaEntity(
                a.getId().value(), a.getTenantId(), a.getName(), a.getStatus(),
                a.getStoreSellerId(), a.getCreatedAt(), a.getUpdatedAt(),
                null /* version null → INSERT */);
        try {
            return toDomain(jpa.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            if (mentionsConstraint(e, NAME_CONSTRAINT)) {
                throw new AgencyNameConflictException(a.getName());
            }
            throw e;
        }
    }

    @Override
    public Agency update(Agency a) {
        AgencyJpaEntity managed = jpa.findById(a.getId().value())
                .orElseThrow(() -> new IllegalStateException(
                        "Agency disappeared between load and save: " + a.getId().value()));
        managed.applyMutable(a.getName(), a.getStatus(), a.getStoreSellerId(), a.getUpdatedAt());
        try {
            return toDomain(jpa.saveAndFlush(managed));
        } catch (DataIntegrityViolationException e) {
            if (mentionsConstraint(e, NAME_CONSTRAINT)) {
                throw new AgencyNameConflictException(a.getName());
            }
            throw e;
        }
    }

    @Override
    public Optional<Agency> findById(AgencyId id, String tenantId) {
        return jpa.findByIdAndTenantId(id.value(), tenantId).map(this::toDomain);
    }

    @Override
    public boolean existsByTenantIdAndName(String tenantId, String name) {
        return jpa.existsByTenantIdAndName(tenantId, name);
    }

    @Override
    public PageResult<Agency> findPage(String tenantId, int page, int size) {
        Sort sort = Sort.by(Sort.Direction.ASC, "name").and(Sort.by(Sort.Direction.ASC, "id"));
        Page<AgencyJpaEntity> p = jpa.findByTenantId(tenantId, PageRequest.of(page, size, sort));
        List<Agency> items = p.getContent().stream().map(this::toDomain).toList();
        return new PageResult<>(items, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }

    @Override
    public Map<String, String> findNamesByIds(String tenantId, Collection<String> ids) {
        Map<String, String> out = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        for (AgencyJpaEntity e : jpa.findByTenantIdAndIdIn(tenantId, ids)) {
            out.put(e.getId(), e.getName());
        }
        return out;
    }

    private Agency toDomain(AgencyJpaEntity e) {
        return Agency.reconstitute(
                AgencyId.of(e.getId()), e.getTenantId(), e.getName(), e.getStatus(),
                e.getStoreSellerId(), e.getCreatedAt(), e.getUpdatedAt(),
                e.getVersion() == null ? 0L : e.getVersion());
    }

    private static boolean mentionsConstraint(Throwable t, String constraint) {
        Throwable cur = t;
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null && msg.toLowerCase().contains(constraint)) return true;
            cur = cur.getCause();
        }
        return false;
    }
}
