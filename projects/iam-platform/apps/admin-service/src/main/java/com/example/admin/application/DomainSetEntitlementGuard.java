package com.example.admin.application;

import com.example.admin.application.exception.DomainNotSubscribedException;
import com.example.admin.application.port.TenantEntitledDomainsPort;
import com.example.admin.domain.rbac.DomainRoleCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;

/**
 * TASK-BE-631 (ADR-MONO-083 D1 · admin-api.md § 도메인 업무 권한 세트) — the ONE predicate behind the three places a
 * set meets a tenant's entitlement: tenant-set create, tenant-set edit (when roles are sent) and a grant into a
 * concrete tenant («생성 · 수정 · 배정 세 자리 모두 같은 술어»). A set may never cover a domain outside the tenant's
 * entitled domains (active subscription ∩ org-node ceiling) — a set cannot widen what the tenant is entitled to.
 *
 * <ul>
 *   <li>a domain outside → {@code 422 DOMAIN_NOT_SUBSCRIBED};</li>
 *   <li>the entitlement read fails → the port's {@code DownstreamFailureException} / circuit-open propagates
 *       ({@code 503}) — the write is refused, never allowed (fail-closed). Callers run this BEFORE any write.</li>
 * </ul>
 * Catalog domain {@code fan} is entitled by either subscription key {@code fan} or {@code fan-platform}
 * ({@link DomainRoleCatalog#isEntitled}).
 */
@Component
@RequiredArgsConstructor
public class DomainSetEntitlementGuard {

    private final TenantEntitledDomainsPort entitledDomainsPort;

    /**
     * @param tenantId a concrete tenant (never {@code '*'} — a {@code '*'} grant row has no subscription check)
     * @param domains  the set's domain keys
     */
    public void requireEntitled(String tenantId, Collection<String> domains) {
        Set<String> entitled = entitledDomainsPort.entitledDomains(tenantId);
        for (String domain : domains) {
            if (!DomainRoleCatalog.isEntitled(domain, entitled)) {
                throw new DomainNotSubscribedException(
                        "Domain '" + domain + "' is not among tenant '" + tenantId + "' entitled domains");
            }
        }
    }
}
