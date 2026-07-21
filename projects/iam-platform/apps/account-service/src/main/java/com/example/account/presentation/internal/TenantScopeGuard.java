package com.example.account.presentation.internal;

import com.example.account.application.exception.TenantScopeDeniedException;

/**
 * Defense-in-depth tenant-scope check shared by the internal provisioning controllers.
 *
 * <p>The gateway performs the primary validation (TASK-BE-230); this is the second line
 * of defense at the controller level. If {@code X-Tenant-Id} is absent (e.g.
 * service-to-service without a JWT), validation is skipped — the gateway's mTLS /
 * shared-token layer is trusted.
 *
 * <p>Consolidates the byte-identical {@code validateTenantScope} guard previously copied
 * into {@code TenantProvisioningController}, {@code BulkAccountController},
 * {@code AccountRoleController}, {@code AccountIdentityController}, and
 * {@code ResolveOrCreateIdentityController}.
 */
public final class TenantScopeGuard {

    private TenantScopeGuard() {
    }

    /**
     * @param callerTenantId value from the {@code X-Tenant-Id} header (may be null)
     * @param pathTenantId   the {@code {tenantId}} path variable
     * @throws TenantScopeDeniedException if the header is present and does not match the path
     */
    public static void validate(String callerTenantId, String pathTenantId) {
        if (callerTenantId != null && !callerTenantId.isBlank()
                && !callerTenantId.equals(pathTenantId)) {
            throw new TenantScopeDeniedException(callerTenantId, pathTenantId);
        }
    }
}
