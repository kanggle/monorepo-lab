package com.example.fanplatform.artist.adapter.in.web.security;

import com.example.security.oauth2.TenantClaimValidator;

import com.example.fanplatform.artist.application.ActorContext;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Converts a verified {@link Jwt} into an authentication token whose principal
 * is an {@link ActorContext}. Lifts {@code sub}, {@code tenant_id}, and the
 * {@code roles}/{@code role} claim into a typed value so use cases never
 * touch Spring Security directly. Mirrors community-service.
 */
public class ActorContextJwtAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String accountId = jwt.getSubject();
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalStateException("sub claim is missing on the JWT");
        }
        String tenantId = jwt.getClaimAsString(TenantClaimValidator.CLAIM_TENANT_ID);
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("tenant_id claim is missing on the JWT");
        }
        Set<String> roles = extractRoles(jwt);
        ActorContext actor = new ActorContext(accountId, tenantId, roles);
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        for (String role : roles) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
        return new ActorContextJwtAuthenticationToken(jwt, actor, authorities);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> extractRoles(Jwt jwt) {
        Object raw = jwt.getClaim("roles");
        if (raw == null) raw = jwt.getClaim("role");
        if (raw == null) return Collections.emptySet();
        Set<String> out = new HashSet<>();
        if (raw instanceof Collection<?> c) {
            for (Object v : c) out.add(String.valueOf(v));
        } else if (raw instanceof String s) {
            for (String part : s.split("[,\\s]+")) {
                if (!part.isBlank()) out.add(part);
            }
        }
        return out;
    }

    /**
     * Token whose principal is the {@link ActorContext} value.
     *
     * <p>{@code final} so that {@code setAuthenticated(true)} in the constructor
     * cannot be observed by an unfinished subclass — silences the
     * {@code [this-escape]} warning from {@code javac -Xlint:all}.
     */
    public static final class ActorContextJwtAuthenticationToken extends JwtAuthenticationToken {
        private final ActorContext actor;

        public ActorContextJwtAuthenticationToken(Jwt jwt,
                                                  ActorContext actor,
                                                  Collection<? extends GrantedAuthority> authorities) {
            super(jwt, authorities, actor.accountId());
            this.actor = actor;
            setAuthenticated(true);
        }

        @Override
        public Object getPrincipal() {
            return actor;
        }
    }
}
