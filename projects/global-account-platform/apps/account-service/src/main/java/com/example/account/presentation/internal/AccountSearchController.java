package com.example.account.presentation.internal;

import com.example.account.application.service.AccountSearchQueryService;
import com.example.account.presentation.dto.response.AccountDetailResponse;
import com.example.account.presentation.dto.response.AccountSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/accounts")
public class AccountSearchController {

    private final AccountSearchQueryService accountSearchQueryService;

    @GetMapping
    public ResponseEntity<AccountSearchResponse> search(
            @RequestParam(required = false) String email,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var result = accountSearchQueryService.search(email, page, size);
        var items = result.content().stream()
                .map(item -> new AccountSearchResponse.Item(
                        item.id(), item.email(), item.status(), item.createdAt()))
                .toList();
        return ResponseEntity.ok(new AccountSearchResponse(
                items, result.totalElements(), result.page(), result.size(), result.totalPages()));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountDetailResponse> detail(@PathVariable String accountId) {
        return accountSearchQueryService.detail(accountId)
                .map(result -> ResponseEntity.ok(AccountDetailResponse.of(result)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
