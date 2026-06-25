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

    /**
     * Operator-tier roles granted by the {@code wms} domain entitlement (TASK-BE-433).
     * {@code WMS_OPERATOR} is the coarse domain role; the rest are the granular
     * service roles outbound/inbound/inventory authorize POST/PUT/PATCH/DELETE on
     * ({@code *_WRITE}) plus read ({@code *_READ}) and {@code MASTER_READ}. ADMIN-tier
     * roles are deliberately excluded (see the switch comment).
     */
    private static final List<String> WMS_OPERATOR_ROLES = List.of(
            "WMS_OPERATOR",
            "OUTBOUND_READ", "OUTBOUND_WRITE",
            "INBOUND_READ", "INBOUND_WRITE",
            "INVENTORY_READ", "INVENTORY_WRITE",
            "MASTER_READ");

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
            List<String> domainRoles = switch (key) {
                // TASK-BE-433: the wms entitlement grants the operator the granular
                // wms-service operator-tier roles the services actually authorize on
                // (OUTBOUND/INBOUND/INVENTORY {READ,WRITE} + MASTER_READ), alongside
                // the coarse WMS_OPERATOR. Without these the assume-tenant token carried
                // only WMS_OPERATOR while outbound/inbound/inventory require their own
                // *_WRITE roles → every wms-service write 403'd. ADMIN-tier roles
                // (*_ADMIN / WMS_ADMIN — cancellation, force-saga-fail, master-data
                // writes) are intentionally NOT granted by the operator entitlement.
                case "wms" -> WMS_OPERATOR_ROLES;
                case "ecommerce" -> List.of("ADMIN");
                case "scm" -> List.of("SCM_OPERATOR");
                case "erp" -> List.of("ERP_OPERATOR");
                case "finance" -> List.of("FINANCE_OPERATOR");
                case "mes" -> List.of("MES_OPERATOR");
                case "fan", "fan-platform" -> List.of("FAN_OPERATOR");
                default -> List.of(); // gap / unknown → no operator role
            };
            roles.addAll(domainRoles);
        }

        return List.copyOf(roles);
    }
}
