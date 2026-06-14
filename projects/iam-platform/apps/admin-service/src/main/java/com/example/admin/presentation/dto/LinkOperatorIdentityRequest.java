package com.example.admin.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * TASK-BE-373 / ADR-MONO-034 U3 — request body for
 * {@code PATCH /api/admin/operators/{operatorId}/identity:link}.
 *
 * <p>{@code accountId} is the consumer account whose central identity is linked;
 * {@code tenantId} is the tenant the account lives in (scopes the step-3b resolve
 * EP). Both are required — the link is explicit and targets a specific account.
 */
public record LinkOperatorIdentityRequest(
        @NotBlank(message = "accountId is required") String accountId,
        @NotBlank(message = "tenantId is required") String tenantId
) {}
