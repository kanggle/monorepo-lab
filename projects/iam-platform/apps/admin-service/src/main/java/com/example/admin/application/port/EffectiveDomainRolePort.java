package com.example.admin.application.port;

import java.util.Collection;
import java.util.List;

/**
 * TASK-BE-631 (auth-to-admin.md § check rule 7 · ADR-MONO-083 D6) — the read behind {@code domainRoles}: the roles of
 * every set an operator holds in the given tenants, every source (DIRECT · MIGRATION · GROUP:&lt;id&gt;). A separate,
 * one-method port so the assume check depends on exactly this read and nothing else of
 * {@link DomainPermissionSetPort}.
 *
 * <p>No catalog filter and no subscription filter here — that is auth-service's (rule 7). A read failure propagates
 * as an exception; it is never answered with an empty list.
 */
@FunctionalInterface
public interface EffectiveDomainRolePort {

    /**
     * @param operatorInternalId {@code admin_operators.id}
     * @param tenantIds          the {@code operator_domain_set_grant.tenant_id} values to read — {@code {T}} on the
     *                           membership path, {@code {T, '*'}} on the {@code '*'} path
     * @return the role names, distinct, in catalog order (a role no longer in the catalog sorts last); empty when
     *         the operator holds no set there
     */
    List<String> effectiveRoleNames(long operatorInternalId, Collection<String> tenantIds);
}
