package com.example.admin.presentation.dto;

/**
 * TASK-BE-373 / ADR-MONO-034 U3 — response for
 * {@code PATCH /api/admin/operators/{operatorId}/identity:link}.
 *
 * <p>{@code alreadyLinked} is {@code true} when the operator was already linked to
 * the SAME identity (idempotent no-op success).
 */
public record LinkOperatorIdentityResponse(
        String operatorId,
        String identityId,
        boolean alreadyLinked
) {}
