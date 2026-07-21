package com.example.erp.masterdata.domain.employee.repository;

import java.time.LocalDate;

/**
 * Optional list-query filter for Employee (masterdata-api.md § GET /employees —
 * {@code ?asOf=&active=&departmentId=&costCenterId=}). Every field nullable;
 * {@code null} = do not constrain on that dimension.
 *
 * <p>Pure Java (domain boundary rule).
 */
public record EmployeeListFilter(LocalDate asOf, Boolean active,
                                 String departmentId, String costCenterId) {

    public static EmployeeListFilter unfiltered() {
        return new EmployeeListFilter(null, null, null, null);
    }
}
