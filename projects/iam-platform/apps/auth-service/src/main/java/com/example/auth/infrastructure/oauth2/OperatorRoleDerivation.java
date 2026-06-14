package com.example.auth.infrastructure.oauth2;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * TASK-BE-376 (ADR-MONO-035 O1 / step 4a): the operator-role derivation table
 * applied at <b>assume-tenant</b> issuance — the operator-role mirror of
 * {@link RoleSeedPolicy}, keyed on the SELECTED tenant's ACTIVE entitled domains
 * (not on a client-platform/account_type pair).
 *
 * <p>Pure, framework-free, unit-testable. The operator (already fail-closed-verified
 * as assigned to the selected tenant) is granted the operator role for everything
 * that tenant is entitled to, so the domain gateway's {@code roles} leg admits them.
 * This replaces TASK-BE-370's preserve-from-base for the operator case — the base
 * operator token ({@code aud=platform-console}, {@code tenant_id='gap'}) structurally
 * has no domain-role set to preserve.
 *
 * <p>De-duplicated, stable order (input-domain order preserved via
 * {@link LinkedHashSet}), immutable result, never null. Null/blank/unknown domain
 * keys are skipped; {@code gap} maps to no operator role (it is the IdP platform,
 * not a domain). An empty result means the {@code roles} claim is omitted, and the
 * gateway then 403s — the correct least-privilege behaviour.
 */
final class OperatorRoleDerivation {

    private OperatorRoleDerivation() {
    }

    /**
     * Derives the ordered, de-duplicated operator-role list for a set of entitled
     * domain keys.
     *
     * @param domainKeys the SELECTED tenant's ACTIVE entitled domain keys
     *                   ({@code wms} / {@code ecommerce} / {@code scm} / {@code erp}
     *                   / {@code finance} / {@code mes} / {@code fan} /
     *                   {@code fan-platform}); {@code gap}/unknown → no role.
     *                   Null/empty → {@code []}.
     * @return an immutable list of operator role names (possibly empty, never null),
     *         in stable input order with duplicates removed
     */
    static List<String> fromEntitledDomains(List<String> domainKeys) {
        if (domainKeys == null || domainKeys.isEmpty()) {
            return List.of();
        }

        Set<String> roles = new LinkedHashSet<>();
        for (String raw : domainKeys) {
            if (raw == null) {
                continue;
            }
            String key = raw.trim();
            if (key.isBlank()) {
                continue;
            }
            String role = switch (key) {
                case "wms" -> "WMS_OPERATOR";
                case "ecommerce" -> "ADMIN";
                case "scm" -> "SCM_OPERATOR";
                case "erp" -> "ERP_OPERATOR";
                case "finance" -> "FINANCE_OPERATOR";
                case "mes" -> "MES_OPERATOR";
                case "fan", "fan-platform" -> "FAN_OPERATOR";
                default -> null; // gap / unknown → no operator role
            };
            if (role != null) {
                roles.add(role);
            }
        }

        return List.copyOf(roles);
    }
}
