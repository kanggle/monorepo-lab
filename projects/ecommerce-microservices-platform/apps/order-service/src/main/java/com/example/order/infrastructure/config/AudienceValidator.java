package com.example.order.infrastructure.config;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * Validates that a JWT's {@code aud} claim contains the configured expected audience
 * (TASK-BE-412). Part of the {@code /api/internal/**} resource-server validation chain
 * so a token minted for a different audience is rejected (fail-closed → 401).
 *
 * <p>When no expected audience is configured (blank), the check passes — so the
 * audience pin can be enabled per environment via the
 * {@code order.internal.oauth2.audience} property without breaking dev/standalone runs.
 */
public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_AUDIENCE = new OAuth2Error(
            "invalid_token", "The required audience is missing", null);

    private final String expectedAudience;

    public AudienceValidator(String expectedAudience) {
        this.expectedAudience = expectedAudience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (expectedAudience == null || expectedAudience.isBlank()) {
            return OAuth2TokenValidatorResult.success();
        }
        List<String> audiences = token.getAudience();
        if (audiences != null && audiences.contains(expectedAudience)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE);
    }
}
