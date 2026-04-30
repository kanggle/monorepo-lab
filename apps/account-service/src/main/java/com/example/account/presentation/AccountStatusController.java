package com.example.account.presentation;

import com.example.account.application.result.AccountStatusResult;
import com.example.account.application.result.DeleteAccountResult;
import com.example.account.application.service.AccountStatusUseCase;
import com.example.account.domain.status.StatusChangeReason;
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

    @GetMapping("/status")
    public ResponseEntity<AccountStatusResponse> getStatus(
            @RequestHeader("X-Account-Id") String accountId) {
        AccountStatusResult result = accountStatusUseCase.getStatus(accountId);
        return ResponseEntity.ok(AccountStatusResponse.from(result));
    }

    @DeleteMapping
    public ResponseEntity<DeleteAccountResponse> deleteAccount(
            @RequestHeader("X-Account-Id") String accountId,
            @Valid @RequestBody DeleteAccountRequest request) {
        DeleteAccountResult result = accountStatusUseCase.deleteAccount(
                accountId,
                StatusChangeReason.USER_REQUEST,
                "user",
                accountId
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(DeleteAccountResponse.from(result));
    }
}
