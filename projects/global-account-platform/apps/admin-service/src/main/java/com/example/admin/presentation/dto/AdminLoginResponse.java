package com.example.admin.presentation.dto;

/**
 * Success response for {@code POST /api/admin/auth/login}. Mirrors the
 * {@code token_type=admin} operator JWT minted by {@link com.example.admin.infrastructure.security.JwtSigner}.
 */
public record AdminLoginResponse(
        String accessToken,
        long expiresIn,
        String refreshToken,
        long refreshExpiresIn
) {}
