package com.example.account.presentation.dto.response;

import com.example.account.application.result.SiteRoleMutationResult;

import java.util.List;

/** TASK-MONO-752 — response of the site-role grant/revoke endpoints (consumer-site-roles.md). */
public record SiteRoleMutationResponse(
        String accountId,
        String tenantId,
        List<String> roles,
        boolean changed
) {
    public static SiteRoleMutationResponse from(SiteRoleMutationResult result) {
        return new SiteRoleMutationResponse(result.accountId(), result.tenantId(), result.roles(), result.changed());
    }
}
