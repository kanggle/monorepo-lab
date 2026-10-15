package com.example.auth.infrastructure.oauth2;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
 *
 * <p>🔴 <b>TASK-BE-632 (ADR-MONO-083 D3 · D6) — this table is now a CEILING, not a grant.</b> The
 * assume-tenant customizer no longer gives an operator everything this returns; it intersects the
 * operator's ASSIGNED business roles (admin-service {@code domainRoles}) with it. What this method
 * still decides is which roles a subscription can make reachable at all. It mirrors admin-service
 * {@code DomainRoleCatalog} (the producer of the roles an operator can be assigned) — a role the
 * catalog offers but this table omits can never reach a token, and vice versa a role only here is
 * never assignable. The two must move together (P5–P8 widen both).
 */
final class OperatorRoleDerivation {

    /**
     * Operator-tier roles granted by the {@code wms} domain entitlement (TASK-BE-433).
     * {@code WMS_OPERATOR} is the coarse domain role; the rest are the granular
     * service roles outbound/inbound/inventory authorize POST/PUT/PATCH/DELETE on
     * ({@code *_WRITE}) plus read ({@code *_READ}) and {@code MASTER_READ}. ADMIN-tier
     * roles are deliberately excluded (see the switch comment).
     *
     * <p><b>{@code MASTER_WRITE} is excluded and that is not an oversight</b>, but the
     * old wording for it was wrong, so read this before "fixing" the asymmetry
     * (TASK-MONO-514 AC-4). TASK-BE-433 grouped master-data writes with the ADMIN
     * tier. {@code master-service} does not: per
     * {@code wms-platform/specs/contracts/http/master-service-api.md § roles},
     * create/update are {@code MASTER_WRITE} and only deactivate/reactivate are
     * {@code MASTER_ADMIN}. So the exclusion is a deliberate decision to keep
     * master-data writes out of the operator tier — it is NOT an instance of the
     * "exclude ADMIN-tier" rule, and calling it one made the omission read like a
     * typo. It is asserted by {@code OperatorRoleDerivationTest#wms_excludesAdminTier}
     * and by TASK-BE-433 AC-2; reversing it is a role-model decision, not a parity fix.
     *
     * <p>Consequence, measured: an operator holding this set gets {@code 403 FORBIDDEN}
     * on {@code POST /api/v1/master/warehouses}, and no other credential in the
     * platform carries {@code MASTER_WRITE} either — demo master data therefore
     * arrives via Flyway seed, not via the API. {@code TASK-MONO-514} owns the
     * decision about whether that should change.
     */
    private static final List<String> WMS_OPERATOR_ROLES = List.of(
            "WMS_OPERATOR",
            "OUTBOUND_READ", "OUTBOUND_WRITE",
            "INBOUND_READ", "INBOUND_WRITE",
            "INVENTORY_READ", "INVENTORY_WRITE",
            "MASTER_READ");

    /**
     * TASK-MONO-792 (ADR-MONO-083 P5 · owner decision Q4) — the scm business roles the four scm services
     * check (scm {@code iam-integration.md} § 업무 역할). Mirrors the {@code scm} rows of admin-service
     * {@code DomainRoleCatalog}, in the same order.
     */
    private static final List<String> SCM_BUSINESS_ROLES = List.of(
            "SCM_PROCUREMENT_READ", "SCM_PROCUREMENT_WRITE",
            "SCM_DEMAND_PLANNING_READ", "SCM_DEMAND_PLANNING_WRITE",
            "SCM_LOGISTICS_READ", "SCM_LOGISTICS_WRITE",
            "SCM_INVENTORY_VISIBILITY_READ", "SCM_INVENTORY_VISIBILITY_WRITE");

    /** The {@code scm} arm of the ceiling: entry role first, then the business roles (catalog order). */
    private static final List<String> SCM_CEILING = java.util.stream.Stream
            .concat(java.util.stream.Stream.of("SCM_OPERATOR"), SCM_BUSINESS_ROLES.stream())
            .toList();

    /**
     * TASK-MONO-793 (ADR-MONO-083 P6) — the erp business roles the four erp services check (erp
     * {@code iam-integration.md} § 업무 역할). Mirrors the {@code erp} rows of admin-service
     * {@code DomainRoleCatalog}, in the same order.
     */
    private static final List<String> ERP_BUSINESS_ROLES = List.of(
            "ERP_MASTERDATA_READ", "ERP_MASTERDATA_WRITE", "ERP_APPROVAL_READ", "ERP_APPROVAL_WRITE");

    /** The {@code erp} arm of the ceiling: entry role first, then the business roles (catalog order). */
    private static final List<String> ERP_CEILING = java.util.stream.Stream
            .concat(java.util.stream.Stream.of("ERP_OPERATOR"), ERP_BUSINESS_ROLES.stream())
            .toList();

    /**
     * TASK-MONO-792 (ADR-MONO-083 D3 · P5 · owner decision Q4) — <b>entry role → the business roles that
     * derive it</b>, one row per fine-grained domain. The entry role (gateway passage · screen entry ·
     * procurement {@code isOperator()}) is NOT in admin-service's catalog and is never assigned: it rides
     * the token iff ≥1 of its business roles does (auth-service architecture.md § 진입 역할 파생).
     *
     * <p>This is data on purpose: ERP · Finance · E-commerce (P6–P8, TASK-MONO-793..795) add one row each
     * here (and widen their ceiling arm in {@link #fromEntitledDomains}) — {@link #withDerivedEntryRoles}
     * does not change.
     */
    static final Map<String, Set<String>> ENTRY_ROLES = Map.of(
            "SCM_OPERATOR", Set.copyOf(SCM_BUSINESS_ROLES),
            // TASK-MONO-793 (ADR-083 P6): the ERP row — erp iam-integration.md § 업무 역할.
            "ERP_OPERATOR", Set.copyOf(ERP_BUSINESS_ROLES));

    private OperatorRoleDerivation() {
    }

    /**
     * TASK-MONO-792 — applies {@link #ENTRY_ROLES} to an already-filtered role list.
     * <ol>
     *   <li>every entry role in {@code roles} is DROPPED first — an entry role never comes from an
     *       assignment or a delegation (a stale {@code SCM_OPERATOR}-only set yields nothing);</li>
     *   <li>an entry role is then added iff ≥1 of its business roles is in the list AND {@code ceiling}
     *       (when non-null) contains the entry role;</li>
     *   <li>order: with a {@code ceiling}, the ceiling's order (the entry role heads its domain's arm);
     *       without one (the partnership path, whose ceiling is the delegated slice itself), the input
     *       order followed by the derived entry roles in key order.</li>
     * </ol>
     *
     * @param roles   the roles already decided for the token (assigned ∩ ceiling, or delegated verbatim)
     * @param ceiling the subscription ceiling ({@link #fromEntitledDomains}) or {@code null} for «no
     *                separate ceiling» (partnership)
     * @return an immutable list, never null
     */
    static List<String> withDerivedEntryRoles(List<String> roles, List<String> ceiling) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        Set<String> kept = new LinkedHashSet<>();
        for (String role : roles) {
            if (role != null && !ENTRY_ROLES.containsKey(role)) {
                kept.add(role);
            }
        }
        Set<String> derived = new java.util.TreeSet<>();
        for (Map.Entry<String, Set<String>> e : ENTRY_ROLES.entrySet()) {
            boolean anyBusiness = kept.stream().anyMatch(e.getValue()::contains);
            boolean allowed = ceiling == null || ceiling.contains(e.getKey());
            if (anyBusiness && allowed) {
                derived.add(e.getKey());
            }
        }
        if (ceiling == null) {
            List<String> out = new java.util.ArrayList<>(kept);
            out.addAll(derived);
            return List.copyOf(out);
        }
        List<String> out = new java.util.ArrayList<>();
        for (String role : ceiling) {
            if (kept.contains(role) || derived.contains(role)) {
                out.add(role);
            }
        }
        return List.copyOf(out);
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
                // (*_ADMIN / WMS_ADMIN — cancellation, force-saga-fail) are
                // intentionally NOT granted by the operator entitlement, and
                // MASTER_WRITE is separately withheld — see the WMS_OPERATOR_ROLES
                // javadoc, which corrects this comment's earlier claim that
                // master-data writes are an ADMIN-tier role (they are not; the
                // wms contract puts create/update at MASTER_WRITE). TASK-MONO-514.
                case "wms" -> WMS_OPERATOR_ROLES;
                case "ecommerce" -> List.of("ECOMMERCE_OPERATOR");
                // TASK-MONO-792 (ADR-083 P5): the scm ceiling = the entry role (derived — see
                // ENTRY_ROLES) + the eight business roles the scm services check.
                case "scm" -> SCM_CEILING;
                // TASK-MONO-793 (ADR-083 P6): the erp ceiling = the entry role (derived) + the
                // four business roles the erp services check.
                case "erp" -> ERP_CEILING;
                case "finance" -> List.of("FINANCE_OPERATOR");
                case "mes" -> List.of("MES_OPERATOR");
                // TASK-MONO-750 / ADR-MONO-079 ACCEPTED — A (D4-A), a partial amendment of
                // ADR-MONO-059: this arm is now REACHABLE, on exactly one path. account-service
                // V0031 subscribes `fan-platform` to `fan`, and a PLATFORM operator (home `*`)
                // may assume `fan-platform` — admin-service refuses every customer operator
                // (assignment check step 2b, rider R3) and refuses `fan` subscriptions on any
                // other tenant. The FAN_OPERATOR minted here is honoured by fan artist-service's
                // directory-management paths ONLY; community, membership and notification refuse
                // it on every path, so ADR-MONO-059's excluded option B (authoring as an artist,
                // ARTIST_POST) stays closed — that refusal lives in those services, not here.
                // Until TASK-MONO-750 this arm was unreachable by decision (TASK-MONO-512); the
                // history is in ADR-MONO-059 § 부분 개정.
                case "fan", "fan-platform" -> List.of("FAN_OPERATOR");
                default -> List.of(); // gap / unknown → no operator role
            };
            roles.addAll(domainRoles);
        }

        return List.copyOf(roles);
    }
}
