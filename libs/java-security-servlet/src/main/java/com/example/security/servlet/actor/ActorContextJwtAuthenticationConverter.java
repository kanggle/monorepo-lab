package com.example.security.servlet.actor;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Objects;

/**
 * Converts a verified {@link Jwt} into an {@link ActorAuthenticationToken} whose principal is the
 * service's own actor value object (ADR-MONO-058 § D1).
 *
 * <p>Wire it into the Resource Server chain, supplying the service's own actor constructor:
 *
 * <pre>{@code
 * .oauth2ResourceServer(rs -> rs
 *         .jwt(jwt -> jwt.jwtAuthenticationConverter(
 *                 new ActorContextJwtAuthenticationConverter<>(ActorContext::new))))
 * }</pre>
 *
 * <p>Claim lifting and {@code ROLE_} prefixing live in {@link ActorClaims}; everything role-<em>meaning</em>
 * related stays in the consuming service.
 *
 * @param <A> the service's own actor type
 */
public class ActorContextJwtAuthenticationConverter<A>
        implements Converter<Jwt, AbstractAuthenticationToken> {

    private final ActorContextFactory<A> actorFactory;

    public ActorContextJwtAuthenticationConverter(ActorContextFactory<A> actorFactory) {
        this.actorFactory = Objects.requireNonNull(actorFactory, "actorFactory");
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        ActorClaims claims = ActorClaims.from(jwt);
        A actor = actorFactory.create(claims.accountId(), claims.tenantId(), claims.roles());
        return new ActorAuthenticationToken(jwt, actor, claims.accountId(), claims.authorities());
    }
}
