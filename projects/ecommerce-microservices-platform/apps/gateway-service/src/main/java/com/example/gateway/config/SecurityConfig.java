package com.example.gateway.config;

import com.example.web.dto.ErrorResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/api/auth/**",
            "/api/search/**",
            "/oauth/**"
    };

    /**
     * Carrier webhook public endpoint (ADR-007 D5-2 / TASK-BE-359).
     * Exact method + path match — only POST to this path is exempt from JWT auth.
     * Authentication is delegated entirely to the downstream shipping-service HMAC
     * verifier (CarrierWebhookVerifier, TASK-BE-294, fail-closed/net-zero).
     * No other /api/shippings/** path is opened by this rule.
     */
    private static final String CARRIER_WEBHOOK_PATH = "/api/shippings/carrier-webhook";

    @Bean
    SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            ObjectMapper objectMapper,
            GatewayMetrics gatewayMetrics) {

        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .authorizeExchange(auth -> auth
                        // Actuator / auth / search / oauth — fully public
                        .pathMatchers(PUBLIC_PATHS).permitAll()
                        // Product read (GET) is public; writes require auth
                        .pathMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                        // Reviews — read-only paths are public
                        .pathMatchers(HttpMethod.GET, "/api/reviews/products/**").permitAll()
                        // Carrier inbound webhook — public (HMAC-authenticated downstream, ADR-007 D5-2).
                        // EXACT method+path: POST only; every other /api/shippings/** stays JWT-protected.
                        .pathMatchers(HttpMethod.POST, CARRIER_WEBHOOK_PATH).permitAll()
                        // Everything else requires authentication
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> {})
                        .authenticationEntryPoint(unauthorizedEntryPoint(objectMapper, gatewayMetrics))
                        .accessDeniedHandler(forbiddenHandler(objectMapper)));

        return http.build();
    }

    private ServerAuthenticationEntryPoint unauthorizedEntryPoint(
            ObjectMapper objectMapper, GatewayMetrics gatewayMetrics) {
        return (exchange, ex) -> {
            log.debug("JWT authentication failed: {}", ex.getMessage());
            gatewayMetrics.incrementJwtValidationFailure("invalid");
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED,
                    ErrorResponse.of("UNAUTHORIZED", "Authentication required"), objectMapper);
        };
    }

    private ServerAccessDeniedHandler forbiddenHandler(ObjectMapper objectMapper) {
        return (exchange, ex) -> {
            log.debug("Access denied: {}", ex.getMessage());
            return writeErrorResponse(exchange, HttpStatus.FORBIDDEN,
                    ErrorResponse.of("FORBIDDEN", "Insufficient privileges for this operation"), objectMapper);
        };
    }

    private Mono<Void> writeErrorResponse(
            ServerWebExchange exchange,
            HttpStatus status,
            ErrorResponse body,
            ObjectMapper objectMapper) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            return response.setComplete();
        }
    }
}
