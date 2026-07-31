package com.example.erp.masterdata.infrastructure.persistence.jpa;

import com.example.common.page.PageResult;
import com.example.erp.masterdata.domain.common.MasterStatus;
import com.example.erp.masterdata.domain.costcenter.CostCenter;
import com.example.erp.masterdata.domain.costcenter.repository.CostCenterListFilter;
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
    public PageResult<CostCenter> findAll(String tenantId, CostCenterListFilter filter, int page, int size) {
        MasterStatus status = MasterStatusFilters.toStatus(filter.active());
        List<CostCenter> content = jpa.findFiltered(tenantId, status, filter.departmentId(),
                filter.asOf(), PageRequest.of(page, size));
        long total = jpa.countFiltered(tenantId, status, filter.departmentId(), filter.asOf());
        // size is always >= 1 here — PageRequest.of() above throws for size < 1 before this
        // line is reached, so the ceiling-division is divide-by-zero-safe.
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResult<>(content, page, size, total, totalPages);
    }

    @Override
    public List<CostCenter> findActiveByDepartmentId(String departmentId, String tenantId) {
        return jpa.findAllByDepartmentIdAndTenantIdAndStatus(departmentId, tenantId, MasterStatus.ACTIVE);
    }
}
