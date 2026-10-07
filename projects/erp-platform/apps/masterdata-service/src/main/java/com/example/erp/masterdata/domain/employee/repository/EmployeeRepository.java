package com.example.erp.masterdata.domain.employee.repository;

import com.example.common.page.PageResult;
import com.example.erp.masterdata.domain.employee.Employee;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository {

    Employee save(Employee employee);

    Optional<Employee> findById(String id, String tenantId);

    Optional<Employee> findByEmployeeNumber(String employeeNumber, String tenantId);

    /** The employee linked to IAM account {@code accountId} in this tenant (at most one — V3 unique). */
    Optional<Employee> findByAccountId(String accountId, String tenantId);

    /**
     * Persists a change to {@code employees.account_id} and flushes inside the caller's
     * transaction, so a concurrent accept that linked the same account to another employee
     * trips {@code uq_employees_tenant_account} here and is translated to
     * {@code EMPLOYEE_LINK_CONFLICT} ({@code account_already_linked}) — never a 500
     * (TASK-ERP-BE-044 Edge Case).
     */
    Employee saveAccountLink(Employee employee);

    /**
     * Filtered, paginated list with the TRUE total-row count
     * (masterdata-api.md § GET /employees + § PageMeta).
     */
    PageResult<Employee> findAll(String tenantId, EmployeeListFilter filter, int page, int size);

    /** Active employees referencing the given department — for retire guard. */
    List<Employee> findActiveByDepartmentId(String departmentId, String tenantId);

    /** Active employees referencing the given cost center — for retire guard. */
    List<Employee> findActiveByCostCenterId(String costCenterId, String tenantId);

    /** Active employees referencing the given job grade — for retire guard. */
    List<Employee> findActiveByJobGradeId(String jobGradeId, String tenantId);
}
