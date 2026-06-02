package com.example.erp.masterdata.application.view;

import com.example.erp.masterdata.domain.jobgrade.JobGrade;

public record JobGradeView(String id, String code, String name, Integer displayOrder,
                           String status, EffectivePeriodDto effectivePeriod,
                           AuditDto audit) {

    public static JobGradeView from(JobGrade g) {
        return new JobGradeView(g.getId(), g.getCode(), g.getName(), g.getDisplayOrder(),
                g.getStatus().name(),
                new EffectivePeriodDto(g.getEffectiveFrom(), g.getEffectiveTo()),
                new AuditDto(g.getCreatedAt(), g.getUpdatedAt()));
    }
}
