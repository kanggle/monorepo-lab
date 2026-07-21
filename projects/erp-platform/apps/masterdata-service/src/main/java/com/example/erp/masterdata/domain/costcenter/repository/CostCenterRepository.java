package com.example.erp.masterdata.domain.costcenter.repository;

import com.example.erp.masterdata.domain.common.PageResult;
import com.example.erp.masterdata.domain.costcenter.CostCenter;

import java.util.List;
import java.util.Optional;

public interface CostCenterRepository {
    CostCenter save(CostCenter costCenter);

    Optional<CostCenter> findById(String id, String tenantId);

    Optional<CostCenter> findByCode(String code, String tenantId);

    /**
     * Filtered, paginated list with the TRUE total-row count
     * (masterdata-api.md § GET /cost-centers + § PageMeta).
     */
    PageResult<CostCenter> findAll(String tenantId, CostCenterListFilter filter, int page, int size);

    /** Active cost-centers referencing the given department — for retire guard. */
    List<CostCenter> findActiveByDepartmentId(String departmentId, String tenantId);
}
