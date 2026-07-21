package com.example.account.application.service;

import com.example.account.application.exception.TenantNotFoundException;
import com.example.account.application.exception.TenantSuspendedException;
import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.tenant.Tenant;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Guards that an account is never born into a tenant that does not exist or is suspended.
 *
 * <p>Extracted from the byte-identical {@code requireActiveTenant} previously inlined in
 * {@link SignupUseCase} and {@link SocialSignupUseCase}. Without this guard the only defense
 * would be the {@code accounts.tenant_id} FK, whose {@code DataIntegrityViolationException}
 * both signup paths already map to "email already exists" — a misleading 409 for what is
 * really a bad tenant.
 */
@Component
@RequiredArgsConstructor
class ActiveTenantGuard {

    private final TenantRepository tenantRepository;

    void requireActive(TenantId tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId.value()));
        if (!tenant.isActive()) {
            throw new TenantSuspendedException(tenantId.value());
        }
    }
}
