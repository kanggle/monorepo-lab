package com.example.erp.masterdata.infrastructure.security;

import com.example.erp.masterdata.application.ActorContext;
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
 * is an {@link ActorContext} — lifts {@code sub}, {@code tenant_id},
 * {@code roles}/{@code role}/{@code scope} (claim name aliases) and
 * {@code org_scope} into a typed value so use cases never touch Spring
 * Security.
 */
public class ActorContextJwtAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String actorId = jwt.getSubject();
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalStateException("sub claim is missing on the JWT");
        }
        String tenantId = jwt.getClaimAsString(TenantClaimValidator.CLAIM_TENANT_ID);
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("tenant_id claim is missing on the JWT");
        }
        Set<String> roles = extractClaim(jwt, "roles", "role", "scope", "scopes");
        Set<String> scope = extractClaim(jwt, "org_scope", "data_scope");
        // client_credentials machine tokens default to platform-wide scope.
        if (scope.isEmpty() && roles.contains("client_credentials")) {
            scope = Set.of("*");
        }
        // ADR-MONO-019 § D5 entitlement-trust: lift the signed entitled_domains
        // claim into the actor so the authorization layer can dual-accept READ
        // (mirrors TenantClaimValidator.isEntitled / safeStringList — fail-closed
        // on any shape anomaly: absent / non-list / non-string element → empty).
        Set<String> entitledDomains = extractEntitledDomains(jwt);
        ActorContext actor = new ActorContext(actorId, tenantId, roles, scope, entitledDomains);
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        for (String role : roles) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
        return new ActorContextJwtAuthenticationToken(jwt, actor, authorities);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> extractClaim(Jwt jwt, String... names) {
        Set<String> out = new HashSet<>();
        for (String name : names) {
            Object raw = jwt.getClaim(name);
            if (raw == null) continue;
            if (raw instanceof Collection<?> c) {
                for (Object v : c) {
                    String s = String.valueOf(v);
                    if (!s.isBlank()) out.add(s);
                }
            } else if (raw instanceof String s) {
                for (String part : s.split("[,\\s]+")) {
                    if (!part.isBlank()) out.add(part);
                }
            }
        }
        return out.isEmpty() ? Collections.emptySet() : out;
    }

    /**
     * Extracts the signed {@code entitled_domains} claim (ADR-MONO-019 § D5)
     * fail-closed: it MUST be a JSON list of strings (the shape GAP's
     * {@code TenantClaimTokenCustomizer} emits). Any anomaly — absent /
     * non-list / null or non-string element — degrades to the empty set (no
     * NPE, no blanket trust), mirroring
     * {@link TenantClaimValidator#isEntitled(Jwt, String)} /
     * {@code safeStringList}. The CSV/space-split alias path used for roles is
     * deliberately NOT applied here: {@code entitled_domains} is a structured
     * list claim, never a delimited string.
     */
    private static Set<String> extractEntitledDomains(Jwt jwt) {
        Object raw = jwt.getClaims().get(TenantClaimValidator.CLAIM_ENTITLED_DOMAINS);
        if (!(raw instanceof Collection<?> list)) {
            return Collections.emptySet();
        }
        Set<String> out = new HashSet<>();
        for (Object element : list) {
            if (element instanceof String s && !s.isBlank()) {
                out.add(s);
            }
        }
        return out.isEmpty() ? Collections.emptySet() : out;
    }

    /**
     * {@code final} so that {@code setAuthenticated(true)} in the constructor
     * cannot be observed by an unfinished subclass — silences the
     * {@code [this-escape]} warning from {@code javac -Xlint:all}.
     */
    public static final class ActorContextJwtAuthenticationToken extends JwtAuthenticationToken {
        private final ActorContext actor;

        public ActorContextJwtAuthenticationToken(Jwt jwt, ActorContext actor,
                                                  Collection<? extends GrantedAuthority> authorities) {
            super(jwt, authorities, actor.actorId());
            this.actor = actor;
            setAuthenticated(true);
        }

        @Override
        public Object getPrincipal() {
            return actor;
        }
    }
}
