package com.example.account.presentation;

import com.example.account.application.result.AccountStatusResult;
import com.example.account.application.result.DeleteAccountResult;
import com.example.account.application.service.AccountStatusUseCase;
import com.example.account.domain.status.StatusChangeReason;
import com.example.account.domain.tenant.TenantId;
import com.example.account.presentation.dto.request.DeleteAccountRequest;
import com.example.account.presentation.dto.response.AccountStatusResponse;
import com.example.account.presentation.dto.response.DeleteAccountResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/accounts/me")
public class AccountStatusController {

    private final AccountStatusUseCase accountStatusUseCase;

    /**
     * TASK-BE-507: {@code X-Tenant-Id} is the gateway-propagated tenant claim (BE-230);
     * absent / blank / {@code "*"} → {@code fan-platform} (net-zero for header-less callers).
     */
    @GetMapping("/status")
    public ResponseEntity<AccountStatusResponse> getStatus(
            @RequestHeader("X-Account-Id") String accountId,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId) {
        AccountStatusResult result =
                accountStatusUseCase.getStatus(accountId, TenantId.fromHeaderOrDefault(tenantId));
        return ResponseEntity.ok(AccountStatusResponse.from(result));
    }

    @DeleteMapping
    public ResponseEntity<DeleteAccountResponse> deleteAccount(
            @RequestHeader("X-Account-Id") String accountId,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantId,
            @Valid @RequestBody DeleteAccountRequest request) {
        DeleteAccountResult result = accountStatusUseCase.deleteAccount(
                accountId,
                StatusChangeReason.USER_REQUEST,
                "user",
                accountId,
                TenantId.fromHeaderOrDefault(tenantId)
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(DeleteAccountResponse.from(result));
    }
}
