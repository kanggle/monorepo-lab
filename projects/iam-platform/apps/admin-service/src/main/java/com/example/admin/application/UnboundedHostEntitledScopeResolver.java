package com.example.admin.application;

import com.example.admin.domain.rbac.ScopeSet;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * TASK-BE-477 / ADR-MONO-045 D3 — the (sole) {@link HostEntitledScopeResolver}.
 *
 * <p>Returns {@link Optional#empty()} ("unbounded") for every host tenant: the
 * request-time {@code ∩ host-holds} step is the identity. Per TASK-BE-479 this is the
 * FINAL decision, not a stub — a hot-path cross-service fetch is forbidden
 * (ADR-MONO-020 §3.1) and the domain dimension is already clipped at assume-tenant mint
 * (TASK-BE-478 step 2b). The real ≤-own enforcement is at invite time
 * ({@code PartnershipManagementUseCase}: domain ∈ host subscriptions, role ∈
 * {@code DelegatableRoleCatalog}). See {@link HostEntitledScopeResolver}.
 */
@Component
public class UnboundedHostEntitledScopeResolver implements HostEntitledScopeResolver {

    @Override
    public Optional<ScopeSet> resolve(String hostTenantId) {
        return Optional.empty();
    }
}
