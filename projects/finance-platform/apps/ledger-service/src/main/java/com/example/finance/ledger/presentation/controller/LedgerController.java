package com.example.finance.ledger.presentation.controller;

import com.example.finance.ledger.application.ActorContext;
import com.example.finance.ledger.application.QueryLedgerUseCase;
import com.example.finance.ledger.application.view.AccountLinePageView;
import com.example.finance.ledger.infrastructure.security.ActorContextResolver;
import com.example.finance.ledger.presentation.dto.AccountLineResponse;
import com.example.finance.ledger.presentation.dto.ApiEnvelope;
import com.example.finance.ledger.presentation.dto.BalanceResponse;
import com.example.finance.ledger.presentation.dto.JournalEntryResponse;
import com.example.finance.ledger.presentation.dto.TrialBalanceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only ledger REST endpoints (ledger-api.md). Postings are event-driven
 * (no mutation endpoints in the first increment). The controller carries NO
 * {@code @Transactional} — reads funnel through {@link QueryLedgerUseCase}; it
 * never touches JPA repositories directly (architecture.md § boundary rules).
 */
@RestController
@RequestMapping("/api/finance/ledger")
@RequiredArgsConstructor
public class LedgerController {

    private final QueryLedgerUseCase queryLedger;

    @GetMapping("/entries/{entryId}")
    public ResponseEntity<ApiEnvelope<JournalEntryResponse>> getEntry(
            @PathVariable String entryId) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        JournalEntryResponse body = JournalEntryResponse.from(
                queryLedger.getEntry(entryId, actor.tenantId()));
        return ResponseEntity.ok(ApiEnvelope.of(body));
    }

    @GetMapping("/accounts/{ledgerAccountCode}/entries")
    public ResponseEntity<ApiEnvelope<List<AccountLineResponse>>> getAccountEntries(
            @PathVariable String ledgerAccountCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        AccountLinePageView pageView =
                queryLedger.getAccountLines(ledgerAccountCode, actor.tenantId(), page, size);
        List<AccountLineResponse> body = pageView.content().stream()
                .map(AccountLineResponse::from).toList();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("page", pageView.page());
        meta.put("size", pageView.size());
        meta.put("totalElements", pageView.totalElements());
        meta.put("totalPages", pageView.totalPages());
        return ResponseEntity.ok(ApiEnvelope.of(body, meta));
    }

    @GetMapping("/accounts/{ledgerAccountCode}/balance")
    public ResponseEntity<ApiEnvelope<BalanceResponse>> getBalance(
            @PathVariable String ledgerAccountCode) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        BalanceResponse body = BalanceResponse.from(
                queryLedger.getBalance(ledgerAccountCode, actor.tenantId()));
        return ResponseEntity.ok(ApiEnvelope.of(body));
    }

    @GetMapping("/trial-balance")
    public ResponseEntity<ApiEnvelope<TrialBalanceResponse>> getTrialBalance() {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        TrialBalanceResponse body = TrialBalanceResponse.from(
                queryLedger.getTrialBalance(actor.tenantId()));
        return ResponseEntity.ok(ApiEnvelope.of(body));
    }
}
