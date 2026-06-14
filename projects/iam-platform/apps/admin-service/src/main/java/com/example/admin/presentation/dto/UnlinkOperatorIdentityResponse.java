package com.example.admin.presentation.dto;

/**
 * TASK-BE-373 / ADR-MONO-034 U3 — response for
 * {@code PATCH /api/admin/operators/{operatorId}/identity:unlink}.
 *
 * <p>{@code previousIdentityId} is the identity the operator was linked to before
 * the unlink ({@code null} when it was already unlinked). {@code alreadyUnlinked}
 * is {@code true} for the idempotent no-op case.
 */
public record UnlinkOperatorIdentityResponse(
        String operatorId,
        String previousIdentityId,
        boolean alreadyUnlinked
) {}
