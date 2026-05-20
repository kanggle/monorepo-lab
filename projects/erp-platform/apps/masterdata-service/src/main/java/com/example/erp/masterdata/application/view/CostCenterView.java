package com.example.erp.masterdata.application.view;

import com.example.erp.masterdata.domain.costcenter.CostCenter;

import java.time.Instant;
import java.time.LocalDate;

public record CostCenterView(String id, String code, String name, String departmentId,
                              String status, LocalDate effectiveFrom, LocalDate effectiveTo,
                              Instant createdAt, Instant updatedAt) {

    public static CostCenterView from(CostCenter c) {
        return new CostCenterView(c.getId(), c.getCode(), c.getName(), c.getDepartmentId(),
                c.getStatus().name(), c.getEffectiveFrom(), c.getEffectiveTo(),
                c.getCreatedAt(), c.getUpdatedAt());
    }
}
