package com.example.erp.masterdata.infrastructure.persistence.jpa;

import com.example.erp.masterdata.domain.department.Department;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepartmentJpaRepository extends JpaRepository<Department, String> {

    Optional<Department> findByIdAndTenantId(String id, String tenantId);

    Optional<Department> findByCodeAndTenantId(String code, String tenantId);

    List<Department> findAllByTenantId(String tenantId, Pageable pageable);

    List<Department> findAllByParentIdAndTenantIdAndStatus(String parentId, String tenantId,
                                                          com.example.erp.masterdata.domain.common.MasterStatus status);
}
