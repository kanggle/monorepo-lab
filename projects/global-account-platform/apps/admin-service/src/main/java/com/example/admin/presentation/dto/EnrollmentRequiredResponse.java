package com.example.admin.presentation.dto;

/**
 * 401 body returned by {@code POST /api/admin/auth/login} when the operator's
 * role set requires 2FA and no {@code admin_operator_totp} row exists. The
 * {@code bootstrapToken} authorises the {@code /api/admin/auth/2fa/enroll}
 * sub-tree (single use, 10-minute TTL).
 */
public record EnrollmentRequiredResponse(
        String code,
        String message,
        String bootstrapToken,
        long bootstrapExpiresIn) {}
