package com.example.gateway.config;

import com.example.security.oauth2.TenantClaimValidator;
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
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
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
            "/api/search/**",
            "/oauth/**",
            // Aggregated Swagger UI (TASK-BE-379). Permit is always present but the
            // content is gated by gateway.swagger-aggregation.enabled (default false):
            // when OFF, springdoc is disabled and the /api-docs/<svc> proxy routes are
            // not registered (SwaggerAggregationConfig is @ConditionalOnProperty), so
            // these paths resolve to no handler / no route — nothing is exposed in prod.
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/webjars/**",
            "/api-docs/**"
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
                        // CORS preflight (OPTIONS) is unauthenticated by spec — a browser
                        // sends no Authorization header on the preflight. Permit it so it
                        // reaches Spring Cloud Gateway's globalcors CorsWebFilter, which
                        // answers with the Access-Control-Allow-* headers. Without this,
                        // anyExchange().authenticated() 401s the preflight before CORS runs,
                        // so every cross-origin authed write (e.g. POST /api/wishlists) fails
                        // in the browser with "TypeError: Failed to fetch" (TASK-BE-394).
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
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

    /**
     * Distinguishes cross-tenant token misuse from generic authentication failures.
     * {@link TenantClaimValidator} attaches the {@code tenant_mismatch} error code;
     * we surface that as 403 {@code TENANT_FORBIDDEN} instead of the default 401.
     * <p>
     * The distinction is not cosmetic: 401 tells a client "your token is stale, get a
     * new one", which for a cross-tenant token is a lie — re-issuing produces the same
     * rejection. 403 says "this token is valid but not for this edge", which is both
     * true and actionable, and it stops clients from looping on token refresh.
     * TASK-BE-501 — {@code TenantClaimValidator} has promised this mapping in its
     * javadoc since it was written, but the branch was never implemented here.
     */
    // Package-private, not private: this lambda IS the branch under test. A test that
    // reached it only through a booted context would need Docker (Redis) and would still
    // be testing Spring's wiring rather than the mapping decision itself.
    ServerAuthenticationEntryPoint unauthorizedEntryPoint(
            ObjectMapper objectMapper, GatewayMetrics gatewayMetrics) {
        return (exchange, ex) -> {
            OAuth2Error oauthError = extractOAuth2Error(ex);
            if (oauthError != null
                    && TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH.equals(oauthError.getErrorCode())) {
                log.debug("Cross-tenant token rejected: {}", oauthError.getDescription());
                gatewayMetrics.incrementJwtValidationFailure(GatewayMetrics.REASON_TENANT_MISMATCH);
                String message = oauthError.getDescription() != null
                        ? oauthError.getDescription()
                        : "Cross-tenant access denied";
                return writeErrorResponse(exchange, HttpStatus.FORBIDDEN,
                        ErrorResponse.of("TENANT_FORBIDDEN", message), objectMapper);
            }
            log.debug("JWT authentication failed: {}", ex.getMessage());
            gatewayMetrics.incrementJwtValidationFailure("invalid");
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED,
                    ErrorResponse.of("UNAUTHORIZED", "Authentication required"), objectMapper);
        };
    }

    /**
     * Digs the {@link OAuth2Error} out of the authentication exception. Spring wraps the
     * decode-time validator failure in an {@link OAuth2AuthenticationException}, but the
     * resource-server filter may in turn wrap that in a generic
     * {@link org.springframework.security.core.AuthenticationException}, so walk the
     * cause chain rather than testing only the top frame. Guards against a self-cause
     * loop.
     */
    private static OAuth2Error extractOAuth2Error(Throwable ex) {
        for (Throwable cur = ex; cur != null; cur = cur.getCause()) {
            if (cur instanceof OAuth2AuthenticationException oauthEx) {
                return oauthEx.getError();
            }
            if (cur == cur.getCause()) {
                break;
            }
        }
        return null;
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
