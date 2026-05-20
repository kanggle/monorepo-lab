package com.example.erp.masterdata.infrastructure.persistence.jpa;

import com.example.erp.masterdata.domain.common.MasterStatus;
import com.example.erp.masterdata.domain.employee.Employee;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeJpaRepository extends JpaRepository<Employee, String> {

    Optional<Employee> findByIdAndTenantId(String id, String tenantId);

    Optional<Employee> findByEmployeeNumberAndTenantId(String employeeNumber, String tenantId);

    List<Employee> findAllByTenantId(String tenantId, Pageable pageable);

    List<Employee> findAllByDepartmentIdAndTenantIdAndStatus(String departmentId, String tenantId,
                                                            MasterStatus status);

    List<Employee> findAllByCostCenterIdAndTenantIdAndStatus(String costCenterId, String tenantId,
                                                             MasterStatus status);

    List<Employee> findAllByJobGradeIdAndTenantIdAndStatus(String jobGradeId, String tenantId,
                                                          MasterStatus status);
}
