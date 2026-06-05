package com.example.admin.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for {@code POST /api/admin/auth/refresh} (TASK-BE-040). */
public record AdminRefreshRequest(@NotBlank String refreshToken) {}
