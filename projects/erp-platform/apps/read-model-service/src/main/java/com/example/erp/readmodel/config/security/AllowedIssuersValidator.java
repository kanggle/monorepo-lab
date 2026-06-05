package com.example.erp.readmodel.config.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;

import java.util.List;
import java.util.Objects;

/**
 * Accepts tokens whose {@code iss} matches any configured allowed issuer
 * (SAS issuer + legacy {@code iam} D2-b window). Mirrors
 * masterdata-service's validator so a direct call to read-model-service gets
 * the same issuer verdict the (v1-deferred) gateway would apply.
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
