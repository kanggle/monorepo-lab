package com.example.admin.presentation.dto;

import java.util.List;

/**
 * TASK-MONO-772 S2 — body of {@code POST /api/admin/operator-invitations} (admin-api.md § Operator Invitation).
 *
 * <p>No Bean Validation annotations on purpose: {@code @Valid} runs during argument resolution, i.e. BEFORE the
 * {@code operator.manage} aspect and the reason check, which would answer a caller without the permission with
 * a 400 about its body. The use case validates these fields in the contract's order instead.
 */
public record CreateOperatorInvitationRequest(
        String email,
        String displayName,
        List<String> roles,
        String tenantId
) {}
