package com.example.gateway.config;

import com.example.apigateway.security.GatewayErrorCodes;
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
import org.springframework.security.oauth2.jwt.JwtValidationException;
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
            OAuth2Error oauthError = findTenantMismatch(ex);
            if (oauthError != null) {
                log.debug("Cross-tenant token rejected: {}", oauthError.getDescription());
                gatewayMetrics.incrementJwtValidationFailure(GatewayMetrics.REASON_TENANT_MISMATCH);
                String message = oauthError.getDescription() != null
                        ? oauthError.getDescription()
                        : "Cross-tenant access denied";
                return writeErrorResponse(exchange, HttpStatus.FORBIDDEN,
                        ErrorResponse.of("TENANT_FORBIDDEN", message), objectMapper);
            }
            // TASK-MONO-696 — audience rejection (enforce mode only). Same chain-walk, same
            // reason: the token is valid, and a fresh one from the same client carries the same
            // aud, so "re-authenticate" (401) would be the wrong instruction.
            if (findInCauseChain(ex, GatewayErrorCodes.AUDIENCE_MISMATCH) != null) {
                log.debug("Token audience not admitted at this gateway");
                gatewayMetrics.incrementJwtValidationFailure(GatewayMetrics.REASON_AUDIENCE_MISMATCH);
                return writeErrorResponse(exchange, HttpStatus.FORBIDDEN,
                        ErrorResponse.of(GatewayErrorCodes.AUDIENCE_FORBIDDEN,
                                "This token's client is not admitted at this gateway"),
                        objectMapper);
            }
            log.debug("JWT authentication failed: {}", ex.getMessage());
            gatewayMetrics.incrementJwtValidationFailure("invalid");
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED,
                    ErrorResponse.of("UNAUTHORIZED", "Authentication required"), objectMapper);
        };
    }

    /**
     * <strong>Predicate: if {@code tenant_mismatch} appears anywhere in the cause chain — as an
     * {@link OAuth2AuthenticationException}'s error <em>or</em> inside a
     * {@link JwtValidationException}'s {@code getErrors()} — the rejection is 403.</strong>
     * Returns that error, or {@code null} when no frame carries it. Guards against a
     * self-cause loop.
     *
     * <p>Why not "the first {@link OAuth2Error} in the chain" (TASK-BE-501's rule): that frame
     * is never the validator's. When {@link TenantClaimValidator} fails, the decoder throws
     * {@link JwtValidationException} — which is <em>not</em> an OAuth2 exception — and
     * {@code JwtReactiveAuthenticationManager} wraps it in
     * {@link org.springframework.security.oauth2.server.resource.InvalidBearerTokenException},
     * whose own error code is always {@code invalid_token}. The first OAuth2 error found was
     * therefore always {@code invalid_token} and the 403 branch was unreachable (TASK-BE-595).
     * The validator's real codes live only in the inner frame's error list.
     *
     * <p>Consequence, deliberate: a token rejected for its tenant <em>and</em> for something
     * else (e.g. also expired) is 403. Re-authenticating cannot cure the tenant, so "go
     * re-authenticate" would be the lie this mapping exists to avoid. The shared
     * {@code libs/java-gateway} SecurityConfig (wms/scm/erp/finance/fan) agrees for
     * expired + tenant, but not in every mix: it reports the first non-{@code invalid_token}
     * error, so a token with a disallowed issuer <em>and</em> a foreign tenant is 401 there
     * ({@code invalid_issuer} is listed first) and 403 here.
     */
    private static OAuth2Error findTenantMismatch(Throwable ex) {
        return findInCauseChain(ex, TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH);
    }

    /**
     * The chain-walk behind {@link #findTenantMismatch}, for any validator error code: returns
     * the first {@link OAuth2Error} carrying {@code errorCode} in an
     * {@link OAuth2AuthenticationException} frame or a {@link JwtValidationException}'s error
     * list, or {@code null}. Guards against a self-cause loop.
     */
    private static OAuth2Error findInCauseChain(Throwable ex, String errorCode) {
        for (Throwable cur = ex; cur != null; cur = cur.getCause()) {
            if (cur instanceof OAuth2AuthenticationException oauthEx && hasCode(oauthEx.getError(), errorCode)) {
                return oauthEx.getError();
            }
            if (cur instanceof JwtValidationException jve) {
                for (OAuth2Error error : jve.getErrors()) {
                    if (hasCode(error, errorCode)) {
                        return error;
                    }
                }
            }
            if (cur == cur.getCause()) {
                break;
            }
        }
        return null;
    }

    private static boolean hasCode(OAuth2Error error, String errorCode) {
        return error != null && errorCode.equals(error.getErrorCode());
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
