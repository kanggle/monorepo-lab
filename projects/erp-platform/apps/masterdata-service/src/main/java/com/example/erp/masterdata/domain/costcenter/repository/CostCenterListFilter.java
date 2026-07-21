package com.example.erp.masterdata.domain.costcenter.repository;

import java.time.LocalDate;

/**
 * Optional list-query filter for CostCenter (masterdata-api.md § GET
 * /cost-centers — {@code ?asOf=&active=&departmentId=}). Every field nullable;
 * {@code null} = do not constrain on that dimension.
 *
 * <p>Pure Java (domain boundary rule).
 */
public record CostCenterListFilter(LocalDate asOf, Boolean active, String departmentId) {

    public static CostCenterListFilter unfiltered() {
        return new CostCenterListFilter(null, null, null);
    }
}
