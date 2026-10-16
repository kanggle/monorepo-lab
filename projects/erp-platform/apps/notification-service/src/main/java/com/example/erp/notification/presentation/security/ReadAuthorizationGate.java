package com.example.erp.notification.presentation.security;

import com.example.security.oauth2.TenantClaimValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * READ authorization gate (E6, fail-closed) for the notification-service inbox. Since TASK-MONO-793 the
 * inbox is a personal endpoint — see {@link #requireRead(Jwt)}.
 *
 * <p>The mark-read set is a self-scoped READ-adjacent write (the recipient
 * clears their own receipt); it requires the same READ gate + recipient
 * ownership, not a separate WRITE scope (there is no org-wide notification
 * mutation). Recipient-scoping is enforced separately in the use cases
 * ({@code recipient == JWT.sub}).
 */
@Component
public class ReadAuthorizationGate {

    private static final String SCOPE_READ = "erp.read";
    private static final String SCOPE_WRITE = "erp.write";
    private static final String DOMAIN_KEY = "erp";

    /** The erp domain key — keyed the removed entitlement clause; no verdict reads it since TASK-MONO-793. */
    private final String domainKey;

    /** @return the configured erp domain key (diagnostics only since TASK-MONO-793). */
    String domainKey() {
        return domainKey;
    }

    public ReadAuthorizationGate(
            @Value("${erpplatform.oauth2.required-tenant-id:erp}") String requiredTenantId) {
        this.domainKey = requiredTenantId == null || requiredTenantId.isBlank()
                ? DOMAIN_KEY : requiredTenantId;
    }

    /**
     * TASK-MONO-793 (ADR-MONO-083 P6 · owner decisions Q2 · Q6; erp iam-integration.md § 업무 역할) — the
     * inbox is a PERSONAL endpoint: any ERP business role or the derived entry role {@code ERP_OPERATOR}, an
     * erp scope, or tenant {@code '*'}. Throws {@link ReadAccessDeniedException} (→ 403
     * {@code PERMISSION_DENIED}); a {@code null} JWT is denied (defense-in-depth).
     *
     * <p>Removed: {@code entitled_domains ∋ erp} — every assume token carries the tenant's subscriptions, so
     * it opened the inbox to people holding no ERP role at all (Q1) — and {@code ERP_ADMIN} · {@code SUPER_ADMIN}
     * (issued nowhere). The tenant gate's dual-accept is untouched.
     */
    public void requireRead(Jwt jwt) {
        if (jwt == null) {
            throw new ReadAccessDeniedException("no authenticated token");
        }
        Set<String> scopes = extractScopesAndRoles(jwt);
        boolean scoped = scopes.contains(SCOPE_READ) || scopes.contains(SCOPE_WRITE);
        boolean operator = ERP_ROLES.stream().anyMatch(scopes::contains);
        // READ-only platform-super-admin wildcard admission (TASK-ERP-BE-031): the erp
        // authority-layer analogue of the tenant gate's allowSuperAdminWildcard(),
        // mirroring finance FIN-BE-049. A platform-console super-admin forwards its base
        // OIDC domain token — tenant_id='*', NO erp scope, NO domain roles,
        // entitled_domains=[] — which passes layer-1 but matches none of the clauses
        // above. Per ADR-033 S2 / ADR-034 U5 the admin-plane SUPER_ADMIN role is kept OFF
        // the domain token, so isOperator()'s SUPER_ADMIN branch is dead for the real
        // super-admin persona — the wildcard tenant_id is the correct key. This gate
        // guards READ only; the sole POST (mark-read) is a self-scoped, recipient-owned
        // READ-adjacent action (there is no org-wide notification mutation), so admitting
        // the wildcard here never widens a mutation.
        boolean superAdminWildcard = TenantClaimValidator.WILDCARD_TENANT
                .equals(jwt.getClaimAsString(TenantClaimValidator.CLAIM_TENANT_ID));
        if (!scoped && !operator && !superAdminWildcard) {
            throw new ReadAccessDeniedException("actor holds no ERP role and no erp scope");
        }
    }

    /** The derived entry role + the four ERP business roles (TASK-MONO-793 — any one opens the inbox). */
    static final java.util.List<String> ERP_ROLES = java.util.List.of("ERP_OPERATOR",
            "ERP_MASTERDATA_READ", "ERP_MASTERDATA_WRITE", "ERP_APPROVAL_READ", "ERP_APPROVAL_WRITE");

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
