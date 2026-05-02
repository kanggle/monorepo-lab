package com.example.membership.infrastructure.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;

import java.util.List;
import java.util.Objects;

/**
 * Accepts tokens whose {@code iss} claim matches any of the configured allowed issuers.
 *
 * <p>TASK-BE-253 (D2-b deprecate window): GAP currently issues two flavours of access
 * token signed by the same JWKS:
 * <ul>
 *   <li>SAS-issued tokens — {@code iss = oidc.issuer-url}</li>
 *   <li>Legacy {@code POST /api/auth/login} tokens — {@code iss = "global-account-platform"}</li>
 * </ul>
 * After deprecation, drop the legacy entry and rely on Spring's standard
 * {@code JwtIssuerValidator}.
 */
public class AllowedIssuersValidator implements OAuth2TokenValidator<Jwt> {

    private final List<String> allowedIssuers;

    public AllowedIssuersValidator(List<String> allowedIssuers) {
        Objects.requireNonNull(allowedIssuers, "allowedIssuers");
        if (allowedIssuers.isEmpty()) {
            throw new IllegalArgumentException("allowedIssuers must not be empty");
        }
        this.allowedIssuers = List.copyOf(allowedIssuers);
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String iss = jwt.getClaimAsString(JwtClaimNames.ISS);
        if (iss == null || !allowedIssuers.contains(iss)) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_issuer",
                    "iss '" + iss + "' is not in the allowed list",
                    null));
        }
        return OAuth2TokenValidatorResult.success();
    }
}
