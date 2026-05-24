package com.example.erp.masterdata.infrastructure.persistence.jpa;

import com.example.erp.masterdata.domain.common.MasterStatus;
import com.example.erp.masterdata.domain.costcenter.CostCenter;
import com.example.erp.masterdata.domain.costcenter.repository.CostCenterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CostCenterRepositoryImpl implements CostCenterRepository {

    private final CostCenterJpaRepository jpa;

    @Override
    public CostCenter save(CostCenter costCenter) {
        return jpa.save(costCenter);
    }

    @Override
    public Optional<CostCenter> findById(String id, String tenantId) {
        return jpa.findByIdAndTenantId(id, tenantId);
    }

    @Override
    public Optional<CostCenter> findByCode(String code, String tenantId) {
        return jpa.findByCodeAndTenantId(code, tenantId);
    }

    @Override
    public List<CostCenter> findAll(String tenantId, int page, int size) {
        return jpa.findAllByTenantId(tenantId, PageRequest.of(page, size));
    }

    @Override
    public List<CostCenter> findActiveByDepartmentId(String departmentId, String tenantId) {
        return jpa.findAllByDepartmentIdAndTenantIdAndStatus(departmentId, tenantId, MasterStatus.ACTIVE);
    }
}
