package com.example.account.presentation.internal;

import com.example.account.application.result.AccountStatusResult;
import com.example.account.application.service.AccountStatusUseCase;
import com.example.account.domain.tenant.TenantId;
import com.example.account.presentation.dto.response.AccountStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/accounts")
public class AccountStatusQueryController {

    private final AccountStatusUseCase accountStatusUseCase;

    /**
     * {@code GET /internal/accounts/{accountId}/status}.
     *
     * <p>TASK-BE-600: accepts the optional {@code X-Tenant-Id} that its siblings under
     * {@code /internal/accounts/{id}} ({@code lock} / {@code unlock} / {@code delete} in
     * {@link AccountLockController}) already accept. Without it this read was pinned to
     * {@code fan-platform}, so an account in any other tenant could be LOCKED through
     * {@code /lock} and then reported as "not found" here — and this is the read
     * auth-service's password login makes before letting a user in.
     *
     * <p>A header-less caller is unchanged: it reaches the same {@code fan-platform}-pinned
     * overload as before (net-zero for membership-service, admin-service and the social path).
     */
    @GetMapping("/{accountId}/status")
    public ResponseEntity<AccountStatusResponse> getStatus(
            @PathVariable String accountId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        AccountStatusResult result = (tenantId == null || tenantId.isBlank())
                ? accountStatusUseCase.getStatus(accountId)
                : accountStatusUseCase.getStatus(accountId, TenantId.fromHeaderOrDefault(tenantId));
        return ResponseEntity.ok(AccountStatusResponse.from(result));
    }
}
