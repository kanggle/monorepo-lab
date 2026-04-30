package com.example.admin.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for {@code POST /api/admin/operators}. Matches
 * {@code specs/contracts/http/admin-api.md §POST /api/admin/operators} —
 * password policy is "≥10 chars, at least one letter, one digit, one special
 * character" enforced via the regex below so invalid shapes surface as
 * {@code 400 VALIDATION_ERROR}.
 */
public record CreateOperatorRequest(
        @NotBlank
        @Email
        String email,

        @NotBlank
        @Size(min = 1, max = 64)
        String displayName,

        @NotBlank
        @Size(min = 10, max = 255)
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
                message = "password must contain letters, digits, and a special character"
        )
        String password,

        List<String> roles
) {}
