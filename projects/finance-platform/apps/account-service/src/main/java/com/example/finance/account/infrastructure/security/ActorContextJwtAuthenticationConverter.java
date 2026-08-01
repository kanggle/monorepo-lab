package com.example.finance.account.infrastructure.security;

import com.example.finance.account.application.ActorContext;
import com.example.security.oauth2.TenantClaimValidator;
import com.example.security.servlet.actor.ActorAuthenticationToken;
import com.example.security.servlet.actor.ActorClaims;
import com.example.security.servlet.actor.ActorContextFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Converts a verified {@link Jwt} into an authentication token whose principal is an
 * {@link ActorContext}, so use cases never touch Spring Security.
 *
 * <h2>Mechanism vs. policy (ADR-MONO-058 § D1, TASK-FIN-BE-065)</h2>
 *
 * The <strong>mechanism</strong> — lifting {@code sub} / {@code tenant_id} / {@code roles}-or-{@code role}
 * and building the base {@code ROLE_*} authorities — is the shared
 * {@link ActorClaims} in {@code libs/java-security-servlet}; finance's own actor type is threaded
 * through the {@link ActorContextFactory} seam ({@link #ACTOR_FACTORY}) so the library never learns
 * an {@code ActorContext} or a finance role name.
 *
 * <p>What stays here is finance's authorization <strong>policy</strong>
 * (`platform/shared-library-policy.md § Ownership Rule`) — this converter grants strictly more than the
 * shared mechanism knows about:
 *
 * <ul>
 *   <li>{@code SCOPE_*} authorities lifted from the OAuth2 {@code scope}/{@code scp} claim
 *       (TASK-FIN-BE-046);</li>
 *   <li>the entitlement-trust {@link #VIEWER_ROLE} (TASK-FIN-BE-048);</li>
 *   <li>the platform super-admin wildcard {@link #SUPERADMIN_READ_ROLE} (TASK-FIN-BE-049).</li>
 * </ul>
 *
 * <p>Those extra authorities are why this is a composition over {@link ActorClaims} rather than a bare
 * {@code implements}/subclass of the shared
 * {@code ActorContextJwtAuthenticationConverter<A>}: {@link ActorAuthenticationToken}'s authority
 * collection is fixed at construction ({@code AbstractAuthenticationToken} contract), so the finance
 * authorities have to be in the collection <em>before</em> the token is built, not appended to a
 * ready-made one.
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

    /**
     * The READ-visibility role synthesised for a platform super-admin wildcard token
     * ({@code tenant_id="*"}). Distinct from {@link #VIEWER_ROLE} so the two admission axes —
     * customer entitlement vs. platform wildcard — stay separable in authority logs (audit clarity).
     * READ only, exactly like {@link #VIEWER_ROLE}: never added to {@code writeAuthorities}.
     */
    public static final String SUPERADMIN_READ_ROLE = "ROLE_FINANCE_SUPERADMIN_READ";

    /**
     * The Ownership-Rule seam: the shared mechanism hands back three plain claim values, and
     * account-service turns them into its own actor type. The role-set literals and role predicates
     * ({@link ActorContext#isOperator()}, {@link ActorContext#actorType()}) stay on that record —
     * they are finance's authorization policy, not library mechanism.
     */
    private static final ActorContextFactory<ActorContext> ACTOR_FACTORY = ActorContext::new;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        // Mechanism (shared): sub / tenant_id guards, roles-or-role normalisation, ROLE_* authorities.
        ActorClaims claims = ActorClaims.from(jwt);
        ActorContext actor =
                ACTOR_FACTORY.create(claims.accountId(), claims.tenantId(), claims.roles());
        Collection<GrantedAuthority> authorities = new ArrayList<>(claims.authorities());
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
        // Platform super-admin wildcard READ authority (TASK-FIN-BE-049, ADR-MONO-019 § D5 — the
        // authority-layer analogue of the tenant gate's allowSuperAdminWildcard()): a platform
        // super-admin's base OIDC domain-facing token carries tenant_id="*" but no finance scope,
        // no finance/domain role (per ADR-033 S2 / ADR-034 U5 the admin plane's SUPER_ADMIN is
        // deliberately kept OFF the domain-facing token), and entitled_domains=[]. Layer-1 already
        // admits it via allowSuperAdminWildcard() "so a platform operator can reach this edge during
        // incident response"; before FIN-BE-049 layer-2 (the readAuthorities gate tightened by
        // FIN-BE-046/047) held none of what this token carries, so its READS 403'd (nightly-e2e run
        // 29635409302, console super-admin persona: finance overview card forbidden,
        // reason=PERMISSION_DENIED). Grant ROLE_FINANCE_SUPERADMIN_READ so its READS pass. This is
        // the wildcard sibling of the entitlement-trust straggler FIN-BE-048 closed — keyed STRICTLY
        // on tenant_id="*" (not on "authenticated"), so a non-wildcard scopeless/roleless token is
        // unaffected. Synthesises ONLY the READ role — the WRITE gate is untouched, so the wildcard
        // widens READ visibility only, never mutation authority.
        if (TenantClaimValidator.WILDCARD_TENANT.equals(claims.tenantId())) {
            authorities.add(new SimpleGrantedAuthority(SUPERADMIN_READ_ROLE));
        }
        return new ActorAuthenticationToken(jwt, actor, claims.accountId(), authorities);
    }

    /**
     * Extract OAuth2 scopes. GAP issues {@code scope} as a JSON array (e.g.
     * {@code ["finance.read"]}); RFC 6749 also allows a space-delimited string, and {@code scp}
     * is a common alias — accept all three shapes.
     *
     * <p>Stays in-service: the shared mechanism deliberately lifts identity + roles only, and which
     * OAuth2 scopes exist is a finance/GAP contract (`iam-integration.md § Token 검증 규칙 #5`).
     */
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
}
