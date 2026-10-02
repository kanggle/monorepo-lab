package com.example.account.presentation.internal;

import com.example.account.application.result.SiteRoleMutationResult;
import com.example.account.application.service.ConsumerSiteRoleWriteUseCase;
import com.example.account.domain.repository.ConsumerSiteMembershipRepository;
import com.example.account.domain.tenant.TenantId;
import com.example.account.presentation.dto.request.SiteRoleGrantRequest;
import com.example.account.presentation.dto.request.SiteRoleRevokeRequest;
import com.example.account.presentation.dto.response.SiteRoleMutationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TASK-MONO-752 (ADR-MONO-079 D5) — {@code PATCH /internal/tenants/{tenantId}/accounts/{accountId}/site-roles:grant}
 * and {@code ...:revoke}: write / remove one consumer site role of a pool account on the site {@code {tenantId}}.
 * Contract: {@code specs/contracts/http/internal/consumer-site-roles.md}.
 *
 * <p>Same surface as {@code AccountRoleController} ({@code roles:add} / {@code roles:remove}, AIP-136 verbs):
 * workload JWT with {@code internal.invoke}, token {@code tenant_id} = path tenant (ADR-MONO-076, enforced by
 * the IAM gateway on {@code /internal/tenants/{tenantId}/**}), and {@link TenantScopeGuard} as
 * defense-in-depth. No new client registration: the caller's existing tenant-scoped exchange for the site
 * already reaches this path.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/tenants/{tenantId}/accounts/{accountId}")
public class ConsumerSiteRoleController {

    private final ConsumerSiteRoleWriteUseCase useCase;
    private final ConsumerSiteMembershipRepository membershipRepository;

    @PatchMapping("/site-roles:grant")
    public ResponseEntity<SiteRoleMutationResponse> grant(
            @PathVariable String tenantId,
            @PathVariable String accountId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String callerTenantId,
            @Valid @RequestBody SiteRoleGrantRequest request) {

        TenantScopeGuard.validate(callerTenantId, tenantId);
        try {
            return ResponseEntity.ok(SiteRoleMutationResponse.from(useCase.grant(
                    tenantId, accountId, request.roleName(), request.expectedEmail(), request.operatorId())));
        } catch (DataIntegrityViolationException e) {
            // Two concurrent grants of the same role race on the primary key. The loser's transaction rolled
            // back and the role is held by the winner: answer as the idempotent no-op it is.
            log.info("site-role grant: concurrent duplicate for account {} on site {}, answering with the read",
                    accountId, tenantId);
            return ResponseEntity.ok(SiteRoleMutationResponse.from(new SiteRoleMutationResult(accountId, tenantId,
                    membershipRepository.findSiteRoles(new TenantId(tenantId), accountId), false)));
        }
    }

    @PatchMapping("/site-roles:revoke")
    public ResponseEntity<SiteRoleMutationResponse> revoke(
            @PathVariable String tenantId,
            @PathVariable String accountId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String callerTenantId,
            @Valid @RequestBody SiteRoleRevokeRequest request) {

        TenantScopeGuard.validate(callerTenantId, tenantId);
        return ResponseEntity.ok(SiteRoleMutationResponse.from(useCase.revoke(
                tenantId, accountId, request.roleName(), request.operatorId())));
    }
}
