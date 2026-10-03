package com.example.account.presentation;

import com.example.account.application.result.LeaveConsumerSiteResult;
import com.example.account.application.service.LeaveConsumerSiteUseCase;
import com.example.account.domain.consumerpool.ConsumerSiteLeftBy;
import com.example.account.domain.tenant.TenantId;
import com.example.account.presentation.dto.response.LeaveConsumerSiteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TASK-BE-619 (account-api.md § DELETE /api/accounts/me/site-membership) — «이 사이트 탈퇴»: the
 * signed-in pool account stops using the site its token is for. The site is the gateway-propagated
 * {@code X-Tenant-Id} (= the token's {@code tenant_id}, always a site — never {@code consumer-pool},
 * contract § 1); the account is {@code X-Account-Id} (= {@code sub}).
 *
 * <p>Not {@code DELETE /api/accounts/me}: that one deletes the ONE pool account — on every consumer
 * site (owner decision 2026-10-03: deleting the pool account is the person's or a platform admin's).
 * This one touches only this site's membership, and the person can come back by consenting again.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/accounts/me/site-membership")
public class ConsumerSiteLeaveController {

    private final LeaveConsumerSiteUseCase leaveConsumerSiteUseCase;

    @DeleteMapping
    public ResponseEntity<LeaveConsumerSiteResponse> leaveSite(
            @RequestHeader("X-Account-Id") String accountId,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId) {
        LeaveConsumerSiteResult result = leaveConsumerSiteUseCase.execute(
                TenantId.fromHeaderOrDefault(tenantId).value(), accountId, ConsumerSiteLeftBy.SELF, accountId);
        return ResponseEntity.ok(LeaveConsumerSiteResponse.from(result));
    }
}
