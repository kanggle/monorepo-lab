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
 * <p><b>Keep in sync</b> with {@code auth-service}
 * {@code com.example.auth.infrastructure.oauth2.OperatorRoleDerivation}: whenever a
 * new domain's operator roles are added there, add them here too. A drift is
 * <b>fail-CLOSED</b> (a legitimately-delegatable role missing here is rejected at
 * invite, never an over-grant). Extracting the table to a shared library is a
 * deferred follow-up; until then this mirror is the single admin-side source.
 *
 * <p>Framework-free (no Spring/JPA) — a pure domain policy, like {@link ScopeSet}.
 */
public final class DelegatableRoleCatalog {

    /**
     * The operator-tier domain roles delegatable across an org boundary — the
     * flattened value set of auth-service {@code OperatorRoleDerivation}:
     * <ul>
     *   <li>{@code wms} → WMS_OPERATOR + granular OUTBOUND/INBOUND/INVENTORY
     *       {READ,WRITE} + MASTER_READ (TASK-BE-433; {@code WMS_ADMIN} excluded)</li>
     *   <li>{@code ecommerce} → ECOMMERCE_OPERATOR (the ecommerce operator role)</li>
     *   <li>{@code scm} → SCM_OPERATOR · {@code erp} → ERP_OPERATOR ·
     *       {@code finance} → FINANCE_OPERATOR · {@code mes} → MES_OPERATOR ·
     *       {@code fan}/{@code fan-platform} → FAN_OPERATOR</li>
     * </ul>
     */
    public static final Set<String> DELEGATABLE_OPERATOR_ROLES = Set.of(
            // wms (TASK-BE-433 granular operator-tier set)
            "WMS_OPERATOR",
            "OUTBOUND_READ", "OUTBOUND_WRITE",
            "INBOUND_READ", "INBOUND_WRITE",
            "INVENTORY_READ", "INVENTORY_WRITE",
            "MASTER_READ",
            // ecommerce operator role
            "ECOMMERCE_OPERATOR",
            // single-role domains
            "SCM_OPERATOR",
            "ERP_OPERATOR",
            "FINANCE_OPERATOR",
            "MES_OPERATOR",
            "FAN_OPERATOR");

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
