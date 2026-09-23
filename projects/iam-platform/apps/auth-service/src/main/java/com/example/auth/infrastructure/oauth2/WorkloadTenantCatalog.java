package com.example.auth.infrastructure.oauth2;

import java.util.Map;
import java.util.Set;

/**
 * TASK-MONO-721 (ADR-MONO-076, ACCEPTED 2026-09-23 — 갈래 D): the tenants a
 * {@code client_credentials} (workload) client may <em>assume</em> through the RFC 8693
 * assume-tenant exchange.
 *
 * <p>Sibling of {@link WorkloadRoleCatalog}. That one answers "what may this machine
 * credential <em>do</em>"; this one answers "which tenant may it do it <em>to</em>". Both are
 * per-client tables with an empty default, for the same reason — a workload has no account, no
 * operator assignment and no entitlement subscription, so the only place the answer can live is
 * a decision recorded against the client.
 *
 * <h3>Why a workload needs to assume a tenant at all</h3>
 *
 * <p>Because {@code /internal/tenants/{tenantId}/**} makes {@code tenant_id == path tenant} the
 * authorization decision itself ({@code jwt-standard-claims.md} § JWT Validation rule 6: the
 * IdP "authorizes by tenant-scoping on its own surface"). That rule was written for
 * <em>operator</em> tokens, where "the tenant the token belongs to" and "the tenant being acted
 * on" are the same thing. A platform workload is the case where they are not: the credential is
 * registered to one tenant and its job is to act on behalf of a tenant named in the request
 * path. Measured 2026-09-23 (TASK-MONO-717 AC-1, demo window): the call returned
 * {@code 403 TENANT_SCOPE_DENIED} and could never have returned anything else.
 *
 * <p>ADR-MONO-076 chose to fix that at <b>issuance</b> rather than at either enforcement point:
 * the workload exchanges its own token for one whose {@code tenant_id} <em>is</em> the target
 * tenant, so the gateway filter and the receiving service's tenant-scope guard keep reading the
 * single claim they always read. Neither of them changes — that is why this option was chosen
 * over a second tenant claim, a gateway-side map, or a receiver-side catalog.
 *
 * <h3>Empty by default, and that is the security property</h3>
 *
 * <p>A client absent from {@link #ASSUMABLE} may assume <b>nothing</b>, and its behaviour is
 * byte-identical to what it was before this decision existed. An <em>absent</em> client means
 * nobody looked; an <em>empty set</em> means someone looked and the answer is none. The two are
 * different facts and are recorded differently — the same distinction ADR-MONO-061 drew for the
 * role axis, and the reason both tables enumerate clients that receive nothing.
 *
 * <h3>The population is "credentials that can reach the exchange", not "all workloads"</h3>
 *
 * <p>Keys here are clients holding <b>both</b> {@code client_credentials} <b>and</b>
 * {@code urn:ietf:params:oauth:grant-type:token-exchange}. A client without the exchange grant
 * never reaches this code, so listing it would enumerate a population this table does not
 * govern — and a table whose population is wrong is the failure this repo keeps paying for.
 * {@code WorkloadTenantCatalogTest} checks that set equality against the Flyway migrations in
 * both directions, so a seed that adds the exchange grant to a machine credential fails rather
 * than drifting.
 *
 * <p>🔴 The test deliberately asserts <em>no floor</em> on the size. This population can
 * legitimately shrink to zero (revoke the one client and it does), and a guard that reads
 * "must not be empty" would turn that correct state into a red build — the "floor under a
 * draining population" failure. What it asserts is the <em>property</em>: every client holding
 * both grants appears here, and every key here holds both.
 *
 * <h3>{@code *} is not the mechanism for this</h3>
 *
 * <p>{@code jwt-standard-claims.md} already carries a {@code tenant_id} wildcard — "the
 * SUPER_ADMIN platform-scope wildcard, admitted only by gateways that opt in". Granting it to a
 * workload client would make the provisioning call succeed today and let that credential create
 * accounts in <b>every</b> tenant, which is precisely the failure this enumeration exists to
 * prevent (TASK-MONO-721 § Failure Scenarios 1). It was considered and rejected in
 * ADR-MONO-076 § Alternatives; this paragraph exists because the next reader will find the
 * wildcard and reasonably ask why it is not simply used.
 *
 * <h3>What this table does NOT decide</h3>
 *
 * <ul>
 *   <li><b>Whether the request may have the token at all</b> — the exchange is still refused
 *       unless the request carries the scope the client is registered for. Registration must
 *       not decide the token; the request does (ADR-MONO-061's second constraint, carried onto
 *       this axis by ADR-MONO-076 § Context).</li>
 *   <li><b>What the assumed token may do inside the target tenant</b> — that is the scope axis
 *       and {@link WorkloadRoleCatalog}. This table is only about <em>which</em> tenant.</li>
 *   <li><b>The operator path</b> — a client absent here falls through to the operator branch,
 *       where the fail-closed assignment gate (admin-service) answers. Nothing about that gate
 *       changes.</li>
 * </ul>
 */
public final class WorkloadTenantCatalog {

    private WorkloadTenantCatalog() {
    }

    /**
     * Every client holding both {@code client_credentials} and the token-exchange grant,
     * mapped to the tenants it may assume.
     *
     * <p>Population as of 2026-09-23 (after {@code V0037}): <b>one</b>. Before V0037 the only
     * client with the exchange grant at all was {@code platform-console-web}, which is an
     * {@code authorization_code} client — an operator credential, not a workload — so it is
     * deliberately not here and takes the operator branch it always took.
     */
    private static final Map<String, Set<String>> ASSUMABLE = Map.ofEntries(
            // --- ecommerce product-service (registered tenant: global-account-platform) ---
            // TASK-MONO-721 AC-5 (promoted rider R1 of ADR-MONO-076 — the owner's plain
            // `ACCEPTED — D` settled the mechanism, not this set, so it is reversible in one
            // line and the AC says so).
            //
            //   ecommerce  — the seller tenant the onboarding flow provisions into
            //                (ADR-MONO-042 D2/D4/D5). This is the tenant the 16th demo window
            //                measured the 403 on.
            //   demo-corp  — the demo's DEFAULT tenant. TASK-MONO-721 § Edge Cases names it:
            //                "셀러가 demo-corp 에서 등록되는 경우 — 데모의 기본 테넌트다".
            //                Leaving it out does not make the demo safer, it makes the demo
            //                path fail for a reason nobody would connect to this table.
            //
            // 🔴 What is NOT here is the point: `wms`, `scm`, `erp`, `finance` and every
            //    tenant registered tomorrow. TASK-MONO-721 AC-1's control measures exactly
            //    that — the same credential asking for `wms` must be refused.
            Map.entry("product-service-client", Set.of("ecommerce", "demo-corp")));

    /**
     * Whether this client is governed by this table at all — i.e. whether the assume-tenant
     * exchange should take the <em>workload</em> branch for it.
     *
     * <p>A client that is absent takes the operator branch, where the assignment gate denies it
     * (a client id is not an assigned operator). Both paths refuse; the difference is only
     * which one says so, and an enumerated client is refused for a reason this table records.
     */
    public static boolean isWorkloadExchangeClient(String clientId) {
        return clientId != null && ASSUMABLE.containsKey(clientId);
    }

    /** The tenants this client may assume; empty for a client that may assume none. */
    public static Set<String> assumableTenants(String clientId) {
        if (clientId == null) {
            return Set.of();
        }
        return ASSUMABLE.getOrDefault(clientId, Set.of());
    }

    /** Fail-closed: absent client, unknown tenant, or blank input all answer {@code false}. */
    public static boolean mayAssume(String clientId, String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return false;
        }
        return assumableTenants(clientId).contains(tenantId);
    }

    /** The enumerated client ids — for the completeness test, which owns the population check. */
    static Set<String> enumeratedClientIds() {
        return ASSUMABLE.keySet();
    }
}
