package com.example.erp.masterdata.infrastructure.persistence.jpa;

import com.example.erp.masterdata.domain.common.MasterStatus;
import com.example.erp.masterdata.domain.common.PageResult;
import com.example.erp.masterdata.domain.employee.Employee;
import com.example.erp.masterdata.domain.employee.repository.EmployeeListFilter;
import com.example.erp.masterdata.domain.employee.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

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
    public PageResult<Employee> findAll(String tenantId, EmployeeListFilter filter, int page, int size) {
        MasterStatus status = MasterStatusFilters.toStatus(filter.active());
        List<Employee> content = jpa.findFiltered(tenantId, status, filter.departmentId(),
                filter.costCenterId(), filter.asOf(), PageRequest.of(page, size));
        long total = jpa.countFiltered(tenantId, status, filter.departmentId(),
                filter.costCenterId(), filter.asOf());
        return new PageResult<>(content, total);
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
