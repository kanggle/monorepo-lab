package com.example.auth.infrastructure.oauth2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TASK-MONO-514 (ADR-MONO-061, ACCEPTED 2026-08-13 — option C): the role set a
 * {@code client_credentials} (workload) client receives on its access token, per client and
 * per granted scope.
 *
 * <p>The third principal kind gets the third policy table. Consumers are seeded by
 * {@link RoleSeedPolicy} (keyed on platform), operators are derived by
 * {@link OperatorRoleDerivation} (keyed on the selected tenant's entitled domains), and a
 * workload is neither — it has no account, no entitlement subscription and no platform seed,
 * so its roles can only be a decision recorded per client. This is that record.
 *
 * <h3>Why a workload needs roles at all</h3>
 *
 * <p>Because authorization behind the gateway is roles, and only the <em>edge</em> ever looked
 * at scope. {@code master-service} gates 24 write predicates on
 * {@code hasRole('MASTER_WRITE') or hasRole('MASTER_ADMIN')}; the wms workload client holds the
 * {@code wms.master.write} <em>scope</em>, is admitted by the wms gateway on the scope leg
 * ({@code RoleAdmissions.roleOrScope()}), and is then refused by the service, because a scope
 * produces no authority. Repo-wide, the number of credentials able to produce
 * {@code MASTER_WRITE} was <b>zero</b> — not restricted, absent (TASK-MONO-514 AC-0). The
 * scope's name was right and it opened nothing; that a name exists is not evidence that it
 * grants anything.
 *
 * <h3>Empty by default, and that is the security property</h3>
 *
 * <p>A client absent from {@link #GRANTS} receives no roles — including a client registered
 * tomorrow, which is the case that matters. The {@code roles} claim is read by <b>19 services
 * across 6 projects</b> (every one that maps it to authorities), so a grant that arrives by
 * default arrives fleet-wide. ADR-MONO-061 § "무엇이 구속력을 갖나" 3 makes the empty default
 * binding, and {@code WorkloadRoleCatalogTest} asserts that every seeded
 * {@code client_credentials} client appears here explicitly — an unlisted client is a
 * decision nobody made, not a client that happens to need nothing.
 *
 * <h3>Keyed on the granted scope, not on the client alone</h3>
 *
 * <p>A role is emitted only when the request was actually granted the scope it hangs off. A
 * client-only table would hand {@code MASTER_WRITE} to a caller that asked for
 * {@code scope=wms.master.read} — the registration would decide the token instead of the
 * request, and per-token least privilege ({@code jwt-standard-claims.md} § Identity Model)
 * would hold for humans and not for workloads. Scope-gating also keeps the two axes from
 * drifting apart: the role a workload receives is the role its scope was already named for,
 * so the answer to "what does this scope open" stops depending on which service you ask.
 * Same shape as the {@code email} claim, gated on the {@code email} scope for the same reason
 * (TASK-BE-577) — consent, or in this case request, is what makes the grant legitimate.
 *
 * <h3>No admin-tier role is granted here, deliberately</h3>
 *
 * <p>ADR-MONO-061's ACCEPT was contrasted for riders and left exactly one question open:
 * whether fan artist-service's {@code ADMIN_ROLES} matcher
 * ({@code ADMIN}/{@code OPERATOR}/{@code SUPER_ADMIN}/{@code FAN_OPERATOR}) opens to workload
 * identity. <b>{@code TASK-MONO-522} has now answered it — ADR-MONO-063 ACCEPTED — D1: no.</b>
 * The artist directory's write surface is out of v1 product scope, so no workload client holds
 * one of those roles, and the test enforces it rather than trusting this comment.
 *
 * <p><b>Nothing about the table changed, and that is exactly why this paragraph had to.</b>
 * What stood here was "until {@code TASK-MONO-522} does, no workload client may hold one" — a
 * hold, whose stated reason expires the moment that ticket closes. Read a month from now it
 * would say the constraint was waiting on something already finished, and the natural next act
 * is to lift it. It is now a decision: reversing it takes an amendment to ADR-MONO-063, not a
 * closed ticket. Same bytes, different standing (see {@code feedback: retract the exemption
 * when the defect is fixed}).
 *
 * <h3>Not an account-service lookup — and it must not become one</h3>
 *
 * <p>This table is consulted from the {@code client_credentials} branch of
 * {@link TenantClaimTokenCustomizer}, where {@link TenantClaimTokenCustomizer#populateRoles}
 * and {@code populateEntitledDomains} are both structurally forbidden: a cc issuance is what
 * mints the Bearer used to call account-service, so an account-service call on that path
 * re-invokes this customizer — infinite recursion. A pure static table does no I/O, which is
 * what makes the roles leg reachable on this grant at all. Do not replace it with a lookup.
 */
final class WorkloadRoleCatalog {

    /**
     * Roles that describe a human admin tier. No workload client may hold one — <b>by decision,
     * not pending one</b>: ADR-MONO-063 (ACCEPTED — D1) settled the rider ADR-MONO-061 left open
     * (§ "이 ACCEPT 가 결정하지 않은 것") and closed fan artist-service's admin surface to
     * workload identity.
     *
     * <p>🔴 These four names are not local to artist-service. They are the same four
     * community-service's {@code ActorContext.isOperator()} accepts, so granting any of them to
     * a {@code fan-platform} workload would — as a side effect of reaching a directory
     * matcher — make {@code owns()} true over every author's gated content in the tenant, which
     * is the option ADR-MONO-059 excluded. The blast radius is why the constraint sits here at
     * the issuer rather than in the matcher.
     *
     * <p>Declared here rather than only in the test so the constraint is readable at the
     * decision site; asserted in {@code WorkloadRoleCatalogTest} so it is checked rather than
     * merely stated.
     */
    static final Set<String> ADMIN_TIER_ROLES =
            Set.of("ADMIN", "OPERATOR", "SUPER_ADMIN", "FAN_OPERATOR");

    /**
     * Every registered {@code client_credentials} client, mapping each scope that carries a
     * role grant to the roles it carries.
     *
     * <p>All ten are listed, empty maps included. An empty map here means "measured, and the
     * answer is none"; an <em>absent</em> client means nobody looked. The distinction is the
     * point of enumerating clients that get nothing — and it is why the completeness test
     * compares this key set against the Flyway seeds rather than against itself.
     *
     * <p>Population recounted from the auth-service migrations at statement level
     * (2026-08-13): 16 registered clients, <b>10</b> with the {@code client_credentials}
     * grant and 6 without. {@code membership-service-client} was revoked by V0029 and is
     * therefore not here. The count corrects TASK-MONO-514's earlier "12 cc / 4 non-cc", which
     * also recorded {@code wms-user-flow-client} as a cc client — V0010 seeds it
     * {@code ["authorization_code","refresh_token"]}.
     */
    private static final Map<String, Map<String, List<String>>> GRANTS = Map.ofEntries(
            // --- wms (tenant: wms) ---
            // The one grant this ticket exists for. master-service gates create/update on
            // MASTER_WRITE; MASTER_ADMIN (deactivate/reactivate) is NOT granted — the Goal is
            // "master data can be created through the API", and a deactivate is a different,
            // higher decision. Each role hangs off the scope the client was already registered
            // with (V0010), whose name promised exactly this.
            //
            // MASTER_READ is granted alongside because the first live measurement found the
            // write-only shape and it is not a shape anyone would have chosen: the workload
            // could POST a warehouse and then receive 403 reading back the row it had just
            // created (measured — GET /api/v1/master/warehouses returned FORBIDDEN on a token
            // that had just returned 201). A seam wired in one direction is the failure mode
            // this repo keeps paying for, and reading is strictly less than the writing that is
            // already granted here.
            Map.entry("wms-internal-services-client", Map.of(
                    "wms.master.write", List.of("MASTER_WRITE"),
                    "wms.master.read", List.of("MASTER_READ"))),

            // --- iam platform infrastructure (tenant: global-account-platform) ---
            // These four authenticate to /internal/** surfaces, which gate on scope or a
            // subject allow-list — never on roles. Granting them roles would widen them onto
            // domain surfaces they have no business reaching. Measured, and the answer is none.
            Map.entry("admin-service-client", Map.of()),
            Map.entry("auth-service-client", Map.of()),
            Map.entry("security-service-client", Map.of()),
            Map.entry("account-service-client", Map.of()),

            // --- fan-platform (tenant: fan-platform) ---
            // community-service calls membership/artist read surfaces with the account.read /
            // membership.read / artist.read scopes it was granted (V0009/V0030/V0032). Those
            // surfaces are scope-gated. Whether it may reach artist-service's ADMIN_ROLES
            // matcher was TASK-MONO-522's open question; ADR-MONO-063 (ACCEPTED — D1) answered
            // no, so this empty map is now a decision rather than a placeholder for one.
            Map.entry("community-service-client", Map.of()),
            Map.entry("test-internal-client", Map.of()),

            // TASK-MONO-528 asked whether INVENTORY_RESERVE belongs on one of the entries
            // below, and the answer measured out as NO — so nothing changed, deliberately.
            // The wms outbound saga was believed to be blocked on that role; it is not. It
            // allocates over Kafka (outbound.picking.requested -> PickingRequestedConsumer ->
            // ReserveStockService), and a consumer carries no JWT, so no role is evaluated on
            // that path at all. The REST surface INVENTORY_RESERVE guards has zero callers
            // repo-wide. Granting it would have widened a credential onto code nobody
            // invokes — the exact shape this catalog's empty default exists to prevent.
            //
            // --- other domain workload clients ---
            // Registered ahead of their callers; none has a role-gated surface it currently
            // fails to reach. When one does, it gets a line here and a measurement, not a
            // default.
            Map.entry("erp-platform-internal-services-client", Map.of()),
            Map.entry("finance-platform-internal-services-client", Map.of()),
            Map.entry("scm-platform-internal-services-client", Map.of()));

    private WorkloadRoleCatalog() {
    }

    /**
     * Returns the roles granted to a workload client for the scopes its request was actually
     * granted.
     *
     * @param clientId         the registered client's {@code client_id}; null/blank/unknown →
     *                         {@code []}
     * @param authorizedScopes the scopes granted on this token
     *                         ({@code JwtEncodingContext#getAuthorizedScopes}); null/empty →
     *                         {@code []}
     * @return an immutable list of role names in declaration order, de-duplicated, possibly
     *         empty, never null. An empty result means the {@code roles} claim is omitted from
     *         the token entirely (the shape the fleet has always seen on a workload token).
     */
    static List<String> rolesFor(String clientId, Collection<String> authorizedScopes) {
        if (clientId == null || clientId.isBlank() || authorizedScopes == null || authorizedScopes.isEmpty()) {
            return List.of();
        }
        Map<String, List<String>> byScope = GRANTS.get(clientId.trim());
        if (byScope == null || byScope.isEmpty()) {
            return List.of();
        }
        Set<String> roles = new LinkedHashSet<>();
        for (String scope : authorizedScopes) {
            roles.addAll(byScope.getOrDefault(scope, List.of()));
        }
        return List.copyOf(new ArrayList<>(roles));
    }

    /**
     * The client ids this table has an explicit answer for — the set the completeness test
     * compares against the Flyway seeds.
     */
    static Set<String> enumeratedClientIds() {
        return GRANTS.keySet();
    }

    /**
     * Every role this table can emit for a client, across all of its scopes. Used by the tests
     * that bound the blast radius; not a runtime path — issuance always goes through
     * {@link #rolesFor(String, Collection)}, which is the scope-gated one.
     */
    static List<String> allGrantableRoles(String clientId) {
        Map<String, List<String>> byScope = GRANTS.getOrDefault(clientId, Map.of());
        Set<String> roles = new LinkedHashSet<>();
        byScope.values().forEach(roles::addAll);
        return List.copyOf(new ArrayList<>(roles));
    }
}
