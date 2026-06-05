package com.example.erp.readmodel.application.query;

import com.example.erp.readmodel.domain.approval.ApprovalFactView;

import java.util.List;

/**
 * Result of the paginated approval-fact list: the assembled views for the
 * requested page plus the total element count (for {@code meta.totalElements}).
 */
public record ApprovalFactPage(List<ApprovalFactView> content, int page, int size,
                               long totalElements) {
}
