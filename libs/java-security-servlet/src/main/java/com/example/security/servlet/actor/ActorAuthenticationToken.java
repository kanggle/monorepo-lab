package com.example.security.servlet.actor;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;

/**
 * A {@link JwtAuthenticationToken} whose principal is the service's own actor value object rather than
 * the raw {@link Jwt} (ADR-MONO-058 § D1).
 *
 * <p>{@code final} so that {@code setAuthenticated(true)} in the constructor cannot be observed by an
 * unfinished subclass — this silences the {@code [this-escape]} warning from {@code javac -Xlint:all},
 * which is why every promoted copy was final too.
 *
 * <p>The principal is typed {@link Object} because the actor type belongs to the consuming service
 * ({@link ActorContextFactory}); {@link ActorContextResolver#currentOrThrow(Class)} is the typed way to
 * read it back.
 */
public final class ActorAuthenticationToken extends JwtAuthenticationToken {

    private final Object actor;

    /**
     * @param jwt         the verified token
     * @param actor       the service-supplied actor value object, exposed as {@link #getPrincipal()}
     * @param name        the authentication name — the JWT {@code sub}
     * @param authorities the {@code ROLE_}-prefixed authorities
     */
    public ActorAuthenticationToken(Jwt jwt,
                                    Object actor,
                                    String name,
                                    Collection<? extends GrantedAuthority> authorities) {
        super(jwt, authorities, name);
        this.actor = actor;
        setAuthenticated(true);
    }

    @Override
    public Object getPrincipal() {
        return actor;
    }
}
