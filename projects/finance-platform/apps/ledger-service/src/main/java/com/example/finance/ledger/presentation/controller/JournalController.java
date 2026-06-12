package com.example.finance.ledger.presentation.controller;

import com.example.finance.ledger.application.ActorContext;
import com.example.finance.ledger.application.PostManualJournalEntryUseCase;
import com.example.finance.ledger.application.PostManualJournalEntryUseCase.Result;
import com.example.finance.ledger.infrastructure.security.ActorContextResolver;
import com.example.finance.ledger.presentation.dto.ApiEnvelope;
import com.example.finance.ledger.presentation.dto.JournalEntryResponse;
import com.example.finance.ledger.presentation.dto.ManualJournalEntryRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual journal-posting REST endpoint (ledger-api.md § 9, 5th increment —
 * TASK-FIN-BE-011). The first journal <b>mutation</b> surface: an operator posts an
 * adjusting entry directly. Carries NO {@code @Transactional} — the use case owns
 * the single guarded write boundary ({@link PostManualJournalEntryUseCase} funnels
 * through {@code PostJournalEntryUseCase.post}); the controller never touches JPA
 * repositories directly (architecture.md § boundary rules, F1).
 *
 * <p>A fresh post returns {@code 201}; an idempotent replay (same
 * {@code Idempotency-Key}) returns {@code 200} with the original entry.
 * {@code .authenticated()} + the dual-accept tenant gate (parity with the
 * period/reconciliation mutations; no new scope-authority axis).
 */
@RestController
@RequestMapping("/api/finance/ledger/entries")
@RequiredArgsConstructor
public class JournalController {

    private final PostManualJournalEntryUseCase postManualEntry;

    @PostMapping
    public ResponseEntity<ApiEnvelope<JournalEntryResponse>> post(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody ManualJournalEntryRequest request) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        Result result = postManualEntry.post(
                request.toCommand(actor.tenantId(), actorIdentity(actor), idempotencyKey));
        JournalEntryResponse body = JournalEntryResponse.from(result.entry());
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(ApiEnvelope.of(body));
    }

    /** The actor identity recorded as the audit actor — the JWT subject, else the tenant. */
    private static String actorIdentity(ActorContext actor) {
        return actor.subject() != null ? actor.subject() : actor.tenantId();
    }
}
