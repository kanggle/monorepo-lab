package com.example.account.application.result;

import java.util.List;

/**
 * TASK-MONO-752 — outcome of a site-role grant/revoke (consumer-site-roles.md). {@code roles} is the account's
 * complete {@code consumer_site_roles} on {@code tenantId} (the site) after the call; {@code changed} is
 * {@code false} for an idempotent no-op.
 */
public record SiteRoleMutationResult(
        String accountId,
        String tenantId,
        List<String> roles,
        boolean changed
) {
}
