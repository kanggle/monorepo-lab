package com.example.fanplatform.membership.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the two JWT decoders used by membership-service:
 *
 * <ul>
 *   <li><b>endUserJwtDecoder</b> — the tenant-pinned end-user decoder
 *       ({@code AllowedIssuersValidator} + {@code TenantClaimValidator}) for
 *       {@code /api/fan/**}. Defense-in-depth: a gateway-bypassing call still
 *       gets the same validator chain.</li>
 *   <li><b>internalJwtDecoder</b> — the workload-identity decoder for
 *       {@code /internal/**}. Validates an IAM {@code client_credentials} JWT
 *       (issuer + signature + timestamps); {@code tenant_id} is intentionally NOT
 *       pinned (client_credentials tokens carry no tenant claim). Built from the
 *       JWKS URI directly so startup is not coupled to auth-service availability
 *       (JWKS fetched lazily on first verification). ADR-MONO-005.</li>
 * </ul>
 *
 * <p>The two are named beans (NOT {@code @Primary}) — each {@code SecurityFilterChain}
 * in {@link SecurityConfig} wires its decoder explicitly so Spring Boot's single
 * default {@code JwtDecoder} auto-configuration does not collide.
 */
@Configuration
public class ServiceLevelOAuth2Config {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String endUserJwkSetUri;

    @Value("${fanplatform.oauth2.allowed-issuers}")
    private String allowedIssuersCsv;

    @Value("${fanplatform.oauth2.required-tenant-id:fan-platform}")
    private String requiredTenantId;

    @Value("${fanplatform.internal.jwt.jwk-set-uri}")
    private String internalJwkSetUri;

    @Value("${fanplatform.internal.jwt.issuer}")
    private String internalIssuer;

    @Bean
    public NimbusJwtDecoder endUserJwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(endUserJwkSetUri).build();
        decoder.setJwtValidator(endUserTokenValidator());
        return decoder;
    }

    @Bean
    public OAuth2TokenValidator<Jwt> endUserTokenValidator() {
        List<String> allowedIssuers = parseCsv(allowedIssuersCsv);
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator());
        validators.add(new AllowedIssuersValidator(allowedIssuers));
        validators.add(new TenantClaimValidator(requiredTenantId));
        validators.add(JwtValidators.createDefault());
        return new DelegatingOAuth2TokenValidator<>(validators);
    }

    /**
     * Workload-identity decoder for {@code /internal/**}. Pins the IAM issuer +
     * default timestamp checks; does NOT pin tenant_id.
     */
    @Bean
    public NimbusJwtDecoder internalJwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(internalJwkSetUri).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(internalIssuer));
        return decoder;
    }

    private static List<String> parseCsv(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null) return out;
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }
}
