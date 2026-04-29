package com.wms.gateway.filter;

import com.wms.gateway.error.GatewayErrorHandler;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .flatMap(auth -> {
                    String accountType = auth.getToken().getClaimAsString("account_type");
                    return Mono.just("OPERATOR".equals(accountType));
                })
                .defaultIfEmpty(Boolean.TRUE) // no JWT security context → public path → proceed
                .flatMap(proceed -> proceed
                        ? chain.filter(exchange)
                        : errorHandler.write(exchange, HttpStatus.FORBIDDEN,
                                "FORBIDDEN", "WMS access requires OPERATOR account"));
    }

    @Override
    public int getOrder() {
        return -2;
    }
}
