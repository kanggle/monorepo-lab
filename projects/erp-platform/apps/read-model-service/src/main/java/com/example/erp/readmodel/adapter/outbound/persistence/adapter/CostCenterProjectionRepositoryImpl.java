package com.example.erp.readmodel.adapter.outbound.persistence.adapter;

import com.example.erp.readmodel.adapter.outbound.persistence.jpa.CostCenterProjJpaEntity;
import com.example.erp.readmodel.adapter.outbound.persistence.jpa.CostCenterProjJpaRepository;
import com.example.erp.readmodel.domain.common.MasterStatus;
import com.example.erp.readmodel.domain.projection.CostCenterProjection;
import com.example.erp.readmodel.domain.projection.repository.CostCenterProjectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CostCenterProjectionRepositoryImpl implements CostCenterProjectionRepository {

    private final CostCenterProjJpaRepository jpa;

    @Override
    public Optional<CostCenterProjection> findById(String id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public Map<String, CostCenterProjection> findAllByIds(Collection<String> ids) {
        Map<String, CostCenterProjection> out = new LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        for (CostCenterProjJpaEntity e : jpa.findByIdIn(ids)) {
            out.put(e.getId(), toDomain(e));
        }
        return out;
    }

    @Override
    public void save(CostCenterProjection projection) {
        CostCenterProjJpaEntity e = jpa.findById(projection.id())
                .orElseGet(CostCenterProjJpaEntity::new);
        e.setId(projection.id());
        e.setCode(projection.code());
        e.setName(projection.name());
        e.setDepartmentId(projection.departmentId());
        e.setStatus(projection.status().name());
        e.setEffectiveFrom(projection.effectiveFrom());
        e.setEffectiveTo(projection.effectiveTo());
        e.setLastEventAt(projection.lastEventAt());
        e.setLastEventId(projection.lastEventId());
        jpa.save(e);
    }

    private CostCenterProjection toDomain(CostCenterProjJpaEntity e) {
        return CostCenterProjection.of(
                e.getId(), e.getCode(), e.getName(), e.getDepartmentId(),
                MasterStatus.valueOf(e.getStatus()), e.getEffectiveFrom(), e.getEffectiveTo(),
                e.getLastEventAt(), e.getLastEventId());
    }
}
