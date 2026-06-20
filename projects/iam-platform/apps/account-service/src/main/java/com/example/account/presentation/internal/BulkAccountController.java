package com.example.account.presentation.internal;

import com.example.account.application.command.BulkProvisionAccountCommand;
import com.example.account.application.exception.TenantScopeDeniedException;
import com.example.account.application.result.BulkProvisionAccountResult;
import com.example.account.application.service.BulkProvisionAccountUseCase;
import com.example.account.presentation.dto.request.BulkProvisionAccountRequest;
import com.example.account.presentation.dto.response.BulkProvisionAccountResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * TASK-BE-257: Bulk provisioning endpoint for enterprise tenants (AIP-136 verb path).
 *
 * <p>URL: {@code POST /internal/tenants/{tenantId}/accounts:bulk}
 * <p>Authentication: {@code X-Internal-Token} header (validated by {@code InternalApiFilter}).
 * <p>Authorization: path {@code {tenantId}} must match {@code X-Tenant-Id} header — mirrors
 * the defense-in-depth validation pattern from {@link TenantProvisioningController}.
 *
 * <p>Always returns 200. The caller must inspect {@code summary.failed} to detect partial
 * failures. Per-row errors do not cause the entire request to fail.
 *
 * <p>Note: the class-level {@code @RequestMapping} is intentionally omitted. The full
 * path is declared directly on the method so that Spring's {@code PathPatternParser} does
 * not mis-parse the AIP-136 colon verb suffix ({@code accounts:bulk}) when it is appended
 * to a class-level prefix.
 *
 * <p><b>Audit (TASK-BE-257 finalised design)</b>: account-service does NOT write the
 * admin-service {@code admin_actions} table for bulk provisioning. The bulk-call audit is
 * recorded in {@code account_status_history} by {@link BulkProvisionAccountUseCase}, and each
 * created account emits its own {@code account.created} event. The {@code ACCOUNT_BULK_CREATE}
 * actionCode stays reserved (forward-compatible) in {@code admin.action.performed} with no
 * emitter by deliberate design — see {@code admin-events.md} (reality-aligned by TASK-BE-316).
 */
@RestController
@RequiredArgsConstructor
public class BulkAccountController {

    private final BulkProvisionAccountUseCase bulkProvisionAccountUseCase;

    /**
     * POST /internal/tenants/{tenantId}/accounts:bulk
     *
     * <p>Google AIP-136 verb path. Creates up to 1 000 accounts in a single request
     * using a partial-success model.
     */
    @PostMapping("/internal/tenants/{tenantId}/accounts:bulk")
    public ResponseEntity<BulkProvisionAccountResponse> bulkCreate(
            @PathVariable String tenantId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String callerTenantId,
            @Valid @RequestBody BulkProvisionAccountRequest request) {

        validateTenantScope(callerTenantId, tenantId);

        List<BulkProvisionAccountCommand.Item> items = request.items().stream()
                .map(i -> new BulkProvisionAccountCommand.Item(
                        i.externalId(),
                        i.email(),
                        i.phone(),
                        i.displayName(),
                        i.roles(),
                        i.status()
                ))
                .toList();

        BulkProvisionAccountCommand command = new BulkProvisionAccountCommand(
                tenantId,
                items,
                null    // operatorId: not exposed at the bulk endpoint level for now
        );

        BulkProvisionAccountResult result = bulkProvisionAccountUseCase.execute(command);
        return ResponseEntity.ok(BulkProvisionAccountResponse.from(result));
    }

    /**
     * Defense-in-depth tenant scope validation.
     *
     * <p>Mirrors the same guard in {@link TenantProvisioningController}.
     * If {@code X-Tenant-Id} is absent, validation is skipped — the gateway's
     * mTLS / shared-token layer is trusted.
     *
     * @param callerTenantId value from {@code X-Tenant-Id} header (may be null)
     * @param pathTenantId   the {@code {tenantId}} path variable
     * @throws TenantScopeDeniedException if the header is present and does not match the path
     */
    private void validateTenantScope(String callerTenantId, String pathTenantId) {
        if (callerTenantId != null && !callerTenantId.isBlank()
                && !callerTenantId.equals(pathTenantId)) {
            throw new TenantScopeDeniedException(callerTenantId, pathTenantId);
        }
    }
}
