package com.example.erp.readmodel.presentation.security;

import com.example.security.oauth2.TenantClaimValidator;
import com.example.security.jwt.AbacDataScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * READ authorization gate (E6, fail-closed) for the read model. Since TASK-MONO-793 (ADR-MONO-083 P6 · owner
 * decision Q6; erp iam-integration.md § 업무 역할) it has two verdicts, one per projection family:
 * <ul>
 *   <li>{@link #requireMasterdataRead(Jwt)} — {@code employees*}: {@code ERP_MASTERDATA_READ ∨ ERP_MASTERDATA_WRITE};</li>
 *   <li>{@link #requireApprovalRead(Jwt)} — {@code approvals*} · {@code delegations*}:
 *       {@code ERP_APPROVAL_READ ∨ ERP_APPROVAL_WRITE};</li>
 * </ul>
 * each {@code ∨ erp.read ∨ erp.write} (workload scopes) {@code ∨ tenant_id='*'} (TASK-ERP-BE-031).
 *
 * <p><b>Symmetric data-scope read filter (TASK-ERP-BE-008 / ADR-MONO-020 D3
 * amendment, 2026-06-05).</b> The gate also extracts the operator's
 * {@code org_scope} claim ({@link #orgScope(Jwt)}) = department subtree-ROOT
 * ids the operator may see (membership-derived per
 * {@code operator_tenant_assignment.org_scope}). The org-view query expands
 * those roots → descendants over {@code department_proj.parent_id} and narrows
 * the list/detail to employees within ANY scoped subtree — symmetric with
 * masterdata-service's write-side subtree containment.
 *
 * <p><b>NET-ZERO</b>: {@code org_scope=["*"]} (platform scope) or an absent
 * {@code org_scope} → no read narrowing ({@link #orgScope(Jwt)} returns
 * {@code null}), so every BE-007 caller is unaffected. An explicit empty
 * {@code org_scope=[]} (zero-scope) narrows to nothing (fail-closed: empty
 * result / 404 on detail).
 */
@Component
public class ReadAuthorizationGate {

    private static final String SCOPE_READ = "erp.read";
    private static final String SCOPE_WRITE = "erp.write";
    private static final String DOMAIN_KEY = "erp";

    static final String ROLE_MASTERDATA_READ = "ERP_MASTERDATA_READ";
    static final String ROLE_MASTERDATA_WRITE = "ERP_MASTERDATA_WRITE";
    static final String ROLE_APPROVAL_READ = "ERP_APPROVAL_READ";
    static final String ROLE_APPROVAL_WRITE = "ERP_APPROVAL_WRITE";

    /**
     * The erp domain key. Before TASK-MONO-793 it keyed the {@code entitled_domains} READ clause; no verdict
     * reads it now — kept so the constructor (and the property it binds) does not change.
     */
    private final String domainKey;

    public ReadAuthorizationGate(
            @Value("${erpplatform.oauth2.required-tenant-id:erp}") String requiredTenantId) {
        this.domainKey = requiredTenantId == null || requiredTenantId.isBlank()
                ? DOMAIN_KEY : requiredTenantId;
    }

    /** @return the configured erp domain key (diagnostics only since TASK-MONO-793). */
    String domainKey() {
        return domainKey;
    }

    /**
     * TASK-MONO-793 Q6 — the {@code employees*} reads (the integrated org view is masterdata).
     * Throws {@link ReadAccessDeniedException} (→ 403 {@code PERMISSION_DENIED}).
     */
    public void requireMasterdataRead(Jwt jwt) {
        require(jwt, ROLE_MASTERDATA_READ, ROLE_MASTERDATA_WRITE);
    }

    /**
     * TASK-MONO-793 Q6 — the {@code approvals*} · {@code delegations*} reads.
     * Throws {@link ReadAccessDeniedException} (→ 403 {@code PERMISSION_DENIED}).
     */
    public void requireApprovalRead(Jwt jwt) {
        require(jwt, ROLE_APPROVAL_READ, ROLE_APPROVAL_WRITE);
    }

    /**
     * The shared verdict (fail-closed; a {@code null} JWT is denied — defense-in-depth). TASK-MONO-793
     * (owner decision Q1) removed two clauses: {@code entitled_domains ∋ erp} — every assume token carries
     * the tenant's subscriptions, so it opened the read model to operators holding no ERP set — and the
     * operator roles ({@code ERP_OPERATOR} is the derived entry role now; {@code ERP_ADMIN} ·
     * {@code SUPER_ADMIN} are issued nowhere). The tenant gate's dual-accept is untouched: it decides which
     * tenant a token may enter, not what it may read there.
     */
    private void require(Jwt jwt, String readRole, String writeRole) {
        if (jwt == null) {
            throw new ReadAccessDeniedException("no authenticated token");
        }
        Set<String> tokens = extractScopesAndRoles(jwt);
        boolean scoped = tokens.contains(SCOPE_READ) || tokens.contains(SCOPE_WRITE);
        boolean role = tokens.contains(readRole) || tokens.contains(writeRole);
        // READ-only platform-super-admin wildcard admission (TASK-ERP-BE-031, unchanged): the
        // platform-console super-admin's base OIDC domain token — tenant_id='*', NO erp scope, NO
        // domain roles. This gate is READ-only (E5: no mutating endpoints), so it never widens a write.
        boolean superAdminWildcard = TenantClaimValidator.WILDCARD_TENANT
                .equals(jwt.getClaimAsString(TenantClaimValidator.CLAIM_TENANT_ID));
        if (!scoped && !role && !superAdminWildcard) {
            throw new ReadAccessDeniedException(
                    "actor lacks " + readRole + " (or " + writeRole + ", or an erp scope)");
        }
    }

    /**
     * Extracts the operator's {@code org_scope} read-narrowing scope (TASK-ERP-BE-008).
     *
     * <p><b>READ vs WRITE differ by design (TASK-ERP-BE-029).</b> This READ gate defaults an
     * <em>absent</em> {@code org_scope} to {@link OrgScope#platform()} (no narrowing — the
     * BE-007 net-zero contract; every existing read caller relies on it). masterdata-service's
     * write side does the <em>opposite</em>: an absent data-scope is fail-closed (403
     * {@code DATA_SCOPE_FORBIDDEN}) so a token with no {@code org_scope} cannot perform a
     * department-scoped write. The asymmetry is intentional — reads on the internal read-model
     * default open (net-zero), mutations default closed (least-privilege). Do NOT "align" this
     * to fail-closed without revisiting the BE-007 read contract.
     * <ul>
     *   <li>absent / blank {@code org_scope} → {@link OrgScope#platform()} (no
     *       narrowing — net-zero for every BE-007 caller).</li>
     *   <li>{@code org_scope} containing {@code "*"} → {@link OrgScope#platform()}
     *       (platform-wide; the tenant gate already isolates cross-tenant).</li>
     *   <li>a non-{@code "*"} set of subtree-root ids → {@link OrgScope#of(Set)}
     *       (narrow to the union of those subtrees; an explicit empty set is
     *       zero-scope = narrows to nothing).</li>
     * </ul>
     * <p>An <b>explicit</b> empty {@code org_scope=[]} (claim present, no roots)
     * is zero-scope ({@link OrgScope#of(Set)} with an empty set → empty read
     * result / 404 on detail, fail-closed) — distinct from an <b>absent</b>
     * claim (platform / net-zero), matching the edge-case contract.
     */
    public OrgScope orgScope(Jwt jwt) {
        if (jwt == null) {
            return OrgScope.platform();
        }
        boolean claimPresent = jwt.hasClaim("org_scope") || jwt.hasClaim("data_scope");
        // Parse the data-scope token set via the shared canonical reader
        // (ADR-MONO-025; dual-reads org_scope + data_scope). The platform/
        // zero-scope distinction below is the read-model's domain-local
        // interpretation, which AbacDataScope does not carry.
        AbacDataScope scope = AbacDataScope.fromClaimValues(
                jwt.getClaim("org_scope"), jwt.getClaim("data_scope"));
        if (scope.isUnrestricted()) {
            return OrgScope.platform();
        }
        if (scope.isEmpty()) {
            // Absent claim → net-zero platform; explicit present-but-empty
            // (org_scope=[]) → zero-scope (narrows to nothing, fail-closed).
            return claimPresent ? OrgScope.of(Set.of()) : OrgScope.platform();
        }
        return OrgScope.of(scope.tokens());
    }

    /**
     * Lifts roles + scopes from the common claim aliases ({@code roles} /
     * {@code role} / {@code scope} / {@code scopes}), splitting space/comma
     * delimited string claims (OAuth2 {@code scope} is space-delimited).
     */
    private static Set<String> extractScopesAndRoles(Jwt jwt) {
        Set<String> out = new HashSet<>();
        for (String name : new String[]{"roles", "role", "scope", "scopes"}) {
            Object raw = jwt.getClaim(name);
            if (raw == null) {
                continue;
            }
            if (raw instanceof Collection<?> c) {
                for (Object v : c) {
                    String s = String.valueOf(v);
                    if (!s.isBlank()) out.add(s);
                }
            } else if (raw instanceof String s) {
                for (String part : s.split("[,\\s]+")) {
                    if (!part.isBlank()) out.add(part);
                }
            }
        }
        return out;
    }
}
