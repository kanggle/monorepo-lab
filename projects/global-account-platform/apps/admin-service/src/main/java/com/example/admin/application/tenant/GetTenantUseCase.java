package com.example.admin.application.tenant;

import com.example.admin.application.port.TenantProvisioningPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * TASK-BE-250: Read-through proxy to account-service for single tenant detail.
 * No audit row for read paths (per task spec).
 */
@Service
@RequiredArgsConstructor
public class GetTenantUseCase {

    private final TenantProvisioningPort provisioningPort;

    public TenantSummary execute(String tenantId) {
        return provisioningPort.get(tenantId);
    }
}
