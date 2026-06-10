package com.wms.master.adapter.in.web.support;

import com.example.security.jwt.AbacDataScope;
import java.util.Set;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * ADR-MONO-025 controller-side helper for resolving an operator's ABAC
 * data-scope (warehouse codes) from the request JWT. Shared by the zone and
 * location controllers; the warehouse controller keeps its own equivalent for
 * its target-decision form ({@code AbacDataScope.allows}).
 *
 * <p>Pure claim read — no persistence. The returned set is the operator's
 * deliberately-scoped warehouse codes, or {@code null} when the operator is
 * unrestricted ({@code "*"}) or unscoped (empty/absent — base authorization_code
 * and machine tokens carry no scope; the assume-tenant producer emits
 * {@code ["*"]} for unscoped assignments). {@code null} therefore means the
 * net-zero path: callers apply no confinement.
 */
public final class DataScopeSupport {

    private DataScopeSupport() {
    }

    /**
     * @param jwt the authenticated principal (may be {@code null} for an
     *            unauthenticated request — the resource server rejects those
     *            earlier, so this is defensive)
     * @return the deliberately-scoped warehouse codes, or {@code null} for the
     *         net-zero (unrestricted / unscoped) path
     */
    public static Set<String> warehouseScopeCodes(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        AbacDataScope scope = AbacDataScope.fromClaimValues(
                jwt.getClaim(AbacDataScope.CLAIM_DATA_SCOPE),
                jwt.getClaim(AbacDataScope.CLAIM_ORG_SCOPE));
        boolean restricted = !scope.isEmpty() && !scope.isUnrestricted();
        return restricted ? scope.tokens() : null;
    }
}
