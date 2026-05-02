package com.example.account.presentation.internal.role;

import com.example.account.application.command.AddAccountRoleCommand;
import com.example.account.application.command.RemoveAccountRoleCommand;
import com.example.account.application.exception.TenantScopeDeniedException;
import com.example.account.application.result.AccountRoleMutationResult;
import com.example.account.application.service.AddAccountRoleUseCase;
import com.example.account.application.service.RemoveAccountRoleUseCase;
import com.example.account.presentation.dto.request.SingleRoleMutationRequest;
import com.example.account.presentation.dto.response.AccountRoleMutationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TASK-BE-255: Single-role add/remove endpoints for the internal provisioning API.
 *
 * <p>Mounted alongside {@code TenantProvisioningController} (which owns the
 * replace-all endpoint). The split lets the bulk and single-role flows evolve
 * independently — the single-role endpoints use idempotent semantics that the
 * replace-all endpoint cannot offer.
 *
 * <p>The path uses the {@code roles:add} / {@code roles:remove} sub-resource
 * verb pattern (Google AIP-136) so the segments cannot collide with a future
 * {@code GET .../roles/{roleName}} resource lookup.
 *
 * <p>Authentication: {@code X-Internal-Token} header (validated by
 * {@code InternalApiFilter}). Authorization: tenant scope is re-checked at the
 * controller level as defense-in-depth (gateway is the primary check).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/tenants/{tenantId}/accounts/{accountId}")
public class AccountRoleController {

    private final AddAccountRoleUseCase addAccountRoleUseCase;
    private final RemoveAccountRoleUseCase removeAccountRoleUseCase;

    /**
     * PATCH /internal/tenants/{tenantId}/accounts/{accountId}/roles:add
     * Idempotent single-role add.
     */
    @PatchMapping("/roles:add")
    public ResponseEntity<AccountRoleMutationResponse> addRole(
            @PathVariable String tenantId,
            @PathVariable String accountId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String callerTenantId,
            @Valid @RequestBody SingleRoleMutationRequest request) {

        validateTenantScope(callerTenantId, tenantId);

        AddAccountRoleCommand command = new AddAccountRoleCommand(
                tenantId, accountId, request.roleName(), request.operatorId());
        AccountRoleMutationResult result = addAccountRoleUseCase.execute(command);
        return ResponseEntity.ok(AccountRoleMutationResponse.from(result));
    }

    /**
     * PATCH /internal/tenants/{tenantId}/accounts/{accountId}/roles:remove
     * Idempotent single-role remove.
     */
    @PatchMapping("/roles:remove")
    public ResponseEntity<AccountRoleMutationResponse> removeRole(
            @PathVariable String tenantId,
            @PathVariable String accountId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String callerTenantId,
            @Valid @RequestBody SingleRoleMutationRequest request) {

        validateTenantScope(callerTenantId, tenantId);

        RemoveAccountRoleCommand command = new RemoveAccountRoleCommand(
                tenantId, accountId, request.roleName(), request.operatorId());
        AccountRoleMutationResult result = removeAccountRoleUseCase.execute(command);
        return ResponseEntity.ok(AccountRoleMutationResponse.from(result));
    }

    /**
     * Defense-in-depth tenant scope check. Mirrors
     * {@code TenantProvisioningController#validateTenantScope}: the gateway
     * performs the primary validation; this is a second line of defense.
     */
    private void validateTenantScope(String callerTenantId, String pathTenantId) {
        if (callerTenantId != null && !callerTenantId.isBlank()
                && !callerTenantId.equals(pathTenantId)) {
            throw new TenantScopeDeniedException(callerTenantId, pathTenantId);
        }
    }
}
