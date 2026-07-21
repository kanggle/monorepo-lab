package com.example.erp.masterdata.domain.department.repository;

import java.time.LocalDate;

/**
 * Optional list-query filter for Department (masterdata-api.md § GET
 * /departments — {@code ?asOf=&active=&parentId=}). Every field is nullable —
 * a {@code null} field means "do not constrain on this dimension".
 *
 * <ul>
 *   <li>{@code asOf} — point-in-time: keep only revisions whose
 *       {@code [effectiveFrom, effectiveTo)} contains {@code asOf} (E2).</li>
 *   <li>{@code active} — lifecycle: {@code true} = ACTIVE only,
 *       {@code false} = RETIRED only.</li>
 *   <li>{@code parentId} — hierarchy: children of this parent.</li>
 * </ul>
 *
 * <p>Pure Java (domain boundary rule).
 */
public record DepartmentListFilter(LocalDate asOf, Boolean active, String parentId) {

    public static DepartmentListFilter unfiltered() {
        return new DepartmentListFilter(null, null, null);
    }
}
