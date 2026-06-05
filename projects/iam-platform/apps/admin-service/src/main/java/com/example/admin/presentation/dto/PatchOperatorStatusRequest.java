package com.example.admin.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for {@code PATCH /api/admin/operators/{operatorId}/status}.
 * Only {@code ACTIVE} and {@code SUSPENDED} are accepted at the HTTP layer;
 * other transitions are rejected with {@code 400 VALIDATION_ERROR}.
 */
public record PatchOperatorStatusRequest(
        @NotBlank
        @Pattern(regexp = "ACTIVE|SUSPENDED",
                message = "status must be ACTIVE or SUSPENDED")
        String status
) {}
