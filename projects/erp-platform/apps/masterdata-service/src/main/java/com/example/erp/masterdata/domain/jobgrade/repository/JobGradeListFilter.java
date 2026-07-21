package com.example.erp.masterdata.domain.jobgrade.repository;

import java.time.LocalDate;

/**
 * Optional list-query filter for JobGrade (masterdata-api.md § GET /job-grades —
 * {@code ?asOf=&active=}). Every field nullable; {@code null} = do not constrain
 * on that dimension.
 *
 * <p>Pure Java (domain boundary rule).
 */
public record JobGradeListFilter(LocalDate asOf, Boolean active) {

    public static JobGradeListFilter unfiltered() {
        return new JobGradeListFilter(null, null);
    }
}
