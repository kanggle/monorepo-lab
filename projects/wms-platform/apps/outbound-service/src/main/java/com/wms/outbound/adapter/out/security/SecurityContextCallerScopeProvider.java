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
 * (TASK-MONO-304 / ADR-MONO-022 § D9).
 *
 * <p>Scoping is derived from the SIGNED {@code tenant_id} claim — never a
 * client-supplied header (which would be spoofable). A caller is unrestricted
 * (sees every order) when:
 * <ul>
 *   <li>there is no authenticated JWT principal (internal Kafka-consumer /
 *       scheduler flow, or a test without a security context), or</li>
 *   <li>{@code tenant_id} equals the native wms tenant
 *       ({@code wms.oauth2.required-tenant-id}), or</li>
 *   <li>{@code tenant_id} is the platform wildcard {@code "*"}.</li>
 * </ul>
 * Any other (customer) {@code tenant_id} — admitted to wms via the
 * {@code entitled_domains} dual-accept ({@link com.example.security.oauth2.TenantClaimValidator})
 * — is restricted to that tenant's ecommerce-fulfilment orders.
 */
@Component
public class SecurityContextCallerScopeProvider implements CallerScopeProvider {

    private static final String CLAIM_TENANT_ID = "tenant_id";
    private static final String PLATFORM_WILDCARD = "*";

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
                || requiredTenantId.equals(tenantId)
                || PLATFORM_WILDCARD.equals(tenantId)) {
            return CallerScope.unrestricted();
        }
        return CallerScope.restrictedTo(tenantId);
    }
}
