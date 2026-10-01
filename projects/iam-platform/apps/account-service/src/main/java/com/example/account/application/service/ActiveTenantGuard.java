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

    /**
     * @return the active tenant (TASK-BE-614: the signup path reads its type to decide whether
     *         the account goes to the consumer pool)
     */
    Tenant requireActive(TenantId tenantId) {
        // TASK-BE-614: the reserved pool tenant now has a tenants row (V0029, for the accounts FK),
        // but it is a storage value, never a tenant anyone signs up INTO by naming it — only the
        // pool rule puts accounts there. Answer exactly what this guard answered before the row
        // existed, so a caller that sends X-Tenant-Id: consumer-pool sees no change.
        if (tenantId.isConsumerPool()) {
            throw new TenantNotFoundException(tenantId.value());
        }
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId.value()));
        if (!tenant.isActive()) {
            throw new TenantSuspendedException(tenantId.value());
        }
        return tenant;
    }
}
