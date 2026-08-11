package com.example.fanplatform.community.application;

import java.util.Set;

/**
 * Authenticated caller context built from the validated JWT. Passed to use
 * cases as a value object — keeps Spring Security types out of the application
 * layer.
 */
public record ActorContext(String accountId, String tenantId, Set<String> roles) {

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    public boolean isOperator() {
        // Accept the assume-tenant operator role (iam's token-exchange mints FAN_OPERATOR —
        // OperatorRoleDerivation) alongside the generic roles a directly-provisioned operator
        // carries. Without FAN_OPERATOR a cross-tenant operator passes the gateway
        // (RoleAdmissions.roleOrScope admits any role) but is silently treated as a
        // non-operator here (TASK-MONO-417). Additive — existing generic operators unaffected.
        //
        // TASK-MONO-512 / ADR-MONO-059 ACCEPTED — A: this predicate is UNREACHABLE in the fan
        // domain, deliberately. FAN_OPERATOR is derived at assume-tenant from the selected
        // tenant's ACTIVE domain subscriptions, no tenant subscribes `fan`, and the ADR
        // excluded option B — so opening that plane is now out of scope by decision rather
        // than merely undone. Option D (delete the acceptors) was offered and NOT chosen, so
        // it stays. Nothing here is wrong; it simply has no caller.
        //
        // 🔴 The consequence matters at the ONE call site that also has another door:
        // PublishPostUseCase's ARTIST_POST gate reads `!hasRole(ARTIST) && !isOperator()`.
        // With this half permanently false, ARTIST is the ONLY way through, which is exactly
        // what A decided — the artist authors as themself. If someone later makes this
        // predicate true for the fan domain (a `fan` subscription row, a granted ADMIN in
        // `fan-platform`), they will have re-opened option B through a side door: an operator
        // could publish ARTIST_POSTs, and `owns()` below would hand them edit and full
        // visibility over every author's gated content in the tenant. That is an ADR-level
        // change, not a configuration one.
        return hasRole("OPERATOR") || hasRole("ADMIN") || hasRole("SUPER_ADMIN")
                || hasRole("FAN_OPERATOR");
    }

    /**
     * Whether this actor may act on content authored by {@code authorAccountId} —
     * true when the actor IS the author, or is an operator. Single-sources the
     * {@code authorAccountId.equals(actor.accountId()) || actor.isOperator()}
     * authorship predicate that was re-derived across the community use cases
     * (TASK-FAN-BE-025 N2). This is authorship, NOT a role check — the ARTIST-role
     * gate in {@code PublishPostUseCase} is deliberately kept separate.
     */
    public boolean owns(String authorAccountId) {
        return authorAccountId.equals(accountId) || isOperator();
    }
}
