package com.example.admin.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/admin/auth/token-exchange}
 * (TASK-BE-298 / ADR-MONO-014, RFC 8693 token exchange).
 *
 * <p>RFC 8693 uses snake_case form/JSON parameter names; mapped explicitly via
 * {@link JsonProperty}. Only {@code subject_token} is bean-validated for
 * presence ({@code 400 VALIDATION_ERROR} on absence); the fixed
 * {@code grant_type} / {@code subject_token_type} values are validated in the
 * controller and yield {@code 400 BAD_REQUEST} on mismatch (admin-api.md
 * error table).
 */
public record TokenExchangeRequest(

        @JsonProperty("grant_type")
        String grantType,

        @NotBlank
        @JsonProperty("subject_token")
        String subjectToken,

        @JsonProperty("subject_token_type")
        String subjectTokenType
) {
    /** RFC 8693 fixed grant type for token exchange. */
    public static final String GRANT_TYPE = "urn:ietf:params:oauth:grant-type:token-exchange";
    /** RFC 8693 token-type URI for an OAuth 2.0 access token subject token. */
    public static final String SUBJECT_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token";
}
