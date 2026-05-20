package com.example.erp.masterdata.application.view;

import com.example.erp.masterdata.domain.department.Department;

import java.time.Instant;
import java.time.LocalDate;

public record DepartmentView(String id, String code, String name, String parentId,
                              String status, LocalDate effectiveFrom, LocalDate effectiveTo,
                              Instant createdAt, Instant updatedAt) {

    public static DepartmentView from(Department d) {
        return new DepartmentView(d.getId(), d.getCode(), d.getName(), d.getParentId(),
                d.getStatus().name(), d.getEffectiveFrom(), d.getEffectiveTo(),
                d.getCreatedAt(), d.getUpdatedAt());
    }
}
