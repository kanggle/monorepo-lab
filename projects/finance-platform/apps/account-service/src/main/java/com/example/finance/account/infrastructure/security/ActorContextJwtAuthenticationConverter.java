package com.example.finance.account.infrastructure.security;

import com.example.finance.account.application.ActorContext;
import com.example.security.oauth2.TenantClaimValidator;
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
 * is an {@link ActorContext} — lifts {@code sub}, {@code tenant_id}, and
 * {@code roles}/{@code role} into a typed value so use cases never touch
 * Spring Security.
 */
public class ActorContextJwtAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    /**
     * Domain key for the entitlement-trust READ-visibility synthesis. A token whose signed
     * {@code entitled_domains} claim contains {@code finance} is granted {@link #VIEWER_ROLE}
     * (READ only) even when it carries no finance scope and no finance role.
     */
    public static final String ENTITLEMENT_DOMAIN = "finance";

    /** The single READ-visibility role synthesised from entitlement-trust. */
    public static final String VIEWER_ROLE = "ROLE_FINANCE_VIEWER";

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
        // Lift the OAuth2 `scope` claim into SCOPE_* authorities (Spring's standard prefix) so
        // SecurityConfig can require a specific scope value per endpoint. Without this the `scope`
        // claim was invisible to authorization and any authenticated finance token could write —
        // the least-privilege gap TASK-FIN-BE-046 closes. ActorContext (roles) is unchanged, so the
        // application-layer operator gate (/kyc/upgrade) keeps deriving from roles independently.
        for (String scope : extractScopes(jwt)) {
            authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
        }
        // Entitlement-trust dual-accept (ADR-MONO-019 § D5, ADR-MONO-020 D4 — the finance
        // analogue of TASK-MONO-162, closing the FIN-BE-046/047 read straggler): a
        // finance-entitled token (entitled_domains ∋ "finance") is granted ROLE_FINANCE_VIEWER
        // so the /api/finance/** READ gate passes even when the token carries no finance scope
        // and no finance role — the entitled-but-scopeless operator the platform console federates
        // (tenant_id=acme, entitled_domains=[finance], no roles, scope=[openid,...]). This
        // synthesises ONLY the VIEWER role — the WRITE gate (SCOPE_finance.write or an operator
        // role) is unaffected, so entitlement-trust widens READ visibility only, never mutation
        // authority. entitled_domains is read only from the RS256/JWKS-verified token, so it is
        // unforgeable; a claim-shape anomaly degrades to "not entitled" (TenantClaimValidator
        // fail-closed). Layer-1 (the tenant gate) already admitted this token via
        // trustEntitledDomains(); this closes the matching AUTHORITY-layer gap.
        if (TenantClaimValidator.isEntitled(jwt, ENTITLEMENT_DOMAIN)) {
            authorities.add(new SimpleGrantedAuthority(VIEWER_ROLE));
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
     * Extract OAuth2 scopes. GAP issues {@code scope} as a JSON array (e.g.
     * {@code ["finance.read"]}); RFC 6749 also allows a space-delimited string, and {@code scp}
     * is a common alias — accept all three shapes.
     */
    @SuppressWarnings("unchecked")
    private static Set<String> extractScopes(Jwt jwt) {
        Object raw = jwt.getClaim("scope");
        if (raw == null) raw = jwt.getClaim("scp");
        if (raw == null) return Collections.emptySet();
        Set<String> out = new HashSet<>();
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
