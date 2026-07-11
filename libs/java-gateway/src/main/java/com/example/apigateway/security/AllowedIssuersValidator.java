package com.example.apigateway.security;

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
 * <p>D2-b deprecation window: IAM issues two flavours of access token signed by the
 * same JWKS — the SAS issuer URL (e.g. {@code http://iam.local}) and the legacy
 * {@code "iam"} string. Both must validate at every gateway edge while the legacy
 * path is being deprecated.
 *
 * <p>This class was byte-identical across all four gateways (wms / scm / fan /
 * ecommerce) — the only Tier-1 class that was 4/4. It is constructed directly by
 * each gateway's {@code OAuth2ResourceServerConfig} (never a bean), so adopting it
 * costs a consumer nothing but an import.
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
