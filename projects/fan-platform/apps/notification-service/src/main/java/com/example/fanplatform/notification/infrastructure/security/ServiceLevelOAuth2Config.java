package com.example.fanplatform.notification.infrastructure.security;

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
 * Builds the tenant-pinned end-user {@code JwtDecoder} for the inbox surface.
 * Defense-in-depth: a gateway-bypassing call still gets the same validator chain
 * ({@code AllowedIssuersValidator} + {@code TenantClaimValidator}).
 *
 * <p>The decoder is a named bean (NOT {@code @Primary}) wired explicitly into the
 * {@code SecurityFilterChain} so Spring Boot's default single-{@code JwtDecoder}
 * auto-configuration does not collide.
 */
@Configuration
public class ServiceLevelOAuth2Config {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String endUserJwkSetUri;

    @Value("${fanplatform.oauth2.allowed-issuers}")
    private String allowedIssuersCsv;

    @Value("${fanplatform.oauth2.required-tenant-id:fan-platform}")
    private String requiredTenantId;

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
