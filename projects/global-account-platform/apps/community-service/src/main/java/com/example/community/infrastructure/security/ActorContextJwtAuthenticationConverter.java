package com.example.community.infrastructure.security;

import com.example.community.application.ActorContext;
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
import java.util.List;
import java.util.Set;

/**
 * Converts a verified {@link Jwt} into a Spring Security {@link AbstractAuthenticationToken}
 * whose principal is the existing {@link ActorContext} (TASK-BE-253).
 *
 * <p>The legacy {@code AccountAuthenticationFilter} produced an {@code ActorContext}
 * principal directly. The Resource Server filter chain works on JWTs, so we adapt
 * the standard {@link JwtAuthenticationToken} into a token that exposes the same
 * {@link ActorContext} object so that {@link ActorContextResolver} keeps working
 * unchanged.
 *
 * <p>Roles are read from either {@code roles} (preferred) or {@code role} claim,
 * matching the legacy semantics. Each role becomes a {@code ROLE_<name>} authority.
 */
public class ActorContextJwtAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String accountId = jwt.getSubject();
        if (accountId == null || accountId.isBlank()) {
            // Should not happen when standard validators are configured; defensive.
            throw new IllegalStateException("sub claim is missing on the JWT");
        }
        Set<String> roles = extractRoles(jwt);
        ActorContext actor = new ActorContext(accountId, roles);
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
     * {@link JwtAuthenticationToken} subclass that exposes {@link ActorContext} as the
     * principal — preserves source compatibility with {@link ActorContextResolver}.
     */
    public static class ActorContextJwtAuthenticationToken extends JwtAuthenticationToken {
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
