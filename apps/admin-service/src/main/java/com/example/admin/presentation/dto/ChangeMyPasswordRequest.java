package com.example.admin.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PATCH /api/admin/operators/me/password}.
 * Structural validation only; policy enforcement (3-of-4 character categories)
 * is delegated to {@code OperatorAdminUseCase.changeMyPassword}.
 */
public record ChangeMyPasswordRequest(
        @NotBlank
        String currentPassword,

        @NotBlank
        @Size(min = 8, max = 128)
        String newPassword
) {}
