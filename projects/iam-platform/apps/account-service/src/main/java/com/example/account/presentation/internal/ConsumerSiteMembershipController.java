package com.example.account.presentation.internal;

import com.example.account.application.service.GetConsumerSiteMembershipUseCase;
import com.example.account.presentation.dto.response.ConsumerSiteMembershipResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TASK-BE-615 (ADR-MONO-078 A) — {@code GET /internal/tenants/{tenantId}/consumer-members/{accountId}}:
 * a pool account's membership of ONE consumer site and its site roles there. Caller: auth-service
 * (form login, {@code /oauth2/authorize}, token issuance). Contract:
 * {@code specs/contracts/http/internal/auth-to-account.md}.
 *
 * <p>Site-first path (the site is the scope, like {@code /internal/tenants/{tenantId}/accounts/{id}/roles}).
 * Authentication: workload JWT on {@code /internal/**} (SecurityConfig). Tenant scope: an
 * {@code X-Tenant-Id} that names another tenant is refused ({@link TenantScopeGuard}); auth-service
 * sends none.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/tenants/{tenantId}/consumer-members")
public class ConsumerSiteMembershipController {

    private final GetConsumerSiteMembershipUseCase getConsumerSiteMembershipUseCase;

    @GetMapping("/{accountId}")
    public ResponseEntity<ConsumerSiteMembershipResponse> get(
            @PathVariable String tenantId,
            @PathVariable String accountId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String callerTenantId) {

        TenantScopeGuard.validate(callerTenantId, tenantId);
        return ResponseEntity.ok(ConsumerSiteMembershipResponse.from(
                getConsumerSiteMembershipUseCase.execute(tenantId, accountId)));
    }
}
