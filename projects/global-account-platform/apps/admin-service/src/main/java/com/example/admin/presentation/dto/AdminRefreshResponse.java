package com.example.admin.presentation.dto;

/**
 * Success response for {@code POST /api/admin/auth/refresh} (TASK-BE-040).
 * Mirrors the login response with the rotated refresh token included.
 */
public record AdminRefreshResponse(
        String accessToken,
        long expiresIn,
        String refreshToken,
        long refreshExpiresIn
) {}
