package com.example.admin.presentation.dto;

/**
 * Response body for {@code PATCH /api/admin/operators/{operatorId}/status}.
 */
public record PatchOperatorStatusResponse(
        String operatorId,
        String previousStatus,
        String currentStatus,
        String auditId
) {}
