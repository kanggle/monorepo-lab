package com.example.auth.infrastructure.oauth2;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Collections;

/**
 * TASK-MONO-721 (ADR-MONO-076, ACCEPTED 2026-09-23 — 갈래 D) D1/D4 — the resolved grant for a
 * <b>workload</b> assume-tenant exchange.
 *
 * <p>Produced by {@link AssumeTenantAuthenticationProvider}'s workload branch and read by
 * {@code TenantClaimTokenCustomizer}, exactly as {@link AssumeTenantAuthenticationToken} is on
 * the operator path.
 *
 * <h3>Why this is a separate type and not a flag</h3>
 *
 * <p>ADR-MONO-076 D4 says an assumed <em>workload</em> token must not acquire the trappings of
 * an identity: no {@code email}, no {@code entitled_domains}, no roles derived from the
 * selected tenant's entitlements, and a {@code sub} that stays the client rather than being
 * aligned to an account. The operator branch does all four.
 *
 * <p>A boolean on the existing token would have expressed that as <em>"skip these four when the
 * flag is set"</em> — four conditions that must each stay correct forever, in a method that has
 * grown a new derivation on nearly every ticket that touched it (BE-338 org_scope, BE-376 role
 * derivation, BE-478 delegated scope, MONO-515 sub alignment). The next derivation added there
 * would apply to workloads by default, and nothing would say so.
 *
 * <p>A separate type makes the operator derivations <b>unreachable</b> for this grant instead
 * of merely skipped: the customizer's operator branch reads fields this class does not have.
 * 🔴 That is the difference between a rule and a reminder — ADR-MONO-076 names "a workload
 * token indistinguishable from an operator token" as the most expensive way the decision fails,
 * and a conditional is exactly how that failure would arrive quietly.
 *
 * <h3>What it carries, and what it deliberately does not</h3>
 *
 * <p>Carries only what minting the token needs: the authenticated client principal, the target
 * tenant (validated against {@link WorkloadTenantCatalog} <em>before</em> this object is
 * built), and that tenant's type. 🔴 It carries <b>no</b> {@code orgScope}, {@code
 * delegatedScope} or {@code subjectAccountId} — not "null for workloads", but absent, because a
 * workload has no assignment, no partnership and no account for those to describe.
 */
public class WorkloadAssumeTenantAuthenticationToken extends AbstractAuthenticationToken {

    private final Authentication clientPrincipal;
    private final String clientId;
    private final String selectedTenantId;
    private final String selectedTenantType;

    public WorkloadAssumeTenantAuthenticationToken(Authentication clientPrincipal,
                                                   String clientId,
                                                   String selectedTenantId,
                                                   String selectedTenantType) {
        super(Collections.emptyList());
        this.clientPrincipal = clientPrincipal;
        this.clientId = clientId;
        this.selectedTenantId = selectedTenantId;
        this.selectedTenantType = selectedTenantType;
        setAuthenticated(true);
    }

    /** The client this token is for — and the value the minted token's {@code sub} keeps. */
    public String getClientId() {
        return clientId;
    }

    public String getSelectedTenantId() {
        return selectedTenantId;
    }

    public String getSelectedTenantType() {
        return selectedTenantType;
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return clientPrincipal;
    }
}
