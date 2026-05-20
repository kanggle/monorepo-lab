package com.example.erp.masterdata.application.view;

import com.example.erp.masterdata.domain.jobgrade.JobGrade;

import java.time.Instant;
import java.time.LocalDate;

public record JobGradeView(String id, String code, String name, Integer displayOrder,
                            String status, LocalDate effectiveFrom, LocalDate effectiveTo,
                            Instant createdAt, Instant updatedAt) {

    public static JobGradeView from(JobGrade g) {
        return new JobGradeView(g.getId(), g.getCode(), g.getName(), g.getDisplayOrder(),
                g.getStatus().name(), g.getEffectiveFrom(), g.getEffectiveTo(),
                g.getCreatedAt(), g.getUpdatedAt());
    }
}
