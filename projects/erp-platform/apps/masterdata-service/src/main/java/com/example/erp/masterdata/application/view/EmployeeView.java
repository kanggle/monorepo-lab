package com.example.erp.masterdata.application.view;

import com.example.erp.masterdata.domain.employee.Employee;

public record EmployeeView(String id, String employeeNumber, String name,
                           String departmentId, String costCenterId, String jobGradeId,
                           String status, EffectivePeriodDto effectivePeriod,
                           AuditDto audit) {

    public static EmployeeView from(Employee e) {
        return new EmployeeView(e.getId(), e.getEmployeeNumber(), e.getName(),
                e.getDepartmentId(), e.getCostCenterId(), e.getJobGradeId(),
                e.getStatus().name(),
                new EffectivePeriodDto(e.getEffectiveFrom(), e.getEffectiveTo()),
                new AuditDto(e.getCreatedAt(), e.getUpdatedAt()));
    }
}
