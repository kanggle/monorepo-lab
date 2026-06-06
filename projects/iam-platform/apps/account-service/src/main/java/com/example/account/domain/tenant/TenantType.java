package com.example.account.domain.tenant;

/**
 * Discriminates the tenant's consumer class.
 *
 * <p>{@code B2C_CONSUMER} — public-facing product (e.g., fan-platform).
 * <p>{@code B2B_ENTERPRISE} — internal enterprise system (e.g., wms, erp, scm).
 *
 * <p>The type influences default role sets and isolation policy decisions
 * (see specs/features/multi-tenancy.md § Per-Tenant Roles).
 */
public enum TenantType {
    B2C_CONSUMER,
    B2B_ENTERPRISE
}
