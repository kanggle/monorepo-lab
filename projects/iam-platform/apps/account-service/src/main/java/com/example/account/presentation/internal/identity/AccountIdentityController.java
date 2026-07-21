package com.example.account.presentation.internal.identity;

import com.example.account.presentation.internal.TenantScopeGuard;
import com.example.account.application.service.GetAccountIdentityUseCase;
import com.example.account.presentation.dto.response.AccountIdentityResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TASK-BE-372 (ADR-MONO-034 U6 step 3b): internal read endpoint resolving an
 * account's central identity_id (the registry from step 3a, V0023). It is the
 * cross-store resolution foundation the operator-link surface (3c) and unified
 * provisioning (3d) build on — e.g. resolving an operator's {@code oidc_subject}
 * (a consumer account_id) to the central identity it belongs to.
 *
 * <p>Authentication: {@code X-Internal-Token} (validated by {@code InternalApiFilter}).
 * Authorization: tenant scope re-checked at the controller as defense-in-depth
 * (gateway is the primary check), mirroring {@code AccountRoleController}.
 *
 * <p>Enumeration-safe: a foreign/missing account, or an account with no identity
 * yet, returns 200 with {@code identityId = null} (no 404). Net-zero: read-only,
 * no audit row, no outbox event, no mutation.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/tenants/{tenantId}/accounts/{accountId}")
public class AccountIdentityController {

    private final GetAccountIdentityUseCase getAccountIdentityUseCase;

    @GetMapping("/identity")
    public ResponseEntity<AccountIdentityResponse> getIdentity(
            @PathVariable String tenantId,
            @PathVariable String accountId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String callerTenantId) {

        TenantScopeGuard.validate(callerTenantId, tenantId);

        String identityId = getAccountIdentityUseCase.execute(tenantId, accountId).orElse(null);
        return ResponseEntity.ok(AccountIdentityResponse.of(accountId, tenantId, identityId));
    }

}
