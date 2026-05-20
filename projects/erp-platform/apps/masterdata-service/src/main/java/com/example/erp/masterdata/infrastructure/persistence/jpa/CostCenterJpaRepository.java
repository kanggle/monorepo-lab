package com.example.erp.masterdata.infrastructure.persistence.jpa;

import com.example.erp.masterdata.domain.common.MasterStatus;
import com.example.erp.masterdata.domain.costcenter.CostCenter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CostCenterJpaRepository extends JpaRepository<CostCenter, String> {

    Optional<CostCenter> findByIdAndTenantId(String id, String tenantId);

    Optional<CostCenter> findByCodeAndTenantId(String code, String tenantId);

    List<CostCenter> findAllByTenantId(String tenantId, Pageable pageable);

    List<CostCenter> findAllByDepartmentIdAndTenantIdAndStatus(String departmentId, String tenantId,
                                                               MasterStatus status);
}
