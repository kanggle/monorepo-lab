package com.example.admin.application.tenant;

import com.example.admin.application.port.TenantProvisioningPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * TASK-BE-250: Read-through proxy to account-service for paginated tenant list.
 * No audit row for read paths.
 */
@Service
@RequiredArgsConstructor
public class ListTenantsUseCase {

    private final TenantProvisioningPort provisioningPort;

    public TenantPageSummary execute(String statusFilter, String tenantTypeFilter, int page, int size) {
        return provisioningPort.list(statusFilter, tenantTypeFilter, page, size);
    }
}
