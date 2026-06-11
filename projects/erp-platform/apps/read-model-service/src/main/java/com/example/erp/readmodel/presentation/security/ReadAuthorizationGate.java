package com.example.erp.readmodel.presentation.security;

import com.example.erp.readmodel.config.security.TenantClaimValidator;
import com.example.security.jwt.AbacDataScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * READ authorization gate (E6, fail-closed) — the read-model equivalent of
 * masterdata-service's {@code RoleScopeAuthorizationAdapter} READ verdict:
 * READ = {@code erp.read} scope ∨ {@code isOperator()} ∨ entitled
 * ({@code entitled_domains ∋ erp}).
 *
 * <p><b>Symmetric data-scope read filter (TASK-ERP-BE-008 / ADR-MONO-020 D3
 * amendment, 2026-06-05).</b> The gate also extracts the operator's
 * {@code org_scope} claim ({@link #orgScope(Jwt)}) = department subtree-ROOT
 * ids the operator may see (membership-derived per
 * {@code operator_tenant_assignment.org_scope}). The org-view query expands
 * those roots → descendants over {@code department_proj.parent_id} and narrows
 * the list/detail to employees within ANY scoped subtree — symmetric with
 * masterdata-service's write-side subtree containment.
 *
 * <p><b>NET-ZERO</b>: {@code org_scope=["*"]} (platform scope) or an absent
 * {@code org_scope} → no read narrowing ({@link #orgScope(Jwt)} returns
 * {@code null}), so every BE-007 caller is unaffected. An explicit empty
 * {@code org_scope=[]} (zero-scope) narrows to nothing (fail-closed: empty
 * result / 404 on detail).
 *
 * <p>The platform-console operator token that already reads masterdata-service
 * satisfies this gate (same READ semantics; TASK-ERP-BE-004).
 */
@Component
public class ReadAuthorizationGate {

    private static final String SCOPE_READ = "erp.read";
    private static final String SCOPE_WRITE = "erp.write";
    private static final String DOMAIN_KEY = "erp";

    private final String domainKey;

    public ReadAuthorizationGate(
            @Value("${erpplatform.oauth2.required-tenant-id:erp}") String requiredTenantId) {
        this.domainKey = requiredTenantId == null || requiredTenantId.isBlank()
                ? DOMAIN_KEY : requiredTenantId;
    }

    /**
     * Enforces the READ gate; throws {@link ReadAccessDeniedException} (→ 403
     * {@code PERMISSION_DENIED}) when the caller is neither scoped, an operator,
     * nor entitled. A {@code null} JWT is denied (defense-in-depth; the security
     * chain should already have rejected an unauthenticated request).
     */
    public void requireRead(Jwt jwt) {
        if (jwt == null) {
            throw new ReadAccessDeniedException("no authenticated token");
        }
        Set<String> scopes = extractScopesAndRoles(jwt);
        boolean scoped = scopes.contains(SCOPE_READ) || scopes.contains(SCOPE_WRITE);
        boolean operator = isOperator(scopes);
        boolean entitled = TenantClaimValidator.isEntitled(jwt, domainKey);
        if (!scoped && !operator && !entitled) {
            throw new ReadAccessDeniedException(
                    "actor lacks erp.read scope, operator role, or erp entitlement");
        }
    }

    /**
     * Extracts the operator's {@code org_scope} read-narrowing scope (TASK-ERP-BE-008).
     * Mirrors masterdata-service's {@code ActorContextJwtAuthenticationConverter}
     * org_scope parsing + {@code isPlatformScope()} semantics:
     * <ul>
     *   <li>absent / blank {@code org_scope} → {@link OrgScope#platform()} (no
     *       narrowing — net-zero for every BE-007 caller).</li>
     *   <li>{@code org_scope} containing {@code "*"} → {@link OrgScope#platform()}
     *       (platform-wide; the tenant gate already isolates cross-tenant).</li>
     *   <li>a non-{@code "*"} set of subtree-root ids → {@link OrgScope#of(Set)}
     *       (narrow to the union of those subtrees; an explicit empty set is
     *       zero-scope = narrows to nothing).</li>
     * </ul>
     * <p>An <b>explicit</b> empty {@code org_scope=[]} (claim present, no roots)
     * is zero-scope ({@link OrgScope#of(Set)} with an empty set → empty read
     * result / 404 on detail, fail-closed) — distinct from an <b>absent</b>
     * claim (platform / net-zero), matching the edge-case contract.
     */
    public OrgScope orgScope(Jwt jwt) {
        if (jwt == null) {
            return OrgScope.platform();
        }
        boolean claimPresent = jwt.hasClaim("org_scope") || jwt.hasClaim("data_scope");
        // Parse the data-scope token set via the shared canonical reader
        // (ADR-MONO-025; dual-reads org_scope + data_scope). The platform/
        // zero-scope distinction below is the read-model's domain-local
        // interpretation, which AbacDataScope does not carry.
        AbacDataScope scope = AbacDataScope.fromClaimValues(
                jwt.getClaim("org_scope"), jwt.getClaim("data_scope"));
        if (scope.isUnrestricted()) {
            return OrgScope.platform();
        }
        if (scope.isEmpty()) {
            // Absent claim → net-zero platform; explicit present-but-empty
            // (org_scope=[]) → zero-scope (narrows to nothing, fail-closed).
            return claimPresent ? OrgScope.of(Set.of()) : OrgScope.platform();
        }
        return OrgScope.of(scope.tokens());
    }

    private static boolean isOperator(Set<String> roles) {
        return roles.contains("ERP_OPERATOR") || roles.contains("ERP_ADMIN")
                || roles.contains("SUPER_ADMIN");
    }

    /**
     * Lifts roles + scopes from the common claim aliases ({@code roles} /
     * {@code role} / {@code scope} / {@code scopes}), splitting space/comma
     * delimited string claims (OAuth2 {@code scope} is space-delimited).
     */
    private static Set<String> extractScopesAndRoles(Jwt jwt) {
        Set<String> out = new HashSet<>();
        for (String name : new String[]{"roles", "role", "scope", "scopes"}) {
            Object raw = jwt.getClaim(name);
            if (raw == null) {
                continue;
            }
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
        return out;
    }
}
