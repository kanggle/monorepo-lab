package com.wms.gateway.filter;

import com.wms.gateway.error.GatewayErrorHandler;
import com.wms.gateway.security.ReactiveJwtAccess;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Rejects requests whose JWT {@code account_type} claim is not {@code OPERATOR}.
 * Runs after Spring Security populates the security context but before header enrichment.
 *
 * <p>Uses a Boolean intermediate Mono to distinguish "proceed" from "reject" without
 * relying on switchIfEmpty, which always fires on Mono&lt;Void&gt; completions.
 */
@Component
public class AccountTypeValidationFilter implements GlobalFilter, Ordered {

    private final GatewayErrorHandler errorHandler;

    public AccountTypeValidationFilter(GatewayErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveJwtAccess.currentJwt()
                .map(AccountTypeValidationFilter::isOperator)
                .defaultIfEmpty(Boolean.TRUE) // no JWT security context → public path → proceed
                .flatMap(proceed -> proceed
                        ? chain.filter(exchange)
                        : errorHandler.write(exchange, HttpStatus.FORBIDDEN,
                                "FORBIDDEN", "WMS access requires an operator role"));
    }

    /**
     * Role-based admission (ADR-MONO-032): wms is an operator-only platform, so a token
     * scoped to {@code aud=wms} carrying at least one role is an operator. The legacy
     * {@code account_type=OPERATOR} is accepted only during the dual-read migration window
     * (jwt-standard-claims § Migration Compatibility) and is removed at D5 step 4.
     */
    private static boolean isOperator(org.springframework.security.oauth2.jwt.Jwt jwt) {
        java.util.List<String> roles = jwt.getClaimAsStringList("roles");
        boolean hasRole = roles != null && !roles.isEmpty();
        return hasRole || "OPERATOR".equals(jwt.getClaimAsString("account_type"));
    }

    @Override
    public int getOrder() {
        return -2;
    }
}
