package com.example.admin.presentation.dto;

import java.util.List;

/** TASK-MONO-772 S2 — {@code GET /api/admin/operator-invitations} page (admin-api.md). */
public record OperatorInvitationListResponse(
        List<OperatorInvitationResponse> content,
        long totalElements,
        int page,
        int size,
        int totalPages
) {}
