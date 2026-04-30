package com.example.gateway.filter;

import com.example.gateway.config.EdgeGatewayProperties;
import com.example.gateway.route.RouteConfig;
import com.example.gateway.security.TokenValidator;
import com.example.web.dto.ErrorResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gap.security.jwt.JwtVerificationException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JWT authentication filter for protected routes.
 * Validates RS256 JWT tokens, checks exp/nbf/tenant_id claims,
 * strips spoofed X-Account-ID / X-Tenant-Id headers, and injects verified headers.
 *
 * Also enforces tenant scope on /internal/tenants/{tenantId}/** routes.
 */
@Slf4j
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final int ORDER = -100;
    private static final String ACCOUNT_ID_HEADER = "X-Account-ID";
    private static final String DEVICE_ID_HEADER = "X-Device-Id";
    private static final String TENANT_ID_HEADER = "X-Tenant-Id";
    private static final String ACCESS_INVALIDATE_KEY_PREFIX = "access:invalidate-before:";
    private static final Pattern INTERNAL_TENANT_PATH_PATTERN =
            Pattern.compile("^/internal/tenants/([^/]+)(/.*)?$");
    private static final String FALLBACK_METRIC_NAME = "gateway_tenant_fallback_total";

    private final TokenValidator tokenValidator;
    private final RouteConfig routeConfig;
    private final ObjectMapper objectMapper;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final EdgeGatewayProperties properties;
    private final MeterRegistry meterRegistry;

    public JwtAuthenticationFilter(TokenValidator tokenValidator,
                                   RouteConfig routeConfig,
                                   ObjectMapper objectMapper,
                                   ReactiveStringRedisTemplate redisTemplate,
                                   EdgeGatewayProperties properties,
                                   MeterRegistry meterRegistry) {
        this.tokenValidator = tokenValidator;
        this.routeConfig = routeConfig;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // 1. Always strip spoofed headers (including X-Tenant-Id from external clients)
        ServerHttpRequest stripped = stripSpoofedHeaders(request);
        ServerWebExchange strippedExchange = exchange.mutate().request(stripped).build();

        // 2. Public routes pass through without auth
        if (routeConfig.isPublicRoute(request.getMethod(), path)) {
            return chain.filter(strippedExchange);
        }

        // 3. Extract Authorization header
        String authHeader = stripped.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return writeUnauthorized(exchange,
                    "Access token is missing, expired, or has an invalid signature");
        }

        String token = authHeader.substring(7);

        // 4. Validate token and enrich request
        return tokenValidator.validate(token)
                .flatMap(claims -> {
                    String accountId = extractAccountId(claims);
                    if (accountId == null) {
                        return writeUnauthorized(exchange,
                                "Access token is missing, expired, or has an invalid signature");
                    }

                    // 5. Extract and validate tenant_id claim
                    String tenantId = extractTenantId(claims);
                    if (tenantId == null) {
                        EdgeGatewayProperties.TenantProperties.LegacyFallbackProperties fallback =
                                properties.getTenant().getLegacyFallback();
                        if (fallback.isEnabled()) {
                            tenantId = fallback.getDefaultTenantId();
                            log.warn("tenant_id claim missing in JWT for accountId={}, using fallback tenantId={}",
                                    accountId, tenantId);
                            recordFallbackCounter();
                        } else {
                            log.debug("tenant_id claim missing in JWT for accountId={}, rejecting (fallback disabled)",
                                    accountId);
                            return writeUnauthorized(exchange,
                                    "Access token is missing, expired, or has an invalid signature");
                        }
                    }

                    // 6. Check internal provisioning route tenant scope
                    String pathTenantId = extractPathTenantId(path);
                    if (pathTenantId != null && !pathTenantId.equals(tenantId)) {
                        log.warn("SECURITY: tenant scope mismatch on path={}, pathTenantId={}, jwtTenantId={}, accountId={}",
                                path, pathTenantId, tenantId, accountId);
                        return writeForbidden(exchange,
                                "Tenant scope mismatch: path tenantId does not match token claim");
                    }

                    final String resolvedTenantId = tenantId;

                    // 7. Check if account's access tokens were force-invalidated after token issuance
                    long iatEpochMilli = extractIatEpochMilli(claims);
                    return redisTemplate.opsForValue()
                            .get(ACCESS_INVALIDATE_KEY_PREFIX + accountId)
                            .defaultIfEmpty("")
                            .flatMap(storedValue -> {
                                if (!storedValue.isEmpty()) {
                                    try {
                                        long invalidatedAtMilli = Long.parseLong(storedValue);
                                        if (iatEpochMilli <= invalidatedAtMilli) {
                                            return writeUnauthorized(exchange,
                                                    "Access token is missing, expired, or has an invalid signature");
                                        }
                                    } catch (NumberFormatException ignored) {
                                        // corrupt key — fail open, let token through
                                    }
                                }

                                return chain.filter(strippedExchange.mutate()
                                        .request(buildEnrichedRequest(stripped, accountId, resolvedTenantId, claims))
                                        .build());
                            })
                            .onErrorResume(e -> {
                                // Redis unavailable — fail open to avoid outage
                                log.warn("Redis unavailable for access invalidation check: {}", e.getMessage());
                                return chain.filter(strippedExchange.mutate()
                                        .request(buildEnrichedRequest(stripped, accountId, resolvedTenantId, claims))
                                        .build());
                            });
                })
                .onErrorResume(JwtVerificationException.class, e -> {
                    log.debug("JWT verification failed: {}", e.getMessage());
                    return writeUnauthorized(exchange,
                            "Access token is missing, expired, or has an invalid signature");
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("Unexpected error during JWT verification: {}", e.getMessage(), e);
                    return writeUnauthorized(exchange,
                            "Access token is missing, expired, or has an invalid signature");
                });
    }

    private ServerHttpRequest buildEnrichedRequest(ServerHttpRequest stripped,
                                                   String accountId,
                                                   String tenantId,
                                                   Map<String, Object> claims) {
        ServerHttpRequest.Builder builder = stripped.mutate()
                .header(ACCOUNT_ID_HEADER, accountId)
                .header(TENANT_ID_HEADER, tenantId);

        String deviceId = extractDeviceId(claims);
        if (deviceId != null && !deviceId.isBlank()) {
            builder.header(DEVICE_ID_HEADER, deviceId);
        }
        return builder.build();
    }

    private ServerHttpRequest stripSpoofedHeaders(ServerHttpRequest request) {
        return request.mutate()
                .headers(h -> {
                    h.remove(ACCOUNT_ID_HEADER);
                    h.remove(DEVICE_ID_HEADER);
                    h.remove(TENANT_ID_HEADER);
                })
                .build();
    }

    private String extractAccountId(Map<String, Object> claims) {
        Object sub = claims.get("sub");
        if (sub != null) {
            return sub.toString();
        }
        Object accountId = claims.get("accountId");
        if (accountId != null) {
            return accountId.toString();
        }
        return null;
    }

    private String extractTenantId(Map<String, Object> claims) {
        Object tenantId = claims.get("tenant_id");
        if (tenantId != null) {
            String value = tenantId.toString().trim();
            return value.isEmpty() ? null : value;
        }
        return null;
    }

    private String extractDeviceId(Map<String, Object> claims) {
        Object deviceId = claims.get("device_id");
        return deviceId != null ? deviceId.toString() : null;
    }

    /**
     * Extracts {tenantId} from /internal/tenants/{tenantId}/... paths.
     * Returns null if the path is not an internal provisioning route.
     */
    private String extractPathTenantId(String path) {
        Matcher matcher = INTERNAL_TENANT_PATH_PATTERN.matcher(path);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }

    /** JWT iat is epoch seconds; convert to millis for Redis comparison. */
    private long extractIatEpochMilli(Map<String, Object> claims) {
        Object iat = claims.get("iat");
        if (iat instanceof Number n) {
            return n.longValue() * 1000L;
        }
        return 0L;
    }

    private void recordFallbackCounter() {
        Counter.builder(FALLBACK_METRIC_NAME)
                .description("Number of requests where tenant_id claim was missing and legacy fallback was used")
                .register(meterRegistry)
                .increment();
    }

    private Mono<Void> writeUnauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ErrorResponse errorResponse = ErrorResponse.of("TOKEN_INVALID", message);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(errorResponse);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            return response.writeWith(Mono.just(
                    response.bufferFactory().wrap(
                            "{\"code\":\"TOKEN_INVALID\",\"message\":\"Access token is missing, expired, or has an invalid signature\"}"
                                    .getBytes(StandardCharsets.UTF_8))));
        }
    }

    private Mono<Void> writeForbidden(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ErrorResponse errorResponse = ErrorResponse.of("TENANT_SCOPE_DENIED", message);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(errorResponse);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            return response.writeWith(Mono.just(
                    response.bufferFactory().wrap(
                            "{\"code\":\"TENANT_SCOPE_DENIED\",\"message\":\"Tenant scope mismatch\"}"
                                    .getBytes(StandardCharsets.UTF_8))));
        }
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
