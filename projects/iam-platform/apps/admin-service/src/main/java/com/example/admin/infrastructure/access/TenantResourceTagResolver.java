package com.example.admin.infrastructure.access;

import com.example.admin.infrastructure.persistence.access.AdminResourceTagJpaRepository;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * TASK-BE-355 (ADR-MONO-029) — resolves a tenant's governance tags for the
 * RESOURCE_TAG access condition. Applicable to the tenant-update mutation
 * {@code PATCH /api/admin/tenants/{tenantId}}; the collection-create
 * {@code POST /api/admin/tenants} (no id) is not matched (skipped). Tags come from
 * the trusted admin-local {@code admin_resource_tags} table (type {@code TENANT}).
 */
@Component
public class TenantResourceTagResolver extends AbstractLocalResourceTagResolver {

    private static final Pattern TENANT_MUTATION =
            Pattern.compile("^/api/admin/tenants/([^/]+)$");

    public TenantResourceTagResolver(AdminResourceTagJpaRepository repository) {
        super(repository, "TENANT", TENANT_MUTATION);
    }
}
