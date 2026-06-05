package com.example.erp.readmodel.application.query;

import com.example.erp.readmodel.domain.delegation.DelegationFactProjection;

import java.util.List;

/**
 * Result of the paginated delegation-fact list (TASK-ERP-BE-015): the projected
 * facts for the requested page plus the total element count (for
 * {@code meta.totalElements}).
 */
public record DelegationFactPage(List<DelegationFactProjection> content, int page, int size,
                                 long totalElements) {
}
