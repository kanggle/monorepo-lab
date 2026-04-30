package com.example.account.presentation.internal;

import com.example.account.application.result.AccountStatusResult;
import com.example.account.application.service.AccountStatusUseCase;
import com.example.account.presentation.dto.response.AccountStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/accounts")
public class AccountStatusQueryController {

    private final AccountStatusUseCase accountStatusUseCase;

    @GetMapping("/{accountId}/status")
    public ResponseEntity<AccountStatusResponse> getStatus(@PathVariable String accountId) {
        AccountStatusResult result = accountStatusUseCase.getStatus(accountId);
        return ResponseEntity.ok(AccountStatusResponse.from(result));
    }
}
