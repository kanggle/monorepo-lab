package com.example.finance.account.presentation.controller;

import com.example.finance.account.application.AccountApplicationService;
import com.example.finance.account.application.ActorContext;
import com.example.finance.account.application.command.OpenAccountCommand;
import com.example.finance.account.application.command.UpgradeKycCommand;
import com.example.finance.account.application.view.AccountView;
import com.example.finance.account.application.view.BalanceView;
import com.example.finance.account.infrastructure.security.ActorContextResolver;
import com.example.finance.account.presentation.dto.AccountResponse;
import com.example.finance.account.presentation.dto.ApiEnvelope;
import com.example.finance.account.presentation.dto.OpenAccountRequest;
import com.example.finance.account.presentation.dto.UpgradeKycRequest;
import com.example.finance.account.presentation.support.IdempotentExecution;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Account lifecycle REST endpoints (account-api.md). Mutating endpoints
 * require {@code Idempotency-Key} (missing → 400 IDEMPOTENCY_KEY_REQUIRED via
 * {@code GlobalExceptionHandler}). Controllers never touch JPA repositories —
 * all persistence flows through {@link AccountApplicationService}.
 */
@RestController
@RequestMapping("/api/finance/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountApplicationService service;
    private final IdempotentExecution idempotency;

    @PostMapping
    public ResponseEntity<?> open(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody OpenAccountRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        return idempotency.run(actor.tenantId(), "POST /api/finance/accounts",
                idempotencyKey, req, () -> {
                    AccountView v = service.openAccount(new OpenAccountCommand(
                            actor, req.ownerRef(), req.currency(), req.kycLevel()));
                    return ResponseEntity.status(HttpStatus.CREATED)
                            .body(ApiEnvelope.of(AccountResponse.from(v)));
                });
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiEnvelope<AccountResponse>> get(@PathVariable String id) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        AccountView v = service.getAccount(id, actor);
        return ResponseEntity.ok(ApiEnvelope.of(AccountResponse.from(v)));
    }

    @GetMapping("/{id}/balances")
    public ResponseEntity<ApiEnvelope<List<AccountResponse.BalanceResponse>>> balances(
            @PathVariable String id) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        List<AccountResponse.BalanceResponse> body = service.getBalances(id, actor)
                .stream()
                .map(AccountController::toBalanceResponse)
                .toList();
        return ResponseEntity.ok(ApiEnvelope.of(body));
    }

    @PostMapping("/{id}/kyc/upgrade")
    public ResponseEntity<?> upgradeKyc(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody UpgradeKycRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        return idempotency.run(actor.tenantId(),
                "POST /api/finance/accounts/{id}/kyc/upgrade",
                idempotencyKey, req, () -> {
                    AccountView v = service.upgradeKyc(new UpgradeKycCommand(
                            actor, id, req.toLevel(), req.reason()));
                    return ResponseEntity.ok(ApiEnvelope.of(AccountResponse.from(v)));
                });
    }

    private static AccountResponse.BalanceResponse toBalanceResponse(BalanceView b) {
        return new AccountResponse.BalanceResponse(
                b.currency(), b.ledger(), b.available(), b.held());
    }
}
