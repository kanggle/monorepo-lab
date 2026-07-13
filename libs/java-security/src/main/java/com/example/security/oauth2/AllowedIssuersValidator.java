package com.example.security.oauth2;

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
 *
 * <h2>Why it lives in {@code java-security} and not {@code java-gateway}</h2>
 *
 * It is <strong>framework-neutral</strong> — an {@link OAuth2TokenValidator}, nothing
 * reactive about it — and {@code TASK-MONO-377} counted <strong>eighteen more copies</strong>
 * of it inside servlet services across six projects, every one of them identical to this
 * file after normalisation. A servlet service cannot consume {@code libs/java-gateway}
 * without dragging WebFlux and Spring Cloud Gateway onto its runtime classpath
 * (<code>ADR-MONO-049</code> § D1; the bleed {@code TASK-MONO-044a} already paid for once),
 * so the class had to move to a module both edges can see. {@code ADR-MONO-049} § D5-1.
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
