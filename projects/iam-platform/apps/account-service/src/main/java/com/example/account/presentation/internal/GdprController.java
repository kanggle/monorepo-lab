package com.example.account.presentation.internal;

import com.example.account.application.result.DataExportResult;
import com.example.account.application.result.GdprDeleteResult;
import com.example.account.application.service.DataExportUseCase;
import com.example.account.application.service.GdprDeleteUseCase;
import com.example.account.domain.tenant.TenantId;
import com.example.account.presentation.dto.request.GdprDeleteRequest;
import com.example.account.presentation.dto.response.DataExportResponse;
import com.example.account.presentation.dto.response.GdprDeleteResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/accounts")
public class GdprController {

    private final GdprDeleteUseCase gdprDeleteUseCase;
    private final DataExportUseCase dataExportUseCase;

    @PostMapping("/{accountId}/gdpr-delete")
    public ResponseEntity<GdprDeleteResponse> gdprDelete(
            @PathVariable String accountId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @Valid @RequestBody GdprDeleteRequest request) {

        // TASK-BE-619 — the TASK-MONO-735 split, now for gdpr-delete too: a caller that names a concrete
        // tenant (a site operator) is confined to it, and on a consumer-pool member that tenant's erasure
        // ends only that site's membership; a caller that names none (absent / blank / "*" — the platform
        // admin) erases the account found by its own row. Before 619 the second case was pinned to
        // fan-platform, so a SUPER_ADMIN could not erase any account outside it.
        GdprDeleteResult result = namesTenant(tenantId)
                ? gdprDeleteUseCase.execute(accountId, request.operatorId(), TenantId.fromHeaderOrDefault(tenantId))
                : gdprDeleteUseCase.executeResolvingTenant(accountId, request.operatorId());
        return ResponseEntity.ok(GdprDeleteResponse.from(result));
    }

    /** Same predicate as {@code AccountLockController#namesTenant} (TASK-MONO-735). */
    private static boolean namesTenant(String tenantHeader) {
        return tenantHeader != null && !tenantHeader.isBlank() && !"*".equals(tenantHeader);
    }

    @GetMapping("/{accountId}/export")
    public ResponseEntity<DataExportResponse> export(
            @PathVariable String accountId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

        DataExportResult result = dataExportUseCase.execute(
                accountId, TenantId.fromHeaderOrDefault(tenantId));
        return ResponseEntity.ok(DataExportResponse.from(result));
    }
}
