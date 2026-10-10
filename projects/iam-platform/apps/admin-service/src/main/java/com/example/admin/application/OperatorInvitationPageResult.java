package com.example.admin.application;

import java.util.List;

/** TASK-MONO-772 S2 — {@code GET /api/admin/operator-invitations} page ({@code createdAt} descending). */
public record OperatorInvitationPageResult(
        List<OperatorInvitationResult> content,
        long totalElements,
        int page,
        int size,
        int totalPages
) {}
