package com.example.admin.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/admin/auth/login}.
 *
 * <p>{@code totpCode} and {@code recoveryCode} are mutually exclusive — exactly
 * one must be provided when the operator's role set demands 2FA and the
 * operator has completed enrollment. Callers with {@code require_2fa=FALSE}
 * omit both. The controller enforces this mutual exclusion (BAD_REQUEST).
 */
public record AdminLoginRequest(
        @NotBlank String operatorId,
        @NotBlank String password,
        String totpCode,
        String recoveryCode
) {}
