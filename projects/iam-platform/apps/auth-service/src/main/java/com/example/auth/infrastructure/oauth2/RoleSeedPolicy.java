package com.example.auth.infrastructure.oauth2;

import java.util.List;

/**
 * TASK-BE-369 (ADR-MONO-033 S3): the aud-default role seed table applied at
 * base-token issuance when an account has no stored {@code account_roles} yet.
 *
 * <p>Pure, framework-free, unit-testable. Keyed on (client-platform, account_type)
 * per the Design Resolution in the task spec — the platform is the registered
 * client's {@code tenant_id} (ClientSettings {@code custom.tenant_id}), the surface
 * is {@code account_type} (CONSUMER|OPERATOR) from the principal details map.
 *
 * <p>Applied <b>only</b> when the stored set is empty (stored roles, when present,
 * are emitted verbatim — never unioned with the seed). {@code PREMIUM_MEMBER}
 * (needs a fan membership lookup) is out of scope — {@code FAN} only.
 *
 * <p>Null/blank inputs or an unknown (platform, account_type) pair → an immutable
 * empty list (never null). An empty result means the roles claim is omitted, and
 * the gateway then 403s — the correct least-privilege behaviour.
 */
final class RoleSeedPolicy {

    private static final String CONSUMER = "CONSUMER";
    private static final String OPERATOR = "OPERATOR";

    private RoleSeedPolicy() {
    }

    /**
     * Returns the seed roles for a (client-platform, account_type) pair.
     *
     * @param platformTenantId the registered client's tenant_id (the platform:
     *                         {@code ecommerce} / {@code wms} / {@code fan-platform}
     *                         / {@code scm} / {@code erp} / {@code mes}); null/blank → {@code []}
     * @param accountType      the account surface ({@code CONSUMER} | {@code OPERATOR});
     *                         null/blank → {@code []}
     * @return an immutable list of seed role names (possibly empty, never null)
     */
    static List<String> seed(String platformTenantId, String accountType) {
        if (platformTenantId == null || platformTenantId.isBlank()
                || accountType == null || accountType.isBlank()) {
            return List.of();
        }

        String platform = platformTenantId.trim();
        String type = accountType.trim();

        if (CONSUMER.equals(type)) {
            return switch (platform) {
                case "ecommerce" -> List.of("CUSTOMER");
                case "fan-platform" -> List.of("FAN");
                default -> List.of();
            };
        }

        if (OPERATOR.equals(type)) {
            return switch (platform) {
                case "ecommerce" -> List.of("ADMIN");
                case "wms" -> List.of("WMS_OPERATOR");
                case "scm" -> List.of("SCM_OPERATOR");
                case "erp" -> List.of("ERP_OPERATOR");
                case "mes" -> List.of("MES_OPERATOR");
                case "fan-platform" -> List.of("FAN");
                default -> List.of();
            };
        }

        return List.of();
    }
}
