package com.example.gateway.filter;

import com.example.web.dto.ErrorResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Enforces account_type constraints per route:
 * <ul>
 *   <li>{@code /api/admin/**} → requires {@code account_type: OPERATOR}; CONSUMER → 403</li>
 *   <li>All other authenticated routes → requires {@code account_type: CONSUMER}; OPERATOR → 403</li>
 *   <li>Public routes (no security context) → passes through unchanged</li>
 * </ul>
 *
 * <p>Uses a Boolean intermediate value to avoid the {@code switchIfEmpty(chain.filter())}
 * anti-pattern: {@code Mono<Void>} completes empty on success, which makes it
 * indistinguishable from "no auth context present".
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountTypeEnforcementFilter implements GlobalFilter, Ordered {

    private static final int ORDER = -2;
    private static final String MSG_CONSUMER_ONLY = "This operation requires a consumer account";
    private static final String MSG_OPERATOR_ONLY = "This operation requires an operator account";

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .flatMap(auth -> {
                    org.springframework.security.oauth2.jwt.Jwt token = auth.getToken();
                    java.util.List<String> roles = token.getClaimAsStringList("roles");
                    String accountType = token.getClaimAsString("account_type");
                    boolean isAdmin = path.startsWith("/api/admin/");
                    if (isAdmin) {
                        // Role-based admission (ADR-MONO-032): an admin-family role grants
                        // access; the legacy account_type=OPERATOR is accepted only during
                        // the dual-read migration window (jwt-standard-claims § Migration
                        // Compatibility) and is removed at D5 step 4.
                        boolean allowed = hasRole(roles, "ADMIN") || "OPERATOR".equals(accountType);
                        return Mono.just(allowed);
                    } else {
                        boolean allowed = hasRole(roles, "CUSTOMER") || "CONSUMER".equals(accountType);
                        return Mono.just(allowed);
                    }
                })
                // No JWT context → public route, pass through
                .defaultIfEmpty(Boolean.TRUE)
                .flatMap(allowed -> {
                    if (allowed) {
                        return chain.filter(exchange);
                    }
                    boolean isAdmin = path.startsWith("/api/admin/");
                    String message = isAdmin ? MSG_OPERATOR_ONLY : MSG_CONSUMER_ONLY;
                    return writeForbidden(exchange, message);
                });
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private static boolean hasRole(java.util.List<String> roles, String role) {
        return roles != null && roles.contains(role);
    }

    private Mono<Void> writeForbidden(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        ErrorResponse body = ErrorResponse.of("FORBIDDEN", message);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            return response.setComplete();
        }
    }
}
