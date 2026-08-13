package com.wms.outbound.adapter.out.security;

import com.wms.outbound.application.port.out.CallerScopeProvider;
import com.wms.outbound.application.security.CallerScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Resolves the {@link CallerScope} from the Spring Security context
 * (TASK-MONO-304 / ADR-MONO-022 § D9, amended by ADR-MONO-064 § D3).
 *
 * <p>Scoping is derived from the SIGNED {@code tenant_id} claim — never a
 * client-supplied header (which would be spoofable). A caller is unrestricted
 * (sees every order) when:
 * <ul>
 *   <li>there is no authenticated JWT principal (internal Kafka-consumer /
 *       scheduler flow, or a test without a security context), or</li>
 *   <li>{@code tenant_id} equals the native wms tenant
 *       ({@code wms.oauth2.required-tenant-id}).</li>
 * </ul>
 * Any other (customer) {@code tenant_id} — admitted to wms via the
 * {@code entitled_domains} dual-accept ({@link com.example.security.oauth2.TenantClaimValidator})
 * — is restricted to that tenant's orders.
 *
 * <h2>The platform wildcard {@code "*"} is NOT a third unrestricted branch (§ D3)</h2>
 *
 * <p>It used to be, and that made this class disagree with the edge it sits behind.
 * wms is the <strong>only</strong> platform that refuses the SUPER_ADMIN {@code "*"}
 * wildcard at admission, deliberately and by decision (ADR-MONO-048 § D5;
 * {@code OAuth2ResourceServerConfig} does not call {@code allowSuperAdminWildcard()}
 * and {@code WmsTenantGatePolicyTest.TheWildcardIsRefused} asserts the refusal). The
 * scoping axis then read the same claim the opposite way and handed that identity
 * <em>every tenant's orders</em>.
 *
 * <p>The contradiction was not academic. A {@code "*"} token can still be
 * <em>admitted</em> — not on the wildcard, but on its {@code entitled_domains}
 * ({@code WmsTenantGatePolicyTest.wildcardTokenIsAdmittedOnlyViaEntitlement} pins
 * exactly that) — and it would land here. What kept it closed was that no tenant
 * named {@code "*"} has a domain subscription, so no such token carries the
 * entitlement claim: <b>data, not design</b>. One subscription row would have opened
 * it silently.
 *
 * <p>ADR-MONO-048 § D5 recorded "can a platform operator reach the wms edge during an
 * incident?" as a question for a future decision. ADR-MONO-064 § D3 settles it on the
 * <em>refusal</em> side and leaves the edge untouched: a {@code "*"} caller reaching
 * here is now {@code restrictedTo("*")}, and no order carries that string as its
 * tenant, so it sees nothing. Fail-closed.
 *
 * <p><b>The sibling shape, measured and deliberately not changed.</b> A JWT with no
 * {@code tenant_id} claim at all is also admitted on entitlement alone
 * ({@code WmsTenantGatePolicyTest.entitlementAloneAdmits}) and still falls to
 * unrestricted below. It is unreachable from the issuer rather than from the gate:
 * {@code TenantClaimTokenCustomizer} throws on every grant that cannot resolve a
 * tenant, so no minted token lacks the claim. Recorded here so the next reader knows
 * it was looked at and why it was left — the null branch is what serves the internal
 * flows this class exists to keep unrestricted.
 */
@Component
public class SecurityContextCallerScopeProvider implements CallerScopeProvider {

    private static final String CLAIM_TENANT_ID = "tenant_id";

    private final String requiredTenantId;

    public SecurityContextCallerScopeProvider(
            @Value("${wms.oauth2.required-tenant-id:wms}") String requiredTenantId) {
        this.requiredTenantId = requiredTenantId;
    }

    @Override
    public CallerScope current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return CallerScope.unrestricted();
        }
        Object raw = jwt.getClaim(CLAIM_TENANT_ID);
        String tenantId = raw instanceof String s ? s : null;
        if (tenantId == null
                || tenantId.isBlank()
                || requiredTenantId.equals(tenantId)) {
            return CallerScope.unrestricted();
        }
        return CallerScope.restrictedTo(tenantId);
    }
}
