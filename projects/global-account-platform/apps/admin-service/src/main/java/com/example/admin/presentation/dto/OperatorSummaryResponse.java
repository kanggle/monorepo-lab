package com.example.admin.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Shared response shape for operator lookups ({@code GET /api/admin/me}, the
 * content items of {@code GET /api/admin/operators}, and the body of
 * {@code POST /api/admin/operators}). Matches
 * {@code specs/contracts/http/admin-api.md §GET /api/admin/me} +
 * {@code §GET /api/admin/operators}.
 *
 * <p>Never includes {@code password_hash} or {@code totp_secret_encrypted} per
 * the spec's Security Constraints.
 *
 * <p>{@code operatorContext} (TASK-BE-308) carries the operator's profile
 * carrier shape (currently only {@code defaultAccountId}). Field-level
 * {@code @JsonInclude(Include.NON_NULL)} discipline: when the operator's
 * {@code finance_default_account_id} is NULL, the entire {@code operatorContext}
 * key is omitted from the JSON body — never literal {@code "operatorContext":null}.
 * Same wire shape as the sibling registry response item and the
 * {@code me/profile} + {@code {operatorId}/profile} request bodies (admin-api.md
 * § "carrier shape 대칭성").
 */
public record OperatorSummaryResponse(
        String operatorId,
        String email,
        String displayName,
        String status,
        List<String> roles,
        boolean totpEnrolled,
        Instant lastLoginAt,
        Instant createdAt,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        OperatorContextResponse operatorContext
) {

    /**
     * TASK-BE-308: wire DTO for the operator's profile carrier on
     * {@code GET /api/admin/operators}. Same shape as the registry surface's
     * {@code ProductOperatorContextResponse} (the carrier is identical across
     * registry response items, list response items, and write request bodies);
     * a dedicated DTO here keeps the presentation layer's import direction
     * controller→dto (no presentation-to-presentation coupling between the
     * operators surface and the registry surface).
     *
     * <p>v1 carries only {@code defaultAccountId}; future per-operator profile
     * attributes nest here.
     */
    public record OperatorContextResponse(String defaultAccountId) {}
}
