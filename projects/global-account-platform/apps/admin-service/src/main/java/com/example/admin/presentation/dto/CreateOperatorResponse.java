package com.example.admin.presentation.dto;

import java.time.Instant;
import java.util.List;

/**
 * Response body for {@code POST /api/admin/operators} (201 Created).
 * Matches {@code specs/contracts/http/admin-api.md §POST /api/admin/operators}.
 *
 * <p>TASK-BE-249: {@code tenantId} added to reflect the tenant the new operator belongs to.
 */
public record CreateOperatorResponse(
        String operatorId,
        String email,
        String displayName,
        String status,
        List<String> roles,
        boolean totpEnrolled,
        Instant createdAt,
        String auditId,
        String tenantId
) {
    /** Backward-compat 8-arg constructor for test fixtures predating TASK-BE-249. */
    public CreateOperatorResponse(String operatorId, String email, String displayName,
                                  String status, List<String> roles, boolean totpEnrolled,
                                  Instant createdAt, String auditId) {
        this(operatorId, email, displayName, status, roles, totpEnrolled,
                createdAt, auditId, "fan-platform");
    }
}
