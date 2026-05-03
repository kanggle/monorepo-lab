package com.example.fanplatform.gateway.config;

import com.example.fanplatform.gateway.error.GatewayErrorHandler;
import com.example.fanplatform.gateway.security.TenantClaimValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info"
    };

    @Bean
    GatewayErrorHandler gatewayErrorHandler(ObjectMapper objectMapper) {
        return new GatewayErrorHandler(objectMapper);
    }

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                  GatewayErrorHandler errorHandler) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .authorizeExchange(auth -> auth
                        .pathMatchers(PUBLIC_PATHS).permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> {})
                        .authenticationEntryPoint(unauthorizedEntryPoint(errorHandler))
                        .accessDeniedHandler(forbiddenHandler(errorHandler)));
        return http.build();
    }

    /**
     * Distinguishes cross-tenant token misuse from generic authentication failures.
     * {@link TenantClaimValidator} attaches the {@code tenant_mismatch} error code;
     * we surface that as 403 {@code TENANT_FORBIDDEN} instead of the default 401.
     */
    private ServerAuthenticationEntryPoint unauthorizedEntryPoint(GatewayErrorHandler errorHandler) {
        return (exchange, ex) -> {
            OAuth2Error oauthError = extractOAuth2Error(ex);
            if (oauthError != null
                    && TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH.equals(oauthError.getErrorCode())) {
                String message = oauthError.getDescription() != null
                        ? oauthError.getDescription()
                        : "Cross-tenant access denied";
                return errorHandler.write(exchange, HttpStatus.FORBIDDEN,
                        "TENANT_FORBIDDEN", message);
            }
            return errorHandler.write(exchange, HttpStatus.UNAUTHORIZED,
                    "UNAUTHORIZED", "Authentication required");
        };
    }

    private ServerAccessDeniedHandler forbiddenHandler(GatewayErrorHandler errorHandler) {
        return (exchange, ex) -> errorHandler.write(
                exchange, HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient privileges for this operation");
    }

    private static OAuth2Error extractOAuth2Error(Throwable t) {
        Throwable cur = t;
        OAuth2Error fallback = null;
        while (cur != null) {
            if (cur instanceof JwtValidationException jve) {
                for (OAuth2Error err : jve.getErrors()) {
                    if (err != null && err.getErrorCode() != null
                            && !"invalid_token".equals(err.getErrorCode())) {
                        return err;
                    }
                }
            }
            if (cur instanceof InvalidBearerTokenException ibte) {
                OAuth2Error err = ibte.getError();
                if (err != null) fallback = err;
            }
            cur = cur.getCause();
        }
        return fallback;
    }
}
