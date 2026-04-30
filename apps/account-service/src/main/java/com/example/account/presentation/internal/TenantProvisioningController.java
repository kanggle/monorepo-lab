package com.example.account.presentation.internal;

import com.example.account.application.command.AssignRolesCommand;
import com.example.account.application.command.ProvisionAccountCommand;
import com.example.account.application.exception.TenantScopeDeniedException;
import com.example.account.application.result.AssignRolesResult;
import com.example.account.application.result.ProvisionAccountResult;
import com.example.account.application.result.ProvisionedAccountDetailResult;
import com.example.account.application.result.ProvisionedAccountListResult;
import com.example.account.application.result.ProvisionPasswordResetResult;
import com.example.account.application.result.ProvisionedStatusChangeResult;
import com.example.account.application.service.AssignRolesUseCase;
import com.example.account.application.service.ProvisionAccountUseCase;
import com.example.account.application.service.ProvisionPasswordResetUseCase;
import com.example.account.application.service.ProvisionStatusChangeUseCase;
import com.example.account.application.service.TenantAccountQueryUseCase;
import com.example.account.domain.status.AccountStatus;
import com.example.account.presentation.dto.request.AssignRolesRequest;
import com.example.account.presentation.dto.request.ProvisionAccountRequest;
import com.example.account.presentation.dto.request.ProvisionPasswordResetRequest;
import com.example.account.presentation.dto.request.ProvisionStatusChangeRequest;
import com.example.account.presentation.dto.response.AssignRolesResponse;
import com.example.account.presentation.dto.response.ProvisionAccountResponse;
import com.example.account.presentation.dto.response.ProvisionPasswordResetResponse;
import com.example.account.presentation.dto.response.ProvisionedAccountDetailResponse;
import com.example.account.presentation.dto.response.ProvisionedAccountListResponse;
import com.example.account.presentation.dto.response.ProvisionedStatusChangeResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * TASK-BE-231: Internal provisioning API for enterprise tenants (WMS etc.).
 *
 * <p>URL prefix: {@code /internal/tenants/{tenantId}/accounts}
 * <p>Authentication: {@code X-Internal-Token} header (validated by {@code InternalApiFilter}).
 * <p>Authorization: path {@code {tenantId}} is validated against {@code X-Tenant-Id} header
 * as defense-in-depth (gateway performs primary validation per TASK-BE-230).
 *
 * <p>All endpoints exclude sensitive fields (password_hash, deleted_at, email_hash) from
 * responses per regulated trait R4.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/tenants/{tenantId}/accounts")
public class TenantProvisioningController {

    private final ProvisionAccountUseCase provisionAccountUseCase;
    private final TenantAccountQueryUseCase tenantAccountQueryUseCase;
    private final AssignRolesUseCase assignRolesUseCase;
    private final ProvisionStatusChangeUseCase provisionStatusChangeUseCase;
    private final ProvisionPasswordResetUseCase provisionPasswordResetUseCase;

    /**
     * POST /internal/tenants/{tenantId}/accounts
     * Create a new user account under the given tenant.
     */
    @PostMapping
    public ResponseEntity<ProvisionAccountResponse> createAccount(
            @PathVariable String tenantId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String callerTenantId,
            @Valid @RequestBody ProvisionAccountRequest request) {

        validateTenantScope(callerTenantId, tenantId);

        ProvisionAccountCommand command = new ProvisionAccountCommand(
                tenantId,
                request.email(),
                request.password(),
                request.displayName(),
                request.locale(),
                request.timezone(),
                request.roles(),
                request.operatorId()
        );

        ProvisionAccountResult result = provisionAccountUseCase.execute(command);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProvisionAccountResponse.from(result));
    }

    /**
     * GET /internal/tenants/{tenantId}/accounts
     * List accounts within the tenant with pagination and optional filters.
     */
    @GetMapping
    public ResponseEntity<ProvisionedAccountListResponse> listAccounts(
            @PathVariable String tenantId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String callerTenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {

        validateTenantScope(callerTenantId, tenantId);

        AccountStatus statusFilter = status != null ? AccountStatus.valueOf(status) : null;
        ProvisionedAccountListResult result = tenantAccountQueryUseCase.listAccounts(
                tenantId, statusFilter, page, size);
        return ResponseEntity.ok(ProvisionedAccountListResponse.from(result));
    }

    /**
     * GET /internal/tenants/{tenantId}/accounts/{accountId}
     * Retrieve a single account within the tenant.
     */
    @GetMapping("/{accountId}")
    public ResponseEntity<ProvisionedAccountDetailResponse> getAccount(
            @PathVariable String tenantId,
            @PathVariable String accountId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String callerTenantId) {

        validateTenantScope(callerTenantId, tenantId);

        ProvisionedAccountDetailResult result = tenantAccountQueryUseCase.getAccount(tenantId, accountId);
        return ResponseEntity.ok(ProvisionedAccountDetailResponse.from(result));
    }

    /**
     * PATCH /internal/tenants/{tenantId}/accounts/{accountId}/roles
     * Replace all roles for an account (empty array removes all roles).
     */
    @PatchMapping("/{accountId}/roles")
    public ResponseEntity<AssignRolesResponse> assignRoles(
            @PathVariable String tenantId,
            @PathVariable String accountId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String callerTenantId,
            @Valid @RequestBody AssignRolesRequest request) {

        validateTenantScope(callerTenantId, tenantId);

        AssignRolesCommand command = new AssignRolesCommand(
                tenantId,
                accountId,
                request.roles(),
                request.operatorId()
        );

        AssignRolesResult result = assignRolesUseCase.execute(command);
        return ResponseEntity.ok(AssignRolesResponse.from(result));
    }

    /**
     * PATCH /internal/tenants/{tenantId}/accounts/{accountId}/status
     * Change the account status following AccountStatusMachine rules.
     */
    @PatchMapping("/{accountId}/status")
    public ResponseEntity<ProvisionedStatusChangeResponse> changeStatus(
            @PathVariable String tenantId,
            @PathVariable String accountId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String callerTenantId,
            @Valid @RequestBody ProvisionStatusChangeRequest request) {

        validateTenantScope(callerTenantId, tenantId);

        AccountStatus targetStatus = AccountStatus.valueOf(request.status());
        ProvisionedStatusChangeResult result = provisionStatusChangeUseCase.execute(
                tenantId, accountId, targetStatus, request.operatorId());
        return ResponseEntity.ok(ProvisionedStatusChangeResponse.from(result));
    }

    /**
     * POST /internal/tenants/{tenantId}/accounts/{accountId}/password-reset
     * Issue a password-reset token (operator-initiated).
     */
    @PostMapping("/{accountId}/password-reset")
    public ResponseEntity<ProvisionPasswordResetResponse> passwordReset(
            @PathVariable String tenantId,
            @PathVariable String accountId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String callerTenantId,
            @RequestBody(required = false) ProvisionPasswordResetRequest request) {

        validateTenantScope(callerTenantId, tenantId);

        String operatorId = request != null ? request.operatorId() : null;
        ProvisionPasswordResetResult result = provisionPasswordResetUseCase.execute(
                tenantId, accountId, operatorId);
        return ResponseEntity.ok(ProvisionPasswordResetResponse.from(result));
    }

    /**
     * Defense-in-depth tenant scope validation.
     *
     * <p>The gateway performs primary validation (TASK-BE-230). This method is the
     * second line of defense at the controller level.
     *
     * <p>If {@code X-Tenant-Id} is absent (e.g., service-to-service without JWT),
     * validation is skipped — the gateway's mTLS / shared-token layer is trusted.
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
