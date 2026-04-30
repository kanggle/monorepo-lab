package com.example.admin.presentation.dto;

import java.util.List;

/**
 * Response body for {@code PATCH /api/admin/operators/{operatorId}/roles}.
 */
public record PatchOperatorRolesResponse(
        String operatorId,
        List<String> roles,
        String auditId
) {}
