package com.example.erp.approval.application.view;

import com.example.common.page.PageResult;

/**
 * Inbox page + the employee the caller acts as (approval-api.md § v2.4 — TASK-MONO-776).
 * {@code actorEmployeeId} is {@code null} when the caller's account is linked to no employee:
 * the controller then omits {@code meta.actorEmployeeId}, which is how the console tells
 * «your account is not linked, so this inbox is empty» from «nothing is waiting for you».
 */
public record ApprovalInboxView(PageResult<ApprovalSummaryView> page, String actorEmployeeId) {
}
