package com.example.admin.application;

import com.example.admin.domain.rbac.ScopeSet;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * TASK-BE-477 / ADR-MONO-045 D3 — the default {@link HostEntitledScopeResolver}.
 *
 * <p>Returns {@link Optional#empty()} ("unbounded") for every host tenant: the
 * request-time {@code ∩ host-holds} step is a no-op that defers to the invite-time
 * ≤-own cap. See {@link HostEntitledScopeResolver} for the deferral rationale (no
 * local host-holds mirror; no hot-path cross-service call). NET-ZERO and
 * fail-consistent: the security-critical cap (no admin role in {@code delegatedScope})
 * is enforced at invite time regardless.
 */
@Component
public class UnboundedHostEntitledScopeResolver implements HostEntitledScopeResolver {

    @Override
    public Optional<ScopeSet> resolve(String hostTenantId) {
        return Optional.empty();
    }
}
