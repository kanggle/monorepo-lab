package com.example.erp.notification.presentation.security;

import com.example.security.oauth2.TenantClaimValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * READ authorization gate (E6, fail-closed) — the notification-service inbox
 * equivalent of masterdata / read-model's READ verdict: READ = {@code erp.read}
 * scope ∨ {@code isOperator()} ∨ entitled ({@code entitled_domains ∋ erp}). The
 * platform-console operator token that already reads read-model satisfies this
 * gate (same READ semantics).
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

    private final String domainKey;

    public ReadAuthorizationGate(
            @Value("${erpplatform.oauth2.required-tenant-id:erp}") String requiredTenantId) {
        this.domainKey = requiredTenantId == null || requiredTenantId.isBlank()
                ? DOMAIN_KEY : requiredTenantId;
    }

    /**
     * Enforces the READ gate; throws {@link ReadAccessDeniedException} (→ 403
     * {@code PERMISSION_DENIED}) when the caller is neither scoped, an operator,
     * nor entitled. A {@code null} JWT is denied (defense-in-depth).
     */
    public void requireRead(Jwt jwt) {
        if (jwt == null) {
            throw new ReadAccessDeniedException("no authenticated token");
        }
        Set<String> scopes = extractScopesAndRoles(jwt);
        boolean scoped = scopes.contains(SCOPE_READ) || scopes.contains(SCOPE_WRITE);
        boolean operator = isOperator(scopes);
        boolean entitled = TenantClaimValidator.isEntitled(jwt, domainKey);
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
        if (!scoped && !operator && !entitled && !superAdminWildcard) {
            throw new ReadAccessDeniedException(
                    "actor lacks erp.read scope, operator role, or erp entitlement");
        }
    }

    private static boolean isOperator(Set<String> roles) {
        return roles.contains("ERP_OPERATOR") || roles.contains("ERP_ADMIN")
                || roles.contains("SUPER_ADMIN");
    }

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
