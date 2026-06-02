package com.example.erp.masterdata.application.view;

import com.example.erp.masterdata.domain.department.Department;

public record DepartmentView(String id, String code, String name, String parentId,
                             String status, EffectivePeriodDto effectivePeriod,
                             AuditDto audit) {

    public static DepartmentView from(Department d) {
        return new DepartmentView(d.getId(), d.getCode(), d.getName(), d.getParentId(),
                d.getStatus().name(),
                new EffectivePeriodDto(d.getEffectiveFrom(), d.getEffectiveTo()),
                new AuditDto(d.getCreatedAt(), d.getUpdatedAt()));
    }
}
