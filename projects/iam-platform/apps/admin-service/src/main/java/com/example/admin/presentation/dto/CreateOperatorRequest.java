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
 *
 * <p>TASK-BE-249: {@code tenantId} added as a required field. The creating
 * operator cannot assign a platform-scope ({@code "*"}) tenantId unless they
 * themselves are platform-scope — that gate is enforced in
 * {@code CreateOperatorUseCase}.
 */
public record CreateOperatorRequest(
        @NotBlank
        @Email
        String email,

        @NotBlank
        @Size(min = 1, max = 64)
        String displayName,

        /**
         * TASK-BE-377 (ADR-MONO-035 O2 / step 4c): OPTIONAL. When omitted/blank the
         * operator is created OIDC-only (no local break-glass password — primary login
         * is the unified IAM OIDC credential via the ADR-014 token-exchange). When
         * supplied it is the break-glass local password and MUST satisfy the policy
         * (≥10 chars, ≥1 letter + ≥1 digit + ≥1 special). {@code @Size}/{@code @Pattern}
         * are skipped by Bean Validation on a null value, so an absent password is
         * accepted and a supplied one is still policy-enforced.
         */
        @Size(min = 10, max = 255)
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
                message = "password must contain letters, digits, and a special character"
        )
        String password,

        List<String> roles,

        /**
         * TASK-BE-249: the tenant this operator belongs to. Required.
         * Must be a valid tenant slug ({@code ^[a-z][a-z0-9-]{1,31}$}) or the
         * platform-scope sentinel {@code "*"} (SUPER_ADMIN only — validated in use-case).
         */
        @NotBlank
        @Size(min = 1, max = 32)
        String tenantId,

        /**
         * TASK-BE-374 (ADR-MONO-034 U4 / U6 step 3d): opt-in to REUSE an existing
         * central identity when one already exists for this (tenant, email), rather
         * than creating a fresh one. Nullable — a missing flag is treated as
         * {@code false} (no silent merge, ADR-034 U3): if an identity already exists
         * and this flag is absent/false, the operator is created UNLINKED (link
         * later via the explicit step-3c surface). Identity provisioning is fail-soft
         * for operator creation, so this never blocks the create.
         */
        Boolean reuseExistingIdentity
) {}
