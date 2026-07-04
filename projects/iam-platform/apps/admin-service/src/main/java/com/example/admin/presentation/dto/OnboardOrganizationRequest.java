package com.example.admin.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * TASK-BE-474 (ADR-MONO-044) — request body for self-service B2B tenant onboarding.
 *
 * <p>{@code subjectToken} is the caller's IAM OIDC access token ({@code platform-console-web}
 * audience) carried in the BODY (token-exchange style, ADR-014) — admin-service has no
 * user-JWT header authentication surface, so the endpoint is {@code permitAll} and validates
 * the token itself via {@code IamOidcSubjectTokenValidator}. The caller's email/display name
 * are resolved from the AUTHORITATIVE account (by the token's {@code sub}), never from this body.
 */
public record OnboardOrganizationRequest(

        @NotBlank(message = "subjectToken is required")
        String subjectToken,

        /** The new tenant slug. Same shape account-service enforces (CreateTenantUseCase). */
        @NotBlank(message = "tenantId is required")
        @Pattern(regexp = "^[a-z][a-z0-9-]{1,31}$",
                message = "tenantId must be 2-32 chars, lowercase alnum/hyphen, starting with a letter")
        String tenantId,

        @NotBlank(message = "organizationName is required")
        @Size(max = 100, message = "organizationName must not exceed 100 characters")
        String organizationName
) {
}
