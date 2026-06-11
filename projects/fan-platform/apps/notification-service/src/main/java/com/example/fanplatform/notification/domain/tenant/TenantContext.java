package com.example.fanplatform.notification.domain.tenant;

/**
 * Tenant claim constants shared by the security layer + the inbox query scope.
 * Mirrors the membership / community convention.
 */
public final class TenantContext {

    public static final String CLAIM_TENANT_ID = "tenant_id";
    public static final String DEFAULT_TENANT_ID = "fan-platform";
    public static final String WILDCARD_TENANT = "*";

    private TenantContext() {
    }
}
