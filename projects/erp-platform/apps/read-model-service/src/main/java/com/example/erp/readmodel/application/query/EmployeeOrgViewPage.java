package com.example.erp.readmodel.application.query;

import com.example.erp.readmodel.domain.orgview.EmployeeOrgView;

import java.util.List;

/**
 * Result of the paginated employee org-view list: the assembled views for the
 * requested page plus the total element count (for {@code meta.totalElements}).
 */
public record EmployeeOrgViewPage(List<EmployeeOrgView> content, int page, int size,
                                  long totalElements) {
}
