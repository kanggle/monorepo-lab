package com.example.auth.application.result;

/**
 * OIDC-oriented profile result from account-service.
 *
 * <p>Used by {@link com.example.auth.infrastructure.oauth2.OidcUserInfoMapper}
 * to build the standard OIDC {@code /oauth2/userinfo} payload. All fields
 * are nullable except {@code accountId} — if a field is absent in the
 * account-service response it is simply omitted from the userinfo claim set.
 *
 * <p>TASK-BE-251 Phase 2a.</p>
 *
 * @param accountId         the account's unique identifier (maps to OIDC {@code sub})
 * @param email             the primary email address (maps to OIDC {@code email})
 * @param emailVerified     whether the email has been verified (maps to OIDC {@code email_verified})
 * @param displayName       the human-readable name (maps to OIDC {@code name})
 * @param preferredUsername the preferred username / handle (maps to OIDC {@code preferred_username})
 * @param locale            BCP-47 locale string, e.g. {@code "ko-KR"} (maps to OIDC {@code locale})
 * @param tenantId          the tenant the account belongs to (custom claim {@code tenant_id})
 * @param tenantType        the tenant type, e.g. {@code "B2C"} (custom claim {@code tenant_type})
 */
public record AccountProfileResult(
        String accountId,
        String email,
        Boolean emailVerified,
        String displayName,
        String preferredUsername,
        String locale,
        String tenantId,
        String tenantType
) {
}
