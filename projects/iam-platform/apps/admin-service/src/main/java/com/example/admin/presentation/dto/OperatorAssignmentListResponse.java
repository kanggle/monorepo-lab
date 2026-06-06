package com.example.admin.presentation.dto;

import java.util.List;

/**
 * TASK-BE-339 — wire shape of
 * {@code GET /api/admin/operators/{operatorId}/assignments}: the operator's
 * assignment row(s) scoped to the active tenant ({@code X-Tenant-Id}). The list
 * holds 0 or 1 element (per (operator, active-tenant)); an operator not assigned
 * to the active tenant yields an empty list.
 */
public record OperatorAssignmentListResponse(List<OperatorAssignmentResponse> assignments) {}
