package com.example.erp.readmodel.adapter.outbound.persistence.adapter;

import com.example.erp.readmodel.adapter.outbound.persistence.jpa.EmployeeProjJpaEntity;
import com.example.erp.readmodel.adapter.outbound.persistence.jpa.EmployeeProjJpaRepository;
import com.example.erp.readmodel.domain.common.MasterStatus;
import com.example.erp.readmodel.domain.projection.EmployeeProjection;
import com.example.erp.readmodel.domain.projection.repository.EmployeeProjectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class EmployeeProjectionRepositoryImpl implements EmployeeProjectionRepository {

    private final EmployeeProjJpaRepository jpa;

    @Override
    public Optional<EmployeeProjection> findById(String id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public List<EmployeeProjection> findPage(MasterStatus status, List<String> departmentIds,
                                             int page, int size) {
        String statusName = (status == null ? MasterStatus.ACTIVE : status).name();
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size));
        // departmentIds == null → no subtree filter; empty → an empty subtree
        // matches nothing (return empty page without hitting the DB on an empty IN).
        if (departmentIds == null) {
            return jpa.findByStatusOrderById(statusName, pageable).stream()
                    .map(this::toDomain).toList();
        }
        if (departmentIds.isEmpty()) {
            return List.of();
        }
        return jpa.findByStatusAndDepartmentIdInOrderById(statusName, departmentIds, pageable)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public long count(MasterStatus status, List<String> departmentIds) {
        String statusName = (status == null ? MasterStatus.ACTIVE : status).name();
        if (departmentIds == null) {
            return jpa.countByStatus(statusName);
        }
        if (departmentIds.isEmpty()) {
            return 0L;
        }
        return jpa.countByStatusAndDepartmentIdIn(statusName, departmentIds);
    }

    @Override
    public List<String> findIdsByDepartmentIdIn(java.util.Collection<String> departmentIds) {
        if (departmentIds == null || departmentIds.isEmpty()) {
            return List.of();
        }
        return jpa.findByDepartmentIdIn(departmentIds).stream()
                .map(EmployeeProjJpaEntity::getId).toList();
    }

    @Override
    public void save(EmployeeProjection projection) {
        EmployeeProjJpaEntity e = jpa.findById(projection.id())
                .orElseGet(EmployeeProjJpaEntity::new);
        e.setId(projection.id());
        e.setEmployeeNumber(projection.employeeNumber());
        e.setName(projection.name());
        e.setDepartmentId(projection.departmentId());
        e.setCostCenterId(projection.costCenterId());
        e.setJobGradeId(projection.jobGradeId());
        e.setStatus(projection.status().name());
        e.setEffectiveFrom(projection.effectiveFrom());
        e.setEffectiveTo(projection.effectiveTo());
        e.setLastEventAt(projection.lastEventAt());
        e.setLastEventId(projection.lastEventId());
        jpa.save(e);
    }

    private static int clampSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, 100);
    }

    private EmployeeProjection toDomain(EmployeeProjJpaEntity e) {
        return EmployeeProjection.of(
                e.getId(), e.getEmployeeNumber(), e.getName(), e.getDepartmentId(),
                e.getCostCenterId(), e.getJobGradeId(), MasterStatus.valueOf(e.getStatus()),
                e.getEffectiveFrom(), e.getEffectiveTo(), e.getLastEventAt(), e.getLastEventId());
    }
}
