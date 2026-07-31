package com.example.security.servlet.actor;

import com.example.security.oauth2.TenantClaimValidator;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The actor claim-lifting <strong>mechanism</strong> shared by every project's
 * {@code ActorContextJwtAuthenticationConverter} (ADR-MONO-058 § D1).
 *
 * <h2>Mechanism, not policy</h2>
 *
 * This type lifts {@code sub}, {@code tenant_id} and the {@code roles}-or-{@code role} claim off a
 * <em>verified</em> {@link Jwt} and normalises the role claim into a plain {@code Set<String>}. It has
 * no opinion about what any role <em>means</em>, and deliberately contains no role-name literal and no
 * role predicate of any kind — not even in an example. Each consuming service keeps its own actor type,
 * with its own predicates and its own role-set literals, and receives the three lifted values through an
 * {@link ActorContextFactory}. That split is `ADR-MONO-058 § D1`'s Ownership-Rule boundary, and it is
 * the reason this class carries a {@code Set<String>} rather than a richer type.
 *
 * <h2>Role-claim normalisation</h2>
 *
 * Both wire forms observed across the fleet are accepted, {@code roles} taking precedence:
 *
 * <ul>
 *   <li>{@code "roles": ["ALPHA", "BETA"]} — any {@link Collection}; each element via
 *       {@code String.valueOf}</li>
 *   <li>{@code "role": "ALPHA,BETA"} / {@code "role": "ALPHA BETA"} — a delimited {@link String}, split
 *       on {@code [,\s]+} with blank parts dropped</li>
 *   <li>neither claim present, or a claim of any other JSON type (number, object, boolean) — the empty
 *       set. Deliberately silent: this is the auth path, and every promoted copy behaved this way. A
 *       caller with an unreadable role claim is authenticated with zero authorities, not rejected with
 *       a 500.</li>
 * </ul>
 *
 * <h2>Null-tolerant role set</h2>
 *
 * {@link #roles()} is an unmodifiable view over a {@link HashSet} — <strong>not</strong> a
 * {@code Set.copyOf(...)}. Consumers' {@code hasRole(role)} helpers call {@code roles.contains(role)}
 * directly, and {@code Set.copyOf(...).contains(null)} throws {@link NullPointerException} where a
 * {@code HashSet} returns {@code false}. Swapping in {@code Set.copyOf} would convert a would-be
 * {@code false} into a thrown exception on the authentication path.
 */
public record ActorClaims(String accountId, String tenantId, Set<String> roles) {

    private static final String ROLE_AUTHORITY_PREFIX = "ROLE_";

    /** Delimiters accepted in the single-string {@code role} claim form. */
    private static final String ROLE_STRING_DELIMITERS = "[,\\s]+";

    /**
     * Lifts the actor claims off a verified JWT.
     *
     * @throws IllegalStateException if {@code sub} or {@code tenant_id} is missing or blank — the JWT
     *         reached this point verified, so a missing identity claim is a wiring fault, not a client
     *         error
     */
    public static ActorClaims from(Jwt jwt) {
        String accountId = jwt.getSubject();
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalStateException("sub claim is missing on the JWT");
        }
        String tenantId = jwt.getClaimAsString(TenantClaimValidator.CLAIM_TENANT_ID);
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("tenant_id claim is missing on the JWT");
        }
        return new ActorClaims(accountId, tenantId, extractRoles(jwt));
    }

    /** The {@code ROLE_}-prefixed authorities for {@link #roles()}, one per role. */
    public Collection<GrantedAuthority> authorities() {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        for (String role : roles) {
            authorities.add(new SimpleGrantedAuthority(ROLE_AUTHORITY_PREFIX + role));
        }
        return authorities;
    }

    private static Set<String> extractRoles(Jwt jwt) {
        Object raw = jwt.getClaim("roles");
        if (raw == null) raw = jwt.getClaim("role");
        if (raw == null) return Collections.emptySet();
        Set<String> out = new HashSet<>();
        if (raw instanceof Collection<?> c) {
            for (Object v : c) out.add(String.valueOf(v));
        } else if (raw instanceof String s) {
            for (String part : s.split(ROLE_STRING_DELIMITERS)) {
                if (!part.isBlank()) out.add(part);
            }
        }
        return Collections.unmodifiableSet(out);
    }
}
