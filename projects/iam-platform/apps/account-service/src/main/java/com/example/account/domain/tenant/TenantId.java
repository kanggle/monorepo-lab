package com.example.account.domain.tenant;

import java.util.regex.Pattern;

/**
 * Value object for tenant identifier.
 *
 * <p>A tenant_id is a lowercase slug that uniquely identifies a tenant across the platform.
 * The format follows the pattern {@code ^[a-z][a-z0-9-]{1,31}$}:
 * - starts with a lowercase letter
 * - followed by 1–31 characters of lowercase letters, digits, or hyphens
 * - total length: 2–32 characters
 *
 * <p>Examples: {@code fan-platform}, {@code wms}, {@code erp}, {@code scm}
 *
 * <p>Once issued, a tenant_id is immutable — it cannot be changed or reassigned
 * (audit trail and external token integrity).
 */
public record TenantId(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[a-z][a-z0-9-]{1,31}$");

    /**
     * Default tenant for the B2C fan platform product. Used as the
     * compile-time constant while dynamic injection (TASK-BE-229) is pending.
     */
    public static final TenantId FAN_PLATFORM = new TenantId("fan-platform");

    public TenantId {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid tenant_id: " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
