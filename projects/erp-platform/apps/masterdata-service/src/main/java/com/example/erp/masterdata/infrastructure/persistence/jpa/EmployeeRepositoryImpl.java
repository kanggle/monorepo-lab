package com.example.erp.masterdata.infrastructure.persistence.jpa;

import com.example.common.page.PageResult;
import com.example.common.persistence.DataIntegrityViolations;
import com.example.erp.masterdata.domain.common.MasterStatus;
import com.example.erp.masterdata.domain.employee.Employee;
import com.example.erp.masterdata.domain.employee.repository.EmployeeListFilter;
import com.example.erp.masterdata.domain.employee.repository.EmployeeRepository;
import com.example.erp.masterdata.domain.error.DomainErrors.EmployeeLinkConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class EmployeeRepositoryImpl implements EmployeeRepository {

    private final EmployeeJpaRepository jpa;

    @Override
    public Employee save(Employee employee) {
        return jpa.save(employee);
    }

    @Override
    public Optional<Employee> findById(String id, String tenantId) {
        return jpa.findByIdAndTenantId(id, tenantId);
    }

    @Override
    public Optional<Employee> findByEmployeeNumber(String employeeNumber, String tenantId) {
        return jpa.findByEmployeeNumberAndTenantId(employeeNumber, tenantId);
    }

    @Override
    public Optional<Employee> findByAccountId(String accountId, String tenantId) {
        return jpa.findByAccountIdAndTenantId(accountId, tenantId);
    }

    /**
     * The link path changes only {@code account_id}, so of the two unique indexes on
     * {@code employees} ({@code uq_employees_tenant_number}, {@code uq_employees_tenant_account})
     * only the account one can fire here.
     */
    @Override
    public Employee saveAccountLink(Employee employee) {
        try {
            return jpa.saveAndFlush(employee);
        } catch (DataIntegrityViolationException e) {
            if (DataIntegrityViolations.isUniqueViolation(e)) {
                throw new EmployeeLinkConflictException(EmployeeLinkConflictException.ACCOUNT_ALREADY_LINKED,
                        "Account is already linked to another employee in this tenant");
            }
            throw e;
        }
    }

    @Override
    public PageResult<Employee> findAll(String tenantId, EmployeeListFilter filter, int page, int size) {
        MasterStatus status = MasterStatusFilters.toStatus(filter.active());
        List<Employee> content = jpa.findFiltered(tenantId, status, filter.departmentId(),
                filter.costCenterId(), filter.asOf(), PageRequest.of(page, size));
        long total = jpa.countFiltered(tenantId, status, filter.departmentId(),
                filter.costCenterId(), filter.asOf());
        // size is always >= 1 here — PageRequest.of() above throws for size < 1 before this
        // line is reached, so the ceiling-division is divide-by-zero-safe.
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResult<>(content, page, size, total, totalPages);
    }

    @Override
    public PageResult<Employee> findPickerPage(String tenantId, Boolean active, Set<String> departmentIds,
                                               int page, int size) {
        // Validates page/size exactly like findAll (PageRequest.of throws for size < 1 / page < 0).
        PageRequest pageable = PageRequest.of(page, size);
        if (departmentIds != null && departmentIds.isEmpty()) {
            // Fail-closed: no department → no row. Never reaches a query (an empty IN () is invalid SQL and
            // must not degrade to «no filter»).
            return new PageResult<>(List.of(), page, size, 0L, 0);
        }
        MasterStatus status = MasterStatusFilters.toStatus(active);
        List<Employee> content;
        long total;
        if (departmentIds == null) {
            content = jpa.findPickerInTenant(tenantId, status, pageable);
            total = jpa.countPickerInTenant(tenantId, status);
        } else {
            content = jpa.findPickerInDepartments(tenantId, status, departmentIds, pageable);
            total = jpa.countPickerInDepartments(tenantId, status, departmentIds);
        }
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResult<>(content, page, size, total, totalPages);
    }

    @Override
    public List<Employee> findActiveByDepartmentId(String departmentId, String tenantId) {
        return jpa.findAllByDepartmentIdAndTenantIdAndStatus(departmentId, tenantId, MasterStatus.ACTIVE);
    }

    @Override
    public List<Employee> findActiveByCostCenterId(String costCenterId, String tenantId) {
        return jpa.findAllByCostCenterIdAndTenantIdAndStatus(costCenterId, tenantId, MasterStatus.ACTIVE);
    }

    @Override
    public List<Employee> findActiveByJobGradeId(String jobGradeId, String tenantId) {
        return jpa.findAllByJobGradeIdAndTenantIdAndStatus(jobGradeId, tenantId, MasterStatus.ACTIVE);
    }
}
