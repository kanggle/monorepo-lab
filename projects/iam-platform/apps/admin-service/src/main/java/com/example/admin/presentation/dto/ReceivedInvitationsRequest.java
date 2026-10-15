package com.example.admin.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * TASK-MONO-773 OD-7 — body of {@code POST /api/admin/onboarding/received-invitations}: the caller's own IAM OIDC
 * access token, in the body (never the query string — access logs), exactly like the onboarding request.
 */
public record ReceivedInvitationsRequest(
        @NotBlank(message = "subjectToken is required")
        String subjectToken
) {
}
