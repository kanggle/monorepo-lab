package com.example.admin.infrastructure.client;

import com.example.admin.application.port.OperatorIdentityResolvePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * TASK-MONO-772 S3 — {@link OperatorIdentityResolvePort} over the existing {@link AccountServiceClient} call
 * (the same one {@code FirstAdminProvisioner} and {@code CreateOperatorUseCase} make), so the acceptance use case
 * depends on a port and not on the HTTP client.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountServiceOperatorIdentityAdapter implements OperatorIdentityResolvePort {

    private final AccountServiceClient accountServiceClient;

    @Override
    public String resolveOrCreateIdentity(String tenantId, String email) {
        try {
            return accountServiceClient.resolveOrCreateIdentity(tenantId, email, true);
        } catch (RuntimeException e) {
            // The client is fail-soft for downstream failures already; anything else must not fail an acceptance
            // that has committed either.
            log.warn("resolve-or-create identity failed (fail-soft, operator left unlinked) tenant={}: {}",
                    tenantId, e.getClass().getSimpleName());
            return null;
        }
    }
}
