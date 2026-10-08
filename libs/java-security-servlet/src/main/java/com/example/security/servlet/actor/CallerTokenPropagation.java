package com.example.security.servlet.actor;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;

/**
 * Reads the current caller's verified JWT back off the Spring {@code SecurityContext} and forwards
 * it, unchanged, as the {@code Authorization: Bearer} header of an outbound {@link RestClient}
 * request (ADR-MONO-058 § D1 neighbourhood; promoted by TASK-MONO-778).
 *
 * <p>Two call sites carried the same six lines before this class existed — the trigger its own
 * javadoc named ("promote when a second service needs the same six lines") had fired. Both kept
 * their own identity check (who the caller must be, which tenant they must match) and their own
 * failure-cause metric; only the mechanical "read the token / attach it as Bearer" part moved here.
 *
 * <p>{@link ActorAuthenticationToken} extends {@link JwtAuthenticationToken}, so a service's typed
 * actor principal is covered by {@link #currentCallerToken()} without this class depending on the
 * actor type.
 */
public final class CallerTokenPropagation {

    private CallerTokenPropagation() {
    }

    /**
     * @return the current caller's verified token, or {@code null} when nothing OAuth2-shaped is on
     *         the context — no authentication at all, or an {@code Authentication} that is not a
     *         {@link JwtAuthenticationToken} (e.g. an async/virtual-thread call site where
     *         {@code SecurityContextHolder} comes up empty).
     */
    public static Jwt currentCallerToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth instanceof JwtAuthenticationToken jwtAuth ? jwtAuth.getToken() : null;
    }

    /**
     * Attaches {@code token} to {@code spec} as an {@code Authorization: Bearer} header, using
     * {@link Jwt#getTokenValue()} verbatim — no claim rebuilding, so the header this produces is
     * byte-identical to the token this service itself was called with (every claim the token
     * carries, including ones this library does not know about, survives the hop unchanged).
     *
     * @param spec  the request being built, typically {@code restClient.get().uri(path)}
     * @param token the token to forward, e.g. from {@link #currentCallerToken()}
     * @return {@code spec}, for chaining
     */
    public static RestClient.RequestHeadersSpec<?> withBearerToken(
            RestClient.RequestHeadersSpec<?> spec, Jwt token) {
        return spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token.getTokenValue());
    }
}
