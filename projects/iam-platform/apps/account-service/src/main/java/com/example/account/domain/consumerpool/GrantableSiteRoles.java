package com.example.account.domain.consumerpool;

import com.example.account.domain.tenant.TenantId;

import java.util.Map;
import java.util.Set;

/**
 * TASK-MONO-752 (ADR-MONO-079 D5; consumer-site-roles.md § Grantable roles) — the CLOSED list of
 * {@code (site, role)} pairs the internal site-role endpoints may write or remove.
 *
 * <p>🔴 Why closed: a consumer site role lands in that site's consumer token ({@code seed ∪ site roles},
 * TASK-BE-615), and the store gateway admits operator paths by role name. An open role name would let any
 * caller holding a token for the site mint {@code ECOMMERCE_OPERATOR} (or anything else) onto a shopper.
 * Adding a pair is a decision recorded in the contract, not configuration.
 *
 * <p>Seed roles ({@code CUSTOMER}, {@code FAN}) are not here: they are computed at issuance and never stored.
 */
public final class GrantableSiteRoles {

    /** The store seller-member role (ADR-MONO-079 D5). */
    public static final String SELLER = "SELLER";

    private static final Map<String, Set<String>> GRANTABLE = Map.of(
            "ecommerce", Set.of(SELLER));

    private GrantableSiteRoles() {
    }

    public static boolean isGrantable(TenantId site, String roleName) {
        if (site == null || roleName == null) {
            return false;
        }
        return GRANTABLE.getOrDefault(site.value(), Set.of()).contains(roleName);
    }
}
