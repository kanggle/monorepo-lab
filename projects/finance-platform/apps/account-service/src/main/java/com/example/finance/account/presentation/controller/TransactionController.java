package com.example.finance.account.presentation.controller;

import com.example.finance.account.application.AccountApplicationService;
import com.example.finance.account.application.AccountApplicationService.CaptureResult;
import com.example.finance.account.application.AccountApplicationService.HoldResult;
import com.example.finance.account.application.AccountApplicationService.ReleaseResult;
import com.example.finance.account.application.ActorContext;
import com.example.finance.account.application.command.CaptureHoldCommand;
import com.example.finance.account.application.command.PlaceHoldCommand;
import com.example.finance.account.application.command.ReleaseHoldCommand;
import com.example.finance.account.application.command.TransferCommand;
import com.example.finance.account.application.view.TransactionView;
import com.example.finance.account.infrastructure.security.ActorContextResolver;
import com.example.finance.account.presentation.dto.ApiEnvelope;
import com.example.finance.account.presentation.dto.CaptureHoldRequest;
import com.example.finance.account.presentation.dto.HoldResponse;
import com.example.finance.account.presentation.dto.MoneyResponse;
import com.example.finance.account.presentation.dto.PlaceHoldRequest;
import com.example.finance.account.presentation.dto.PageResponse;
import com.example.finance.account.presentation.dto.TransactionResponse;
import com.example.finance.account.presentation.dto.TransferRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fund-movement REST endpoints (account-api.md):
 * {@code /api/finance/accounts/{id}/{holds,holds/{holdId}/capture,
 * holds/{holdId}/release,transfers,transactions}}. Every mutating endpoint is
 * idempotent (F1) and delegates to the single gated application path (F4).
 */
@RestController
@RequestMapping("/api/finance/accounts")
@RequiredArgsConstructor
public class TransactionController {

    private final AccountApplicationService service;
    private final IdempotentExecution idempotency;

    @PostMapping("/{id}/holds")
    public ResponseEntity<?> placeHold(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PlaceHoldRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        return idempotency.run(actor.tenantId(),
                "POST /api/finance/accounts/{id}/holds",
                idempotencyKey, req, () -> {
                    HoldResult r = service.placeHold(new PlaceHoldCommand(
                            actor, id, req.money().amount(), req.money().currency(),
                            req.expiresInSeconds(), req.reason()));
                    return ResponseEntity.status(HttpStatus.CREATED)
                            .body(ApiEnvelope.of(
                                    HoldResponse.from(r.hold(), r.transactionId())));
                });
    }

    @PostMapping("/{id}/holds/{holdId}/capture")
    public ResponseEntity<?> capture(
            @PathVariable String id,
            @PathVariable String holdId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CaptureHoldRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        return idempotency.run(actor.tenantId(),
                "POST /api/finance/accounts/{id}/holds/{holdId}/capture",
                idempotencyKey, req, () -> {
                    CaptureResult r = service.captureHold(new CaptureHoldCommand(
                            actor, id, holdId, req.money().amount(),
                            req.money().currency()));
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("holdId", r.holdId());
                    body.put("captured", MoneyResponse.from(r.captured()));
                    body.put("released", MoneyResponse.from(r.released()));
                    body.put("status", r.status());
                    body.put("transactionId", r.transactionId());
                    return ResponseEntity.ok(ApiEnvelope.of(body));
                });
    }

    @PostMapping("/{id}/holds/{holdId}/release")
    public ResponseEntity<?> release(
            @PathVariable String id,
            @PathVariable String holdId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        return idempotency.run(actor.tenantId(),
                "POST /api/finance/accounts/{id}/holds/{holdId}/release",
                idempotencyKey, "release:" + id + ":" + holdId, () -> {
                    ReleaseResult r = service.releaseHold(
                            new ReleaseHoldCommand(actor, id, holdId));
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("holdId", r.holdId());
                    body.put("released", MoneyResponse.from(r.released()));
                    body.put("status", r.status());
                    body.put("transactionId", r.transactionId());
                    return ResponseEntity.ok(ApiEnvelope.of(body));
                });
    }

    @PostMapping("/{id}/transfers")
    public ResponseEntity<?> transfer(
            @PathVariable String id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest req) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        return idempotency.run(actor.tenantId(),
                "POST /api/finance/accounts/{id}/transfers",
                idempotencyKey, req, () -> {
                    TransactionView v = service.transfer(new TransferCommand(
                            actor, id, req.toAccountId(), req.money().amount(),
                            req.money().currency(), req.reason()));
                    return ResponseEntity.ok(
                            ApiEnvelope.of(TransactionResponse.from(v)));
                });
    }

    @GetMapping("/{id}/transactions")
    public ResponseEntity<ApiEnvelope<PageResponse<TransactionResponse>>> list(
            @PathVariable String id,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        com.example.finance.account.application.view.TransactionPageView p =
                service.listTransactions(id, actor, type, status, page, size);
        PageResponse<TransactionResponse> body = new PageResponse<>(
                p.content().stream().map(TransactionResponse::from).toList(),
                p.page(), p.size(), p.totalElements(), p.totalPages());
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("page", p.page());
        meta.put("size", p.size());
        meta.put("totalElements", p.totalElements());
        return ResponseEntity.ok(ApiEnvelope.of(body, meta));
    }
}
