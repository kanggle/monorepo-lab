package com.example.admin.application.tenant;

/**
 * TASK-BE-250: Local copy of the tenant type enum for admin-service validation.
 * Mirrors {@code account-service}'s {@code TenantType} without creating a cross-service
 * domain dependency. Keep in sync if new types are added.
 */
public enum TenantTypeEnum {
    B2C_CONSUMER,
    B2B_ENTERPRISE
}
