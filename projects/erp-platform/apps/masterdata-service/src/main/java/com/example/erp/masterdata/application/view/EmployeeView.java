package com.example.erp.masterdata.application.view;

import com.example.erp.masterdata.domain.employee.Employee;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Employee list element / detail. {@code accountId} (TASK-ERP-BE-044) is ABSENT from the wire
 * when the employee is not linked — {@code NON_NULL} on that component only, so the other
 * fields keep their existing serialisation (e.g. {@code effectivePeriod.effectiveTo: null}).
 */
public record EmployeeView(String id, String employeeNumber, String name,
                           String departmentId, String costCenterId, String jobGradeId,
                           String status, EffectivePeriodDto effectivePeriod,
                           AuditDto audit,
                           @JsonInclude(JsonInclude.Include.NON_NULL) String accountId) {

    public static EmployeeView from(Employee e) {
        return new EmployeeView(e.getId(), e.getEmployeeNumber(), e.getName(),
                e.getDepartmentId(), e.getCostCenterId(), e.getJobGradeId(),
                e.getStatus().name(),
                new EffectivePeriodDto(e.getEffectiveFrom(), e.getEffectiveTo()),
                new AuditDto(e.getCreatedAt(), e.getUpdatedAt()),
                e.getAccountId());
    }
}
