package com.example.erp.masterdata.application.view;

import com.example.erp.masterdata.domain.employee.Employee;

import java.time.Instant;
import java.time.LocalDate;

public record EmployeeView(String id, String employeeNumber, String name,
                            String departmentId, String costCenterId, String jobGradeId,
                            String status, LocalDate effectiveFrom, LocalDate effectiveTo,
                            Instant createdAt, Instant updatedAt) {

    public static EmployeeView from(Employee e) {
        return new EmployeeView(e.getId(), e.getEmployeeNumber(), e.getName(),
                e.getDepartmentId(), e.getCostCenterId(), e.getJobGradeId(),
                e.getStatus().name(), e.getEffectiveFrom(), e.getEffectiveTo(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
