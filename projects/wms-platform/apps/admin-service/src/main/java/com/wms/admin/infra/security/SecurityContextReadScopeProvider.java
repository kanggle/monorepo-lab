package com.wms.admin.infra.security;

import com.wms.admin.application.security.ReadScope;
import com.wms.admin.application.security.ReadScopeProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Resolves the {@link ReadScope} from the Spring Security context
 * (TASK-BE-583 / ADR-MONO-065 § D1).
 *
 * <p>The scope comes from the <b>signed</b> {@code tenant_id} claim and from nothing
 * else — there is no tenant query parameter, request header, or body field on any
 * dashboard endpoint, so a caller cannot widen or redirect its own visibility.
 *
 * <p>A caller is unrestricted when:
 * <ul>
 *   <li>there is no authenticated JWT principal — an internal flow or a test without
 *       a security context; or</li>
 *   <li>{@code tenant_id} is the native wms tenant
 *       ({@code wms.oauth2.required-tenant-id}), i.e. the operator plane this
 *       read model was originally built for.</li>
 * </ul>
 * Any other {@code tenant_id} is a customer tenant admitted through the
 * {@code entitled_domains} dual-accept (ADR-MONO-019 § D5) and is restricted to its
 * own rows.
 *
 * <h2>Deliberately the same predicate as outbound-service, including its edges</h2>
 *
 * <p>This mirrors {@code SecurityContextCallerScopeProvider} as amended by
 * ADR-MONO-064 § D3, and the two must not drift — they now guard the same rows
 * through two different surfaces, which is precisely the split that let the defect
 * ADR-MONO-065 closes exist in the first place (the raw REST plane isolated; this
 * projection did not).
 *
 * <ul>
 *   <li><b>No {@code "*"} branch.</b> ADR-MONO-064 § D3 removed it from the outbound
 *       axis and it is not reintroduced here. wms refuses the SUPER_ADMIN wildcard at
 *       admission (ADR-MONO-048 § D5) while such a token can still be admitted on its
 *       {@code entitled_domains}; treating it as unrestricted <em>here</em> would
 *       re-open, on the read side, exactly what D3 closed on the write side. A
 *       {@code "*"} caller is {@code restrictedTo("*")} and no row carries that
 *       string — fail-closed.</li>
 *   <li><b>A blank or absent {@code tenant_id} falls to unrestricted.</b> Unreachable
 *       from the issuer rather than from the gate: {@code TenantClaimTokenCustomizer}
 *       throws on every grant that cannot resolve a tenant, so no minted token lacks
 *       the claim. Recorded so the next reader knows it was looked at — this branch
 *       is what keeps the no-security-context internal flows unrestricted.</li>
 * </ul>
 */
@Component
public class SecurityContextReadScopeProvider implements ReadScopeProvider {

    private static final String CLAIM_TENANT_ID = "tenant_id";

    private final String nativeTenantId;

    public SecurityContextReadScopeProvider(
            @Value("${wms.oauth2.required-tenant-id:wms}") String nativeTenantId) {
        this.nativeTenantId = nativeTenantId;
    }

    @Override
    public ReadScope current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return ReadScope.unrestricted();
        }
        Object raw = jwt.getClaim(CLAIM_TENANT_ID);
        String tenantId = raw instanceof String s ? s : null;
        if (tenantId == null || tenantId.isBlank() || nativeTenantId.equals(tenantId)) {
            return ReadScope.unrestricted();
        }
        return ReadScope.restrictedTo(tenantId);
    }
}
