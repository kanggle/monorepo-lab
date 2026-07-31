package com.example.erp.approval.infrastructure.security;

import com.example.erp.approval.application.ActorContext;
import com.example.security.jwt.AbacDataScope;
import com.example.security.oauth2.TenantClaimValidator;
import com.example.security.servlet.actor.ActorAuthenticationToken;
import com.example.security.servlet.actor.ActorClaims;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Converts a verified {@link Jwt} into an {@link ActorAuthenticationToken} whose principal is this
 * service's own {@link ActorContext} — ADR-MONO-058 § D1, adopted for approval-service.
 *
 * <h2>What moved to {@code libs/java-security-servlet}, and what did not</h2>
 *
 * The <strong>mechanism</strong> is the library's: {@link ActorClaims#from(Jwt)} lifts and validates
 * {@code sub}/{@code tenant_id} (including the two contractual {@code IllegalStateException} messages
 * that this service maps to 422 {@code ILLEGAL_STATE}), {@link ActorClaims#authorities()} does the
 * {@code ROLE_} prefixing, and {@link ActorAuthenticationToken} is the {@code JwtAuthenticationToken}
 * subclass. This service's own {@code ActorContextJwtAuthenticationConverter} and
 * {@code ActorContextResolver} are deleted; the resolver is now
 * {@link com.example.security.servlet.actor.ActorContextResolver#currentOrThrow(Class)}.
 *
 * <p>What stays here is <strong>erp policy</strong>, and it is the reason this class exists rather
 * than a bare {@code new ActorContextJwtAuthenticationConverter<>(ActorContext::new)}:
 *
 * <ol>
 *   <li><strong>The role-claim alias set is erp's, and it is wider than the shared one.</strong>
 *       {@link ActorClaims#from(Jwt)} normalises {@code roles}-or-{@code role}, which is the fleet
 *       mechanism § D1 promoted. erp additionally folds the OAuth2 {@code scope}/{@code scopes}
 *       claims into the same set, because {@link ActorContext#hasScope(String)} reads that set and
 *       {@link ActorContext#canReadErp()} / {@link ActorContext#canWriteErp()} authorise off
 *       {@code erp.read} / {@code erp.write} / {@code erp.approval.*} — which arrive on
 *       {@code scope}, not on {@code roles}. Adopting the shared converter verbatim would silently
 *       drop every machine-token scope from both the actor and the granted authorities. That
 *       decision — "an OAuth2 scope is an erp role token" — is erp's authorization policy, so it
 *       stays in erp (Ownership Rule) and only the lifted triple is handed to the library's
 *       {@link ActorClaims} carrier.</li>
 *   <li><strong>{@code dataScopeDepartmentIds} and {@code entitledDomains}.</strong> erp's
 *       {@link ActorContext} carries two components the fleet-wide three-component shape never had
 *       (ADR-MONO-025 {@code org_scope}; ADR-MONO-019 § D5 entitlement-trust). The shared
 *       {@link com.example.security.servlet.actor.ActorContextFactory} signature is
 *       {@code (accountId, tenantId, roles) -> A} and deliberately carries no project field, so
 *       both are sourced here, from the raw JWT, at the composition site — as is the
 *       {@code client_credentials → ["*"]} data-scope default, which this service keeps and
 *       masterdata-service does not (TASK-ERP-BE-029 removed it there as dead code; the two
 *       services' converters were never byte-identical, and this adoption does not make them so).
 *       </li>
 * </ol>
 */
public class ErpActorClaimsConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        // The library lifts and validates sub + tenant_id; its role normalisation is replaced by
        // erp's wider alias set (see the class javadoc), so the triple is re-composed here.
        ActorClaims lifted = ActorClaims.from(jwt);
        ActorClaims claims = new ActorClaims(
                lifted.accountId(), lifted.tenantId(), roleAndScopeTokens(jwt));
        ActorContext actor = new ActorContext(claims.accountId(), claims.tenantId(), claims.roles(),
                dataScope(jwt, claims.roles()), entitledDomains(jwt));
        return new ActorAuthenticationToken(jwt, actor, claims.accountId(), claims.authorities());
    }

    /**
     * erp's role/scope token set — the union of the {@code roles}, {@code role}, {@code scope} and
     * {@code scopes} claims, array or delimited-string form. Unchanged from the pre-adoption
     * converter, deliberately: the alias list is authorization policy this service's
     * {@link ActorContext#canReadErp()} / {@link ActorContext#canWriteErp()} scope-tuples read.
     */
    private static Set<String> roleAndScopeTokens(Jwt jwt) {
        Set<String> out = new HashSet<>();
        for (String name : new String[]{"roles", "role", "scope", "scopes"}) {
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
     * The data-scope token set, parsed by the shared canonical reader {@link AbacDataScope}
     * (ADR-MONO-025; dual-reads {@code org_scope} + {@code data_scope}); only the domain-local
     * {@code client_credentials → ["*"]} default is kept here. Downstream interpretation (subtree
     * containment) lives in the authorization adapter.
     */
    private static Set<String> dataScope(Jwt jwt, Set<String> roleAndScopeTokens) {
        Set<String> scope = new HashSet<>(AbacDataScope.fromClaimValues(
                jwt.getClaim("org_scope"), jwt.getClaim("data_scope")).tokens());
        if (scope.isEmpty() && roleAndScopeTokens.contains("client_credentials")) {
            scope = Set.of("*");
        }
        return scope;
    }

    /**
     * Extracts the signed {@code entitled_domains} claim (ADR-MONO-019 § D5) fail-closed: it MUST
     * be a JSON list of strings. Any anomaly degrades to the empty set (no NPE, no blanket trust),
     * mirroring {@link TenantClaimValidator#isEntitled(Jwt, String)} / {@code safeStringList}. The
     * CSV/space-split alias path used for roles is deliberately NOT applied — {@code
     * entitled_domains} is a structured list claim.
     */
    private static Set<String> entitledDomains(Jwt jwt) {
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
}
