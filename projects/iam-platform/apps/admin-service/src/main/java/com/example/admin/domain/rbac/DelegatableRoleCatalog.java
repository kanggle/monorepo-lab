package com.example.admin.domain.rbac;

import java.util.List;
import java.util.Set;

/**
 * TASK-BE-479 / ADR-MONO-045 D3 — the allowlist of <b>delegatable operator-tier
 * domain roles</b> a host tenant may place in a partnership {@code delegatedScope}
 * (the role dimension of the invite-time ≤-own cap).
 *
 * <p>This is the flattened value set of the auth-service
 * {@code OperatorRoleDerivation} table (the operator roles derived per entitled
 * domain at assume-tenant issuance) — i.e. exactly the domain-operating roles any
 * operator of a tenant already obtains for the domains that tenant holds. A host may
 * therefore delegate only these, never:
 * <ul>
 *   <li>the tenant-admin roles ({@link ScopeSet#ADMIN_ROLE_NAMES} — also rejected by
 *       {@link ScopeSet#containsAdminRole()}), and</li>
 *   <li>admin-tier <i>domain</i> roles ({@code *_ADMIN}, e.g. {@code WMS_ADMIN}) that
 *       {@code OperatorRoleDerivation} deliberately withholds from the operator
 *       entitlement — these are NOT delegatable across the org boundary.</li>
 * </ul>
 *
 * <p><b>TASK-BE-631 (ADR-MONO-083 D2) — one source.</b> The allow-list is no longer a hand-kept mirror: it is
 * the role-name set of {@link DomainRoleCatalog}, the single admin-side catalog of assignable domain work roles
 * (whose content is the same flattened {@code OperatorRoleDerivation} value set this class used to copy).
 * {@code DelegatableRoleCatalogTest} pins the derived set byte-identical to the list it replaced, so a catalog
 * change that would silently widen or narrow what a partnership may delegate turns that test red first.
 * A drift is still <b>fail-CLOSED</b> (a role missing from the catalog is rejected at invite, never an over-grant).
 *
 * <p>Framework-free (no Spring/JPA) — a pure domain policy, like {@link ScopeSet}.
 */
public final class DelegatableRoleCatalog {

    /**
     * The operator-tier domain roles delegatable across an org boundary — {@link DomainRoleCatalog#roleNames()}:
     * <ul>
     *   <li>{@code wms} → WMS_OPERATOR + granular OUTBOUND/INBOUND/INVENTORY
     *       {READ,WRITE} + MASTER_READ (TASK-BE-433; {@code WMS_ADMIN} excluded)</li>
     *   <li>{@code ecommerce} → ECOMMERCE_OPERATOR (the ecommerce operator role)</li>
     *   <li>{@code scm} → the eight SCM business roles (TASK-MONO-792) · {@code erp} → the four ERP business
     *       roles (TASK-MONO-793) — their entry roles SCM_OPERATOR / ERP_OPERATOR are derived by auth, never
     *       delegated · {@code finance} → FINANCE_OPERATOR · {@code mes} → MES_OPERATOR ·
     *       {@code fan}/{@code fan-platform} → FAN_OPERATOR</li>
     * </ul>
     */
    public static final Set<String> DELEGATABLE_OPERATOR_ROLES = Set.copyOf(DomainRoleCatalog.roleNames());

    private DelegatableRoleCatalog() {
    }

    /** @return {@code true} iff {@code role} is a delegatable operator-tier role. */
    public static boolean isDelegatable(String role) {
        return role != null && DELEGATABLE_OPERATOR_ROLES.contains(role);
    }

    /**
     * @return the first role in {@code roles} that is NOT delegatable, or
     *         {@code null} if every role is delegatable (or the list is empty). Used
     *         to build a precise 422 message naming the offending role.
     */
    public static String firstNonDelegatable(List<String> roles) {
        if (roles == null) {
            return null;
        }
        for (String role : roles) {
            if (!isDelegatable(role)) {
                return role;
            }
        }
        return null;
    }
}
