package com.example.erp.readmodel.adapter.outbound.persistence.adapter;

import com.example.erp.readmodel.adapter.outbound.persistence.jpa.JobGradeProjJpaEntity;
import com.example.erp.readmodel.adapter.outbound.persistence.jpa.JobGradeProjJpaRepository;
import com.example.erp.readmodel.domain.common.MasterStatus;
import com.example.erp.readmodel.domain.projection.JobGradeProjection;
import com.example.erp.readmodel.domain.projection.repository.JobGradeProjectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JobGradeProjectionRepositoryImpl implements JobGradeProjectionRepository {

    private final JobGradeProjJpaRepository jpa;

    @Override
    public Optional<JobGradeProjection> findById(String id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public Map<String, JobGradeProjection> findAllByIds(Collection<String> ids) {
        Map<String, JobGradeProjection> out = new LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        for (JobGradeProjJpaEntity e : jpa.findByIdIn(ids)) {
            out.put(e.getId(), toDomain(e));
        }
        return out;
    }

    @Override
    public void save(JobGradeProjection projection) {
        JobGradeProjJpaEntity e = jpa.findById(projection.id())
                .orElseGet(JobGradeProjJpaEntity::new);
        e.setId(projection.id());
        e.setCode(projection.code());
        e.setName(projection.name());
        e.setDisplayOrder(projection.displayOrder());
        e.setStatus(projection.status().name());
        e.setEffectiveFrom(projection.effectiveFrom());
        e.setEffectiveTo(projection.effectiveTo());
        e.setLastEventAt(projection.lastEventAt());
        e.setLastEventId(projection.lastEventId());
        jpa.save(e);
    }

    private JobGradeProjection toDomain(JobGradeProjJpaEntity e) {
        return JobGradeProjection.of(
                e.getId(), e.getCode(), e.getName(), e.getDisplayOrder(),
                MasterStatus.valueOf(e.getStatus()), e.getEffectiveFrom(), e.getEffectiveTo(),
                e.getLastEventAt(), e.getLastEventId());
    }
}
