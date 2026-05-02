package com.example.account.domain.account;

import java.util.regex.Pattern;

/**
 * Validates the {@code role_name} column of {@code account_roles}.
 *
 * <p>TASK-BE-255: Role names are uppercase identifiers with optional digits
 * and underscores (e.g., {@code WAREHOUSE_ADMIN}, {@code INBOUND_OPERATOR}).
 * The regex is enforced in the application layer; the database column is
 * just a {@code VARCHAR(64)} so future relaxation does not require a
 * migration. Tenant-specific role catalogs (a future task) will replace
 * this regex with a positive list of allowed names per tenant.
 */
public final class AccountRoleName {

    /** Maximum length is governed by the database column width. */
    public static final int MAX_LENGTH = 64;

    /** Uppercase letter, then any number of uppercase letters, digits or underscores. */
    private static final Pattern PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]*$");

    private AccountRoleName() {}

    /**
     * Validate {@code roleName} as an account role identifier. Throws
     * {@link IllegalArgumentException} if the value is null, blank, too long,
     * or fails the format regex. Callers should map this to the
     * {@code VALIDATION_ERROR} HTTP error at the controller boundary.
     */
    public static void validate(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            throw new IllegalArgumentException("roleName must not be blank");
        }
        if (roleName.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "roleName must be at most " + MAX_LENGTH + " characters: " + roleName);
        }
        if (!PATTERN.matcher(roleName).matches()) {
            throw new IllegalArgumentException(
                    "roleName must match pattern ^[A-Z][A-Z0-9_]*$: " + roleName);
        }
    }
}
