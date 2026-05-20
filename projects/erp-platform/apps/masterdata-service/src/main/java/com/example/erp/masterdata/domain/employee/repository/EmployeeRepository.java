package com.example.erp.masterdata.domain.employee.repository;

import com.example.erp.masterdata.domain.employee.Employee;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository {

    Employee save(Employee employee);

    Optional<Employee> findById(String id, String tenantId);

    Optional<Employee> findByEmployeeNumber(String employeeNumber, String tenantId);

    List<Employee> findAll(String tenantId, int page, int size);

    /** Active employees referencing the given department — for retire guard. */
    List<Employee> findActiveByDepartmentId(String departmentId, String tenantId);

    /** Active employees referencing the given cost center — for retire guard. */
    List<Employee> findActiveByCostCenterId(String costCenterId, String tenantId);

    /** Active employees referencing the given job grade — for retire guard. */
    List<Employee> findActiveByJobGradeId(String jobGradeId, String tenantId);
}
