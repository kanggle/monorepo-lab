package com.example.account.application.service;

import com.example.account.application.port.AccountQueryPort;
import com.example.account.application.result.AccountDetailResult;
import com.example.account.application.result.AccountSearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AccountSearchQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AccountQueryPort accountQueryPort;

    @Transactional(readOnly = true)
    public AccountSearchResult search(String email, int page, int size) {
        if (size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be ≤ " + MAX_PAGE_SIZE);
        }

        if (email == null || email.isBlank()) {
            return accountQueryPort.findAll(PageRequest.of(page, size));
        }

        return accountQueryPort.findByEmail(email.trim())
                .map(item -> new AccountSearchResult(List.of(item), 1, 0, size, 1))
                .orElseGet(() -> new AccountSearchResult(List.of(), 0, 0, size, 0));
    }

    @Transactional(readOnly = true)
    public Optional<AccountDetailResult> detail(String accountId) {
        return accountQueryPort.findDetailById(accountId);
    }
}
