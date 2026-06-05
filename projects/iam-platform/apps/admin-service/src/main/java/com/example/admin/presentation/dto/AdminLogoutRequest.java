package com.example.admin.presentation.dto;

/**
 * Request body for {@code POST /api/admin/auth/logout} (TASK-BE-040).
 * {@code refreshToken} is optional — if supplied, its jti is also revoked
 * with reason=LOGOUT alongside the access jti blacklist write.
 */
public record AdminLogoutRequest(String refreshToken) {}
