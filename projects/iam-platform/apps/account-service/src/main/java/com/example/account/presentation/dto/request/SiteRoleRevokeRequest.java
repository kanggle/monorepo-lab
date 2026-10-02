package com.example.account.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * TASK-MONO-752 — body of {@code PATCH /internal/tenants/{tenantId}/accounts/{accountId}/site-roles:revoke}
 * (consumer-site-roles.md).
 */
public record SiteRoleRevokeRequest(

        @NotBlank(message = "roleName must not be blank")
        @Size(max = 64, message = "roleName must be at most 64 characters")
        String roleName,

        @Size(max = 36, message = "operatorId must be at most 36 characters")
        String operatorId
) {
}
