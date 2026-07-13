package com.example.auth.infrastructure.oauth2;

import java.util.List;

/**
 * TASK-BE-369 (ADR-MONO-033 S3): the aud-default role seed table applied at
 * base-token issuance when an account has no stored {@code account_roles} yet.
 *
 * <p>Pure, framework-free, unit-testable. Keyed on the client-platform per the
 * Design Resolution in the task spec — the platform is the registered client's
 * {@code tenant_id} (ClientSettings {@code custom.tenant_id}).
 *
 * <p>TASK-MONO-263 (ADR-MONO-035 4b-2b / ADR-032 D5 step 4): decoupled from
 * {@code account_type}. The seed now returns the <b>consumer</b> role keyed on
 * platform only ({@code ecommerce → CUSTOMER}, {@code fan-platform → FAN}, else
 * {@code []}). The OPERATOR branch is removed entirely — operators are seeded at
 * assume-tenant by {@link OperatorRoleDerivation} (TASK-BE-376), not at base
 * login. The operator base login (platform {@code gap}/{@code *}) therefore
 * seeds {@code []} (correct — a base operator token has no domain roles to carry).
 *
 * <p>Applied <b>only</b> when the stored set is empty (stored roles, when present,
 * are emitted verbatim — never unioned with the seed). {@code PREMIUM_MEMBER}
 * (needs a fan membership lookup) is out of scope — {@code FAN} only.
 *
 * <p><b>TASK-MONO-381: the caller gates this table, and that gate is the security property.</b>
 * This method still answers "what does platform X seed", but
 * {@link TenantClaimTokenCustomizer} now calls it <b>only when the principal's own tenant IS
 * that platform</b>. Keyed on the client alone — as it was — it handed {@code CUSTOMER} to
 * every principal who authenticated through the storefront client, operators and SUPER_ADMIN
 * included, which made the role guard of ADR-MONO-035 §4b-iii structurally unable to fire.
 * Do not reintroduce a client-only call site.
 *
 * <p>A null/blank input or an unknown platform → an immutable empty list (never
 * null). An empty result means the roles claim is omitted, and the gateway then
 * 403s — the correct least-privilege behaviour.
 */
final class RoleSeedPolicy {

    private RoleSeedPolicy() {
    }

    /**
     * Returns the consumer seed roles for a client-platform.
     *
     * @param platformTenantId the registered client's tenant_id (the platform:
     *                         {@code ecommerce} / {@code wms} / {@code fan-platform}
     *                         / {@code scm} / {@code erp} / {@code mes}); null/blank → {@code []}
     * @return an immutable list of seed role names (possibly empty, never null)
     */
    static List<String> seed(String platformTenantId) {
        if (platformTenantId == null || platformTenantId.isBlank()) {
            return List.of();
        }

        return switch (platformTenantId.trim()) {
            case "ecommerce" -> List.of("CUSTOMER");
            case "fan-platform" -> List.of("FAN");
            default -> List.of();
        };
    }
}
