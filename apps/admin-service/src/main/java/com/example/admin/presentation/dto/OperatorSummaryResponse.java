package com.example.admin.presentation.dto;

import java.time.Instant;
import java.util.List;

/**
 * Shared response shape for operator lookups ({@code GET /api/admin/me}, the
 * content items of {@code GET /api/admin/operators}, and the body of
 * {@code POST /api/admin/operators}). Matches
 * {@code specs/contracts/http/admin-api.md §GET /api/admin/me}.
 *
 * <p>Never includes {@code password_hash} or {@code totp_secret_encrypted} per
 * the spec's Security Constraints.
 */
public record OperatorSummaryResponse(
        String operatorId,
        String email,
        String displayName,
        String status,
        List<String> roles,
        boolean totpEnrolled,
        Instant lastLoginAt,
        Instant createdAt
) {}
