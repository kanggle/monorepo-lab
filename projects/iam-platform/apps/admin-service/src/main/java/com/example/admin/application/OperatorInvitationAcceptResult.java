package com.example.admin.application;

import java.util.List;

/**
 * TASK-MONO-772 S3 — the answer of an acceptance (auth-to-admin.md § accept, 200). {@code alreadyAccepted = true}
 * is the same account resubmitting: the first result, nothing written again.
 */
public record OperatorInvitationAcceptResult(
        String operatorId,
        String tenantId,
        List<String> roles,
        boolean alreadyAccepted
) {
}
