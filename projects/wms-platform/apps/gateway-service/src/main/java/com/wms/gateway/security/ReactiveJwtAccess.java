package com.wms.gateway.security;

import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

/**
 * Reactive accessor for the currently-authenticated JWT.
 *
 * <p>Returns an empty {@link Mono} when the security context is absent or the
 * authentication is not a {@link JwtAuthenticationToken} (e.g. public paths
 * configured via {@code permitAll()}). Callers retain responsibility for any
 * fallback (typically via {@code defaultIfEmpty(...)}).
 */
public final class ReactiveJwtAccess {

    private ReactiveJwtAccess() {}

    public static Mono<JwtAuthenticationToken> currentToken() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class);
    }

    public static Mono<Jwt> currentJwt() {
        return currentToken().map(JwtAuthenticationToken::getToken);
    }
}
