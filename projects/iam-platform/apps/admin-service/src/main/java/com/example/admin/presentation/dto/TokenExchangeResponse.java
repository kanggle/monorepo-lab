package com.example.admin.presentation.dto;

/**
 * Success response for {@code POST /api/admin/auth/token-exchange}
 * (TASK-BE-298 / ADR-MONO-014). Carries the operator access token minted by
 * the <b>same</b> issuer as {@code POST /api/admin/auth/login}
 * ({@code token_type=admin}, {@code iss=admin-service}). No refresh token is
 * issued — the console re-exchanges with its GAP-rotated token (ADR-MONO-014
 * D2 re-exchange model).
 *
 * <p>Shape per admin-api.md §POST /api/admin/auth/token-exchange:
 * {@code { accessToken, expiresIn, tokenType }}.
 */
public record TokenExchangeResponse(
        String accessToken,
        long expiresIn,
        String tokenType
) {}
