package com.wms.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.gateway.error.GatewayErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
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

    private ServerAuthenticationEntryPoint unauthorizedEntryPoint(GatewayErrorHandler errorHandler) {
        return (exchange, ex) -> errorHandler.write(
                exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
    }

    private ServerAccessDeniedHandler forbiddenHandler(GatewayErrorHandler errorHandler) {
        return (exchange, ex) -> errorHandler.write(
                exchange, HttpStatus.FORBIDDEN, "FORBIDDEN", "Insufficient privileges for this operation");
    }
}
