package com.wms.admin.infra.security;

import java.util.List;
import java.util.Objects;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;

/**
 * Accepts tokens whose {@code iss} claim matches any allowed issuer.
 * Mirrors the master-service pattern (TASK-MONO-019, D2-b deprecation
 * window — accepts SAS issuer + legacy {@code "global-account-platform"}).
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
