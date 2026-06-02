package com.example.erp.masterdata.application.view;

import com.example.erp.masterdata.domain.costcenter.CostCenter;

public record CostCenterView(String id, String code, String name, String departmentId,
                             String status, EffectivePeriodDto effectivePeriod,
                             AuditDto audit) {

    public static CostCenterView from(CostCenter c) {
        return new CostCenterView(c.getId(), c.getCode(), c.getName(), c.getDepartmentId(),
                c.getStatus().name(),
                new EffectivePeriodDto(c.getEffectiveFrom(), c.getEffectiveTo()),
                new AuditDto(c.getCreatedAt(), c.getUpdatedAt()));
    }
}
