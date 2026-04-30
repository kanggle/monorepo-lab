package com.example.admin.presentation.dto;

import java.time.Instant;
import java.util.List;

/**
 * Response body for {@code POST /api/admin/operators} (201 Created).
 * Matches {@code specs/contracts/http/admin-api.md §POST /api/admin/operators}.
 */
public record CreateOperatorResponse(
        String operatorId,
        String email,
        String displayName,
        String status,
        List<String> roles,
        boolean totpEnrolled,
        Instant createdAt,
        String auditId
) {}
